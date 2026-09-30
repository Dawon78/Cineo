# 결제 연동 설계

## 프로젝트 개요

Cineo 서브프로젝트 4번. 3번 서브프로젝트(좌석 예약)에서 "가짜 결제"로 처리했던 예약 확정 로직을, 실제 PG(포트원)를 통한 결제 검증으로 교체한다. 예약 취소 시 실제 환불까지 연결하는 것도 이번 서브프로젝트 범위다.

### 전체 서브프로젝트 로드맵

1. ✅ 도메인 모델 + 인증
2. ✅ 관리자 기능 + TMDB 연동
3. ✅ 좌석 예약 + Redis 동시성 제어
4. **결제 연동** (이 문서)
5. 적립금 + 등급 시스템
6. 프론트엔드 (React)
7. Docker + 배포 (Oracle Cloud)

## 이 서브프로젝트의 목표

- 포트원(구 아임포트) PG 연동을 통한 실제 결제 검증
- 예약 확정 시 클라이언트가 보낸 결제 정보를 그대로 믿지 않고, 포트원 서버 API로 실제 결제금액/상태를 조회해서 검증
- 예약 취소 시 포트원 환불 API 호출
- `Member.totalSpent`(누적 결제금액) 실제 갱신 — 적립금/등급 실제 계산 로직 자체는 5번 서브프로젝트 스코프

프론트엔드(6번 서브프로젝트)가 아직 없어서, 결제창을 띄우는 용도로만 쓰는 별도의 스크래치 테스트 HTML 파일(`test-payment.html`)을 하나 둔다. 이건 실제 프론트엔드가 아니라 순수 테스트 도구이며, 6번 서브프로젝트 스코프를 침범하지 않는다.

## 전체 흐름

**예약 확정:**
```
1. 좌석 선점 (POST /api/showtimes/{id}/seats/hold — 기존 API 그대로 재사용)
2. 가격 미리보기 (GET /api/showtimes/{id}/seats/price?seatIds=1,2 — 신규)
3. [test-payment.html] 포트원 결제창 띄우기 → 테스트 카드로 결제 → imp_uid 받음
4. 예약 확정 요청 (POST /api/reservations, body에 showtimeId/seatIds/impUid)
5. 백엔드가 포트원 서버 API(GET /payments/{imp_uid})로 실제 결제금액/상태 조회
6. 우리가 계산한 가격(SeatPriceCalculator 합계)과 실제 결제금액이 일치하고, 결제 상태가 "paid"면
   → Reservation을 CONFIRMED로 생성, Member.totalSpent 증가
7. 불일치하거나 상태가 paid가 아니면 → 예약 거부, 400 응답 (DB에 아무것도 남기지 않음)
```

**예약 취소:**
```
1. DELETE /api/reservations/{id}
2. 백엔드가 포트원 환불 API(POST /payments/cancel)를 imp_uid로 호출
3. 환불 성공 → Reservation 상태를 CANCELLED로 변경
4. 환불 실패 → 500 에러, 예약 상태는 그대로 유지 (재시도 가능하도록)
```

## 포트원 API 연동 (V1, imp_uid 기반)

| API | 용도 |
|---|---|
| `POST /users/getToken` | API키+시크릿으로 access_token 발급 |
| `GET /payments/{imp_uid}` | 결제 정보 조회 (금액, 상태 확인) |
| `POST /payments/cancel` | 환불 처리 |

access_token은 매 요청마다 새로 발급받는다(만료 시간이 있지만, 이번 스코프에서는 캐싱 없이 매번 발급 — 트래픽이 적은 학습 프로젝트라 성능 문제 없음, 필요해지면 나중에 캐싱 추가).

**서버 검증이 중요한 이유**: 클라이언트(테스트 HTML이든 나중의 React 프론트엔드든)가 "얼마 결제했다"고 보내는 값을 그대로 믿으면, 결제 없이 imp_uid만 조작해서 예약을 확정시킬 수 있다. 그래서 반드시 포트원 서버에 직접 물어봐서 실제 결제 정보를 확인해야 한다.

## 엔티티 변경

### Reservation (필드 추가)

| 필드 | 타입 | 설명 |
|---|---|---|
| impUid | String | 포트원 결제 고유번호 (nullable) |
| paidAmount | Long | 실제 검증된 결제 금액 |

별도 `Payment` 엔티티는 만들지 않는다 — 예약 1건당 결제 1건(1:1)이고, 결제 이력을 여러 건 추적하거나 부분 환불을 지원할 필요가 없어서(YAGNI). 환불 = 예약 전체 취소로 스코프를 고정했다.

### Member (메서드 추가)

```java
public void addSpent(long amount) {
    this.totalSpent += amount;
}
```

결제 확정 시 호출해서 누적 결제금액을 갱신한다. 포인트 적립/등급 승급 로직은 이 메서드를 호출하지 않으며, 5번 서브프로젝트에서 별도로 구현한다.

## API 스펙

| Method | URL | 인증 | 설명 |
|---|---|---|---|
| GET | `/api/showtimes/{showtimeId}/seats/price?seatIds=1,2` | O | 가격 미리보기 (신규) |
| POST | `/api/reservations` | O | 예약 확정 — body에 `impUid` 추가 (변경) |
| DELETE | `/api/reservations/{id}` | O | 예약 취소 — 포트원 환불 호출 추가 (변경) |

**요청/응답 예시**

가격 미리보기 응답:
```json
{ "totalPrice": 20000 }
```

예약 확정 요청:
```json
{ "showtimeId": 1, "seatIds": [3, 4], "impUid": "imp_123456789" }
```

### 에러 처리

| 상황 | 응답 |
|---|---|
| 포트원 실제 결제금액과 계산된 가격 불일치 | 400 Bad Request |
| 포트원 결제 상태가 "paid"가 아님(실패/취소된 결제) | 400 Bad Request |
| 존재하지 않는 imp_uid | 400 Bad Request |
| 포트원 API 호출 자체 실패(네트워크, 인증 등) | 500 Internal Server Error |
| 환불 처리 실패 | 500 Internal Server Error |

## 패키지 구조

```
com.dw.movie
└── payment
    ├── PortOneClient.java                    (포트원 API 호출 전담 — TmdbClient와 동일한 패턴)
    ├── PaymentVerificationFailedException.java
    └── RefundFailedException.java
```

`ReservationService`가 `PortOneClient`를 생성자로 주입받아 `confirm()`/`cancel()` 안에서 직접 호출한다 — `MovieService`가 `TmdbClient`를 쓰는 것과 동일한 구조. 별도의 `PaymentService` 계층은 두지 않는다(YAGNI, `ReservationService`가 이미 결제-예약을 묶어서 처리하는 자연스러운 위치).

`GET .../seats/price`는 기존 좌석 관련 조회를 담당하는 `SeatController`/`SeatQueryService`에 추가한다.

## 인프라

- `application.yaml`에 `portone.api-key`, `portone.api-secret` 추가 (TMDB_API_KEY와 동일한 패턴)
- 환경변수 `PORTONE_API_KEY`, `PORTONE_API_SECRET`을 IntelliJ Run Configuration에 추가
- 포트원 가입 + 테스트 연동 키 발급은 구현 단계에서 진행

## 테스트

- `test-payment.html`: 포트원 결제창을 띄우고 테스트 카드로 결제해서 `imp_uid`를 얻기 위한 스크래치 파일. 프로젝트에는 두되, 실제 애플리케이션 코드(`src/`)와는 분리된 위치에 둔다.
- Postman 시나리오: 좌석 선점 → 가격 미리보기 → (test-payment.html에서 결제) → 받은 imp_uid로 예약 확정 요청 → 201 확인 → `Member.totalSpent` 증가 확인 → 예약 취소 → 환불 확인 및 상태 CANCELLED 확인
- 금액 불일치 테스트: 실제 결제된 금액과 다른 seatIds 조합으로 확정 시도해서 400 확인

## 스코프 제외 사항

- 부분 환불, 결제 이력 다건 관리 (YAGNI — 예약 전체 취소=전액 환불만 지원)
- access_token 캐싱 (트래픽이 적어 매 요청 발급으로 충분, 필요해지면 나중에 최적화)
- 적립금 적립, 등급 승급 실제 계산 로직 (5번 서브프로젝트)
- 실제 프론트엔드 결제 UI (6번 서브프로젝트) — `test-payment.html`은 순수 테스트 도구일 뿐
- 카카오페이 등 개별 결제수단 직접 연동 (포트원이 여러 결제수단을 통합 처리)
