# Cineo

영화관 좌석 예약 시스템입니다. 단일 지점에 여러 상영관을 운영하는 멀티플렉스를 가정하고,
회원가입부터 영화 예매, 결제까지 이어지는 흐름을 백엔드부터 프론트엔드, 배포까지 직접 구현하고 있습니다.

스프링 부트 포트폴리오 프로젝트로 시작했고,
JWT 인증, TMDB 연동, Redis를 이용한 좌석 동시성 제어, PG 결제 검증처럼 실제 서비스에서 마주치는 문제들을 하나씩 풀어가는 걸 목표로 잡았습니다.

> 배포 주소와 성능 측정 결과는 배포·튜닝이 끝나면 추가할 예정입니다.

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| Backend | Java 17, Spring Boot 4, Spring Security, Spring Data JPA, Bean Validation |
| DB / 캐시 | PostgreSQL, Redis |
| 인증 | JWT (access 30분 / refresh 14일) |
| 외부 API | TMDB (영화 정보), PortOne V1 (결제 검증·환불) |
| Frontend | React 19, React Router, Vite |
| 테스트 | JUnit 5, Mockito |

MyBatis는 의존성만 추가해 둔 상태입니다. 단순 CRUD는 JPA로 처리하고, 이후 매출·예매 현황처럼 조건이 많은 동적 조회가 생기면 MyBatis를 쓸 계획입니다.

## 주요 기능

**사용자**
- 회원가입·로그인, access token 만료 시 refresh token으로 자동 재발급
- 영화 목록(검색·정렬), 영화 상세와 날짜별 상영시간
- 좌석 선택 → 5분 선점(타이머 표시) → PortOne 결제 → 예매 확정
- 마이페이지: 등급·적립금·누적 결제, 예매 내역, 예매 취소(환불)

**관리자** (`/admin`, ADMIN 권한)
- TMDB 제목 검색으로 영화 등록
- 상영관 등록 (행·열 수 입력, 좌석 자동 생성, 좌석판 미리보기)
- 상영시간 등록·조회·삭제 (예매 기록이나 좌석 선점이 있으면 삭제 거절)

## 설계하면서 고민한 부분

### 1. 좌석 동시 선점 (Redis)
- 좌석을 고르면 Redis에 `seat-hold:{상영ID}:{좌석ID}` 키를 `SETNX` + TTL 5분으로 저장합니다. 먼저 저장한 사람만 성공하므로 같은 좌석을 두 명이 동시에 잡을 수 없습니다.
- 여러 좌석 중 하나라도 실패하면 이미 잡은 좌석을 직접 풀어 줍니다.
- 좌석 상태(빈 좌석 / 선점 / 예매 완료)는 DB에 저장하지 않고, 조회할 때 Redis와 예매 테이블을 보고 계산합니다.

### 2. 결제 검증과 자동 환불 (PortOne)
- 화면에서 결제가 끝나면 서버가 `imp_uid`로 PortOne에 실제 결제 내역을 다시 조회합니다. 화면이 보낸 금액은 믿지 않고, 서버가 계산한 금액과 비교합니다.
- 금액이 다르거나, 결제 사이에 좌석을 뺏겼거나, 상영이 이미 시작됐으면 **결제를 자동으로 환불**하고 예매를 거절합니다.
- 같은 `imp_uid`는 한 번만 쓸 수 있습니다 (중복 결제 방지).
- 예매 확정은 회원·상영 행에 비관적 락을 걸어서, 동시에 들어온 요청을 순서대로 처리합니다.

### 3. 가격과 적립금
- 좌석 가격 = 기본 가격 + 포맷 추가금(3D +2,000 / IMAX +6,000 / 4DX +8,000) + 좌석 등급 추가금
- 좌석 등급 추가금(프리미엄 +3,000 / 커플 +5,000)은 계산 로직만 있습니다. 상영관을 만들면 좌석이 모두 일반석으로 생성되고, 등급을 지정하는 기능은 아직 없습니다.
- 가격은 서버가 계산합니다. 좌석 화면의 가격 미리보기와 결제 금액 검증이 같은 계산을 씁니다.
- 예매할 때의 가격을 예매 좌석에 저장해 두어, 나중에 기본 가격이 바뀌어도 지난 예매 금액은 그대로입니다.
- 결제 금액의 일정 비율을 적립하고 누적 결제액으로 등급이 정해집니다 (BASIC 1% / SILVER 10만 원↑ 2% / GOLD 30만 원↑ 3% / VIP 70만 원↑ 5%). 취소하면 적립금과 누적 금액을 되돌립니다.

### 4. 권한
- `/api/admin/**`는 Security 설정의 URL 규칙(`hasRole("ADMIN")`)과 메서드마다 붙인 `@PreAuthorize`로 두 번 막습니다.
- 영화·상영시간 조회(`GET /api/movies/**`)만 로그인 없이 열려 있고, 나머지는 모두 로그인이 필요합니다.
- 프론트에서 관리자 메뉴를 숨기는 건 편의용이고, 실제로 막는 건 서버입니다.

## 실행 방법 (로컬)

### 준비물
- Java 17, Node.js, Docker (Redis용), PostgreSQL
- TMDB API 키, PortOne 테스트 채널 (결제를 테스트할 경우)

### 1. DB와 Redis
```bash
# PostgreSQL에 movie 데이터베이스를 만든다
createdb -U postgres movie

# Redis (외부에서 접근하지 못하게 localhost에만 연다)
docker run -d --name cineo-redis -p 127.0.0.1:6379:6379 redis
```

### 2. 백엔드 (포트 8082)
환경변수를 설정하고 `local` 프로필로 실행합니다.

| 환경변수 | 설명 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` (초기 데이터 생성, PortOne 테스트 결제 허용) |
| `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL 계정 |
| `JWT_SECRET` | JWT 서명 키 (32바이트 이상) |
| `TMDB_API_KEY` | TMDB API 키 |
| `PORTONE_API_KEY`, `PORTONE_API_SECRET` | PortOne REST API 키 |

```bash
./gradlew bootRun
```

`local` 프로필로 처음 실행하면 TMDB에서 영화 12편을 등록하고, 상영관과 내일부터 3일치 상영시간을 자동으로 만듭니다.

### 3. 프론트엔드 (포트 5173)
```bash
cd frontend
cp .env.example .env.local   # PortOne 가맹점 식별코드와 채널 키를 채운다
npm install
npm run dev
```

### 관리자 계정
회원가입을 한 뒤 DB에서 권한을 바꾸고, **다시 로그인**합니다 (권한은 로그인할 때 받는 토큰에 들어갑니다).
```sql
UPDATE member SET role = 'ADMIN' WHERE email = '가입한 이메일';
```

### 테스트
```bash
./gradlew test
```
Mockito 단위 테스트는 바로 돌아갑니다. `@SpringBootTest` 테스트는 PostgreSQL·Redis와 위 환경변수가 있어야 합니다.

## API 요약

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| POST | `/api/auth/signup` | 회원가입 | - |
| POST | `/api/auth/login` | 로그인 (access/refresh 발급) | - |
| POST | `/api/auth/refresh` | access token 재발급 | - |
| POST | `/api/auth/logout` | 로그아웃 | 로그인 |
| GET | `/api/members/me` | 내 정보 (등급, 적립금, 권한) | 로그인 |
| GET | `/api/movies` | 영화 목록 | - |
| GET | `/api/movies/{id}` | 영화 상세 | - |
| GET | `/api/movies/{id}/showtimes` | 앞으로의 상영시간 | - |
| GET | `/api/showtimes/{id}/seats` | 좌석 현황 | 로그인 |
| POST | `/api/showtimes/{id}/seats/hold` | 좌석 선점 | 로그인 |
| DELETE | `/api/showtimes/{id}/seats/hold` | 선점 해제 | 로그인 |
| GET | `/api/showtimes/{id}/seats/price` | 선택 좌석 가격 미리보기 | 로그인 |
| POST | `/api/reservations` | 결제 검증 후 예매 확정 | 로그인 |
| GET | `/api/reservations/me` | 내 예매 목록 | 로그인 |
| GET | `/api/reservations/{id}` | 예매 상세 | 로그인 |
| DELETE | `/api/reservations/{id}` | 예매 취소 (환불) | 로그인 |
| GET, POST | `/api/admin/movies` | 영화 조회·등록 (TMDB ID) | ADMIN |
| GET | `/api/admin/movies/search?query=` | TMDB 제목 검색 | ADMIN |
| GET, POST | `/api/admin/screens` | 상영관 조회·등록 | ADMIN |
| GET, POST | `/api/admin/showtimes` | 상영시간 조회·등록 | ADMIN |
| DELETE | `/api/admin/showtimes/{id}` | 상영시간 삭제 | ADMIN |

## 추가 보완 예정

- 선점 해제가 원자적이지 않습니다 (Lua 스크립트 미사용). 여러 좌석 중 일부 실패 시 되돌리는 아주 짧은 순간에 다른 사용자가 "선점됨"으로 볼 수 있습니다. 이중 예매로 이어지지는 않습니다.
- 로그인·회원가입·선점 요청 횟수 제한, 1인당 선점 좌석 수 제한이 없습니다.
- refresh token을 해시하지 않고 DB에 저장합니다.
- PortOne 호출이 DB 락·트랜잭션 안에서 일어납니다.
- 같은 상영관에 시간이 겹치는 상영을 등록할 수 있습니다.
- 에러 응답이 JSON이 아니라 문자열 메시지입니다.
- 좌석 등급(프리미엄·커플)을 지정할 수 없어 모든 좌석이 일반석입니다.
- 적립금은 쌓이기만 하고 결제에 사용할 수는 없습니다.
- TMDB 검색은 첫 페이지(20개)만 보여줍니다.

## 설계 문서

기능마다 설계와 구현 계획을 먼저 쓰고 진행했습니다. `docs/superpowers/specs/`, `docs/superpowers/plans/`

- 도메인·인증: `2026-09-01-domain-auth-design.md`
- 관리자·TMDB: `2026-09-15-admin-tmdb-design.md`
- 좌석 예약·Redis: `2026-09-17-seat-reservation-design.md`
- 결제: `2026-09-19-payment-integration-design.md`
- 관리자 화면: `2026-10-27-admin-screens-design.md`

## 앞으로 할 일

- [ ] Docker Compose + Oracle Cloud 배포
- [ ] 성능 측정·개선 (k6 부하 테스트, 좌석 조회 쿼리·Redis 조회 최적화, 인덱스)
- [ ] 배포 주소, 화면 캡처, 성능 수치 추가
- 추가 보완 예정 마무리
