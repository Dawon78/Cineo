# 좌석 예약 + Redis 동시성 제어 설계

## 프로젝트 개요

Cineo 서브프로젝트 3번. 사용자가 상영시간표의 좌석을 선택하고 예약을 확정하는 기능이며, 여러 사용자가 동시에 같은 좌석을 선택했을 때 한 명만 성공하도록 Redis로 동시성을 제어하는 것이 이번 서브프로젝트의 핵심 학습 목표다.

### 전체 서브프로젝트 로드맵

1. ✅ 도메인 모델 + 인증
2. ✅ 관리자 기능 + TMDB 연동
3. **좌석 예약 + Redis 동시성 제어** (이 문서)
4. 결제 연동
5. 적립금 + 등급 시스템
6. 프론트엔드 (React)
7. Docker + 배포 (Oracle Cloud)

## 이 서브프로젝트의 목표

- 좌석 선점(Hold) → 예약 확정까지의 플로우 구현
- Redis `SETNX` + TTL을 이용한 좌석 단위 동시성 제어
- 결제는 실제 PG 연동 없이 "가짜 결제"로 처리하고, 확정 시 바로 `Reservation`을 CONFIRMED 상태로 생성한다 (4번 서브프로젝트에서 이 가짜 결제 부분만 실제 PG로 교체 예정)

## 전체 흐름

```
[좌석 현황 조회] → [좌석 선점 Hold] → [선점 취소] 또는 [예약 확정]
                                              ↓
                                        [내 예약 목록 / 예약 취소]
```

- **선점(Hold)**: DB에는 아무것도 쓰지 않고 Redis에만 `seat-hold:{showtimeId}:{seatId}` 키를 TTL 5분으로 생성한다. 값은 `memberId` — 이후 "내가 잡은 선점이 맞는지" 검증에 사용한다.
- **선점 취소**: 본인이 잡은 hold 키만 삭제한다 (value의 memberId를 먼저 검증해서 남의 hold를 지우지 못하게 한다).
- **예약 확정**: 본인 hold 키가 맞는지 검증 → DB 트랜잭션으로 `Reservation` + `ReservationSeat` 레코드를 CONFIRMED 상태로 생성 → 커밋 성공 후 Redis hold 키 삭제. DB 트랜잭션이 실패하면 Redis hold 키는 그대로 두어(TTL로 자동 정리) 사용자가 재시도할 수 있게 한다.
- **좌석 현황 조회**: 좌석마다 (1) 해당 상영시간표에 CONFIRMED 예약이 있는지(DB), (2) Redis hold가 걸려있는지 둘 다 확인해서 `AVAILABLE` / `HELD` / `CONFIRMED` 중 하나로 응답한다.
- **예약 취소**: `Reservation.status`를 CANCELLED로 변경한다. 환불 로직은 없음(4번 서브프로젝트 스코프).

## Redis 동시성 제어 설계

여러 접근법 중 **좌석별 `SETNX` + 실패 시 수동 롤백**을 채택했다.

| 항목 | 내용 |
|---|---|
| Key | `seat-hold:{showtimeId}:{seatId}` |
| Value | `memberId` |
| TTL | 300초(5분) |
| 생성 | `SETNX` — 키가 이미 있으면 실패 (원자적 연산이라 동시 요청에서도 단 하나만 성공) |
| 삭제 | 선점 취소 시 / 예약 확정 후(DB 커밋 성공 시) / TTL 만료(자동) |

여러 좌석을 한 번에 선점할 때의 로직 (의사코드):

```
succeededKeys = []
for seatId in seatIds:
    success = redis.setnx(key(showtimeId, seatId), memberId, ttl=300s)
    if not success:
        for k in succeededKeys:
            redis.del(k)  // 방금 성공시킨 것만 롤백
        return 409 Conflict ("이미 선점된 좌석: {seatId}")
    succeededKeys.add(key)
return 200 OK
```

**대안으로 검토했으나 채택하지 않은 것:**
- *Lua Script로 완전 원자적 처리*: 여러 SETNX를 하나의 스크립트로 묶어 Redis 서버 내에서 원자적으로 처리. 정합성은 더 완벽하지만 Lua 스크립팅이라는 새 개념이 필요하고 디버깅이 어려워 현재 학습 단계에는 과함.
- *Redisson 분산락(`RLock`)*: 실무에서 흔한 패턴이지만, 이 프로젝트는 Redis 키 자체(SETNX)가 이미 키 단위 원자성을 보장하므로 별도 분산락이 불필요 — 의존성만 늘어남.

위 채택안의 한계: 여러 키에 걸친 진짜 트랜잭션이 아니므로, 롤백 완료 직전까지 아주 짧은 순간 다른 요청이 "이미 선점됨"으로 볼 수 있다. 데이터 정합성(이중 예약)에는 영향 없고, UX상 미세한 지연일 뿐이라 이번 스코프에서는 허용한다.

## 엔티티 설계

### Reservation (예약)

| 필드 | 타입 | 설명 |
|---|---|---|
| id | Long | PK |
| member | Member (FK) | 예약한 사람 |
| showtime | Showtime (FK) | 상영시간표 |
| status | Enum(`CONFIRMED`, `CANCELLED`) | 예약 상태 |
| totalPrice | Long | 좌석 가격 합계 (스냅샷) |
| createdAt | LocalDateTime | 예약(확정) 시각 |
| canceledAt | LocalDateTime | 취소 시각 (nullable) |

### ReservationSeat (예약-좌석 매핑)

| 필드 | 타입 | 설명 |
|---|---|---|
| id | Long | PK |
| reservation | Reservation (FK) | 소속 예약 |
| seat | Seat (FK) | 좌석 |
| price | Long | 이 좌석의 확정 가격 (basePrice + 좌석등급 추가요금 + 상영포맷 추가요금) |

가격을 스냅샷으로 저장하는 이유: `Showtime.basePrice`나 등급별 추가요금이 나중에 바뀌어도 이미 확정된 예약의 금액은 예약 당시 그대로 유지되어야 한다.

## API 스펙

| Method | URL | 인증 | 설명 |
|---|---|---|---|
| GET | `/api/showtimes/{showtimeId}/seats` | O | 좌석별 상태(AVAILABLE/HELD/CONFIRMED) 목록 조회 |
| POST | `/api/showtimes/{showtimeId}/seats/hold` | O | 좌석 선점 (body: `seatIds: [1,2,3]`) |
| DELETE | `/api/showtimes/{showtimeId}/seats/hold` | O | 선점 취소 (body: `seatIds: [1,2,3]`) |
| POST | `/api/reservations` | O | 예약 확정 (body: `showtimeId, seatIds`) |
| GET | `/api/reservations/me` | O | 내 예약 목록 조회 |
| DELETE | `/api/reservations/{id}` | O | 예약 취소 |

**응답 예시 — 좌석 현황 조회**

```json
[
  { "seatId": 1, "rowLabel": "A", "seatNumber": 1, "grade": "STANDARD", "status": "AVAILABLE" },
  { "seatId": 2, "rowLabel": "A", "seatNumber": 2, "grade": "STANDARD", "status": "HELD" },
  { "seatId": 3, "rowLabel": "A", "seatNumber": 3, "grade": "PREMIUM", "status": "CONFIRMED" }
]
```

### 에러 처리

| 상황 | 응답 |
|---|---|
| 이미 선점/예약된 좌석 선점 시도 | 409 Conflict |
| 존재하지 않는 좌석/상영시간표 | 404 Not Found |
| 본인이 선점하지 않은 좌석 취소/확정 시도 | 403 Forbidden |
| 선점 안 한(또는 TTL 만료된) 좌석 확정 시도 | 409 Conflict ("선점이 만료되었습니다") |
| 본인 소유가 아니거나 이미 CANCELLED인 예약 취소 시도 | 403 Forbidden / 409 Conflict |
| 이미 지난 상영시간표(startTime < now)에 대한 선점/예약 시도 | 400 Bad Request |

## 패키지 구조

```
com.dw.movie
└── reservation
    ├── Reservation.java              (엔티티)
    ├── ReservationSeat.java          (엔티티)
    ├── ReservationStatus.java        (Enum: CONFIRMED, CANCELLED)
    ├── SeatStatus.java               (Enum: AVAILABLE, HELD, CONFIRMED — 조회 응답용, 엔티티 아님)
    ├── ReservationController.java    (예약 확정/조회/취소 API)
    ├── SeatHoldController.java       (좌석 현황 조회/선점/선점취소 API)
    ├── ReservationService.java
    ├── SeatHoldService.java          (Redis 선점 로직 전담, StringRedisTemplate 캡슐화)
    ├── ReservationRepository.java    (JpaRepository 직접 상속 — Movie/Screen 패턴과 동일, YAGNI)
    └── dto/
        ├── SeatStatusResponse.java
        ├── SeatHoldRequest.java
        └── ReservationResponse.java
```

`Reservation`/`ReservationSeat`는 현재 복잡한 동적 조회가 필요 없으므로 기존 Movie/Screen처럼 `JpaRepository` 직접 상속으로 간다 (auth처럼 Repository/RepositoryImpl 3계층 분리는 하지 않음 — 필요해지면 나중에 리팩터링).

## 인프라

- `build.gradle.kts`에 `spring-boot-starter-data-redis` 의존성 추가
- 로컬 개발용 Redis는 Docker로 실행: `docker run -p 6379:6379 redis`
- `application.yaml`에 `spring.data.redis.host`/`port` 설정 (기본 localhost:6379, 인증 없음 — 로컬 개발 한정)

## 테스트

- Postman 시나리오: 좌석 현황 조회 → 선점 → (다른 계정으로 같은 좌석 선점 시도 → 409 확인) → 선점 취소 → 재선점 → 예약 확정 → 내 예약 조회 → 예약 취소 → 좌석 다시 AVAILABLE 확인
- 동시성 테스트: 같은 좌석에 대해 `ExecutorService`로 여러 스레드가 거의 동시에 선점을 시도하는 JUnit 테스트를 작성해서, 실제로 단 하나의 요청만 성공하는지 검증한다.

## 스코프 제외 사항

- 실제 PG 결제 연동 (4번 서브프로젝트) — 이번엔 확정 시 바로 CONFIRMED 처리
- 예약 취소 시 환불 로직 (4번 서브프로젝트)
- 좌석 선점 남은 시간 카운트다운 UI (6번 프론트엔드 서브프로젝트)
- Lua Script / Redisson 등 더 정교한 분산락 방식 (YAGNI, 필요해지면 재검토)
