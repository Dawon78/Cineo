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

## 기술 스택 / 데이터 접근 방식 (2026-09-09 업데이트)

JPA와 MyBatis를 함께 사용한다. 역할 분담 기준:

- **JPA**: 단순 CRUD (예: 회원 가입/조회, 영화 등록/조회)
- **MyBatis**: 복잡한 동적 조회, 통계 (예: 좌석 현황 검색, 조건별 상영 검색 등 — 주로 2번 이후 서브프로젝트에서 등장)

`build.gradle.kts`에 `mybatis-spring-boot-starter` 의존성 추가 (기존 `spring-boot-starter-data-jpa`는 유지).

## 패키지 구조

```
com.dw.movie
├── auth       (Member 엔티티, JWT 발급/검증, 회원가입/로그인/토큰갱신/로그아웃)
├── movie      (Movie 엔티티)
├── screen     (Screen, Seat 엔티티)
└── showtime   (Showtime 엔티티)
```

각 기능 패키지 내부는 다음 계층으로 구성한다 (예: `auth` 패키지 기준):

```
com.dw.movie.auth
├── Member.java                 (도메인 객체)
├── MemberController.java       (HTTP 요청 처리)
├── dto/                        (요청/응답 DTO)
├── MemberService.java          (비즈니스 로직 — 구현체 하나만 두고 인터페이스는 만들지 않음. YAGNI)
├── MemberRepository.java       (도메인 관점의 저장/조회 인터페이스)
└── MemberRepositoryImpl.java   (Repository 구현체 — 내부에서 JPA/MyBatis를 조합)
```

**Repository 패턴**: Service는 `MemberRepository` 인터페이스만 의존하고, `MemberRepositoryImpl`이 내부적으로 Spring Data `JpaRepository`(단순 CRUD)와 MyBatis Mapper(복잡 조회)를 조합해서 구현한다. 이렇게 하면 Service 입장에서 특정 조회가 JPA로 구현됐는지 MyBatis로 구현됐는지 신경 쓸 필요가 없다.

Member는 현재 복잡한 조회가 없으므로 `MemberRepositoryImpl` 내부는 JPA 위임만 있으면 된다 (MyBatis Mapper는 필요해지는 도메인부터 추가).

**기존 코드 리팩터링**: 현재 `MemberRepository`가 `JpaRepository`를 직접 상속하는 방식으로 되어 있는데, 이 패턴에 맞춰 다음과 같이 정리한다.
- `MemberJpaRepository` 신설 — `JpaRepository<Member, Long>` 상속, 실제 JPA 접근 담당
- `MemberRepository` — 도메인 인터페이스로 재정의 (Service가 의존하는 대상)
- `MemberRepositoryImpl` — `MemberRepository`를 구현, 내부에서 `MemberJpaRepository`에 위임

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
