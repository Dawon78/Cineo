# 영화 좌석 예약 시스템 — 도메인 모델 + 인증 설계

## 프로젝트 개요

영화관 좌석 예약 웹서비스 (포트폴리오용). 단일 지점, 여러 상영관 운영. 회원가입/로그인, 관리자 기능(TMDB 연동 영화 등록, 상영관/시간표 관리), 좌석 선택(Redis 동시성 제어), 결제(PG 샌드박스 연동), 적립금/등급제, React 프론트엔드, Docker 기반 실배포(Oracle Cloud)까지 포함하는 풀스택 프로젝트.

범위가 커서 서브프로젝트로 분리해서 순서대로 진행한다. 이 문서는 그 중 **1번 서브프로젝트: 도메인 모델 + 인증**의 스펙이다.

### 전체 서브프로젝트 로드맵

1. **도메인 모델 + 인증** (이 문서)
2. 관리자 기능 + TMDB 연동
3. 좌석 예약 + Redis 동시성 제어
4. 결제 연동
5. 적립금 + 등급 시스템
6. 프론트엔드 (React)
7. Docker + 배포 (Oracle Cloud)

## 이 서브프로젝트의 목표

- 전체 도메인의 기반이 되는 엔티티(Member, Movie, Screen, Seat, Showtime) 설계 및 구현
- JWT 기반 회원가입/로그인/토큰갱신/로그아웃 구현
- 이후 모든 서브프로젝트가 이 위에 쌓인다

## 엔티티 설계

### Member (회원)
| 필드 | 타입 | 설명 |
|---|---|---|
| id | Long | PK |
| email | String | 고유, 로그인 ID로 사용 |
| password | String | BCrypt 해싱 저장 |
| name | String | 이름 |
| phone | String | 전화번호 |
| role | Enum(`USER`, `ADMIN`) | 권한 |
| totalSpent | Long | 누적 결제금액 (기본값 0, 로직은 4번 서브프로젝트에서 구현) |
| points | Long | 보유 적립금 (기본값 0, 로직은 5번 서브프로젝트에서 구현) |
| tier | Enum(`BASIC`, `SILVER`, `GOLD`, `VIP`) | 등급 (기본값 BASIC, 로직은 5번 서브프로젝트에서 구현) |
| createdAt | LocalDateTime | 가입일시 |

### Movie (영화)
| 필드 | 타입 | 설명 |
|---|---|---|
| id | Long | PK |
| tmdbId | Long | TMDB 고유 ID, 고유 (연동 로직은 2번 서브프로젝트) |
| title | String | 제목 |
| posterUrl | String | 포스터 이미지 URL |
| overview | String (TEXT) | 줄거리 |
| releaseDate | LocalDate | 개봉일 |
| runningTime | Integer | 상영시간(분) |
| ageRating | String | 관람등급 (예: "전체", "12", "15", "청불") — TMDB `release_dates` API의 KR 인증 정보 사용, 없으면 "정보없음" |
| createdAt | LocalDateTime | 등록일시 |

### Screen (상영관)
| 필드 | 타입 | 설명 |
|---|---|---|
| id | Long | PK |
| name | String | 예: "1관" |
| createdAt | LocalDateTime | 등록일시 |

### Seat (좌석)
| 필드 | 타입 | 설명 |
|---|---|---|
| id | Long | PK |
| screen | Screen (FK) | 소속 상영관 |
| rowLabel | String | 예: "A" |
| seatNumber | Integer | 예: 7 |
| grade | Enum(`STANDARD`, `PREMIUM`, `COUPLE`) | 좌석 등급 |
| createdAt | LocalDateTime | 등록일시 |

Screen 등록 시 행/열 개수를 지정하면 그에 맞춰 Seat 레코드가 일괄 생성된다 (구현 로직은 2번 서브프로젝트).

### Showtime (상영시간표)
| 필드 | 타입 | 설명 |
|---|---|---|
| id | Long | PK |
| movie | Movie (FK) | 상영 영화 |
| screen | Screen (FK) | 상영관 |
| format | Enum(`TWO_D`, `THREE_D`, `IMAX`, `FOUR_DX`) | 상영 포맷 |
| startTime | LocalDateTime | 시작시간 |
| endTime | LocalDateTime | 종료시간 |
| basePrice | Long | 기본 가격 |
| createdAt | LocalDateTime | 등록일시 |

가격 계산: `basePrice + 좌석등급별 추가요금 + 상영포맷별 추가요금`. 추가요금은 코드 상수로 관리:
- 좌석등급: PREMIUM +3000원, COUPLE +5000원, STANDARD +0원
- 상영포맷: THREE_D +2000원, IMAX +6000원, FOUR_DX +8000원, TWO_D +0원

관리자가 이 금액들을 직접 조정 가능하게 하는 기능은 이번 스코프에서 제외.

## 인증 (JWT)

- **Access Token**: 유효기간 30분. Payload에 memberId, role 포함. HS256 서명.
- **Refresh Token**: 유효기간 14일. 발급 시 DB(RefreshToken 테이블 또는 Member의 컬럼)에 저장하여 로그아웃 시 삭제(무효화) 가능하게 함.
- 비밀번호는 Spring Security `BCryptPasswordEncoder`로 해싱하여 저장.
- Redis는 이 서브프로젝트에서 사용하지 않음 (3번 서브프로젝트에서 좌석 락 용도로 도입 예정).

### API 엔드포인트

| Method | URL | 설명 | 인증 필요 |
|---|---|---|---|
| POST | `/api/auth/signup` | 회원가입 | X |
| POST | `/api/auth/login` | 로그인, accessToken+refreshToken 발급 | X |
| POST | `/api/auth/refresh` | refreshToken으로 accessToken 재발급 | X (refreshToken 자체로 검증) |
| POST | `/api/auth/logout` | refreshToken 무효화(DB에서 삭제) | O |

### 에러 처리

- 이메일 중복 가입 시도: 409 Conflict
- 로그인 실패(이메일/비밀번호 불일치): 401 Unauthorized
- 만료/위조된 토큰으로 요청: 401 Unauthorized
- 인증 필요한 API에 토큰 없이 접근: 401 Unauthorized
- 권한 부족(USER가 ADMIN 전용 API 접근): 403 Forbidden

## 패키지 구조

```
com.dw.movie
├── auth       (Member 엔티티, JWT 발급/검증, 회원가입/로그인/토큰갱신/로그아웃)
├── movie      (Movie 엔티티)
├── screen     (Screen, Seat 엔티티)
└── showtime   (Showtime 엔티티)
```

각 패키지는 like-spring 프로젝트와 동일하게 Controller → Service → Repository → Entity 계층 구조를 따른다.

## 테스트

- 각 API에 대해 Postman으로 직접 요청 보내서 응답 확인 (회원가입 → 로그인 → 토큰으로 인증 필요 API 접근 → 갱신 → 로그아웃 순서로 시나리오 테스트)
- JUnit 기반 단위/통합 테스트는 이번 서브프로젝트 범위에서는 선택사항 (필요시 추가)

## 스코프 제외 사항 (이번 서브프로젝트에서 하지 않는 것)

- TMDB 연동 로직 (2번 서브프로젝트)
- 상영관/좌석 등록 관리자 API (2번 서브프로젝트)
- 좌석 예약/Redis 락 (3번 서브프로젝트)
- 결제 로직 (4번 서브프로젝트)
- 적립금/등급 실제 계산 로직 (5번 서브프로젝트) — 필드만 존재, 로직은 없음
- 이메일 인증, 비밀번호 찾기, 소셜 로그인 — 스코프 아예 제외 (YAGNI)
- 프론트엔드 (6번 서브프로젝트)
