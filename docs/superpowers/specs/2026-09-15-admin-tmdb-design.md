# 관리자 기능 + TMDB 연동 — 설계

서브프로젝트 로드맵 2번. 관리자가 영화/상영관/상영시간표를 등록·조회하는 기능. 서브프로젝트 1번(도메인 모델 + 인증)에서 만든 JWT 인증 위에 ADMIN 권한 체크를 얹어서 구현한다.

## 스코프

- Movie, Screen, Showtime **생성 + 조회만** 구현 (수정/삭제는 YAGNI로 이번 스코프 제외)
- TMDB 연동: 관리자가 TMDB 영화 ID를 입력하면 서버가 TMDB API를 호출해 정보를 자동으로 채워 저장
- Screen 등록 시 행/열 개수만 받아 Seat를 일괄 생성 (등급은 전부 STANDARD 고정, 개별 등급 조정 API는 이번 스코프 제외)
- 좌석 예약(Redis 동시성 제어)은 3번 서브프로젝트에서, 최종 결제 가격 계산도 그때 구현 — 이번엔 `basePrice`만 저장

## 패키지 구조

`auth` 패키지와 동일하게 Controller → Service → Repository → Entity 계층을 따르되, 도메인별로 분리:

```
com.dw.movie.movie
├── Movie.java
├── MovieController.java
├── MovieService.java
├── MovieRepository.java
├── TmdbClient.java          (TMDB API 호출 전담)
└── dto/
    ├── MovieRegisterRequest.java   (tmdbId만 받음)
    └── MovieResponse.java

com.dw.movie.screen
├── Screen.java
├── Seat.java
├── ScreenController.java
├── ScreenService.java
├── ScreenRepository.java
├── SeatRepository.java
└── dto/
    ├── ScreenRegisterRequest.java  (name, rowCount, colCount)
    └── ScreenResponse.java

com.dw.movie.showtime
├── Showtime.java
├── ShowtimeController.java
├── ShowtimeService.java
├── ShowtimeRepository.java
└── dto/
    ├── ShowtimeRegisterRequest.java  (movieId, screenId, format, startTime, basePrice)
    └── ShowtimeResponse.java
```

## 엔티티 (서브프로젝트 1 설계 문서에서 재확인)

### Movie
| 필드 | 타입 | 설명 |
|---|---|---|
| id | Long | PK |
| tmdbId | Long | TMDB 고유 ID, 고유(unique) |
| title | String | 제목 |
| posterUrl | String | 포스터 이미지 URL |
| overview | String (TEXT) | 줄거리 |
| releaseDate | LocalDate | 개봉일 |
| runningTime | Integer | 상영시간(분) |
| ageRating | String | 관람등급 — TMDB `release_dates` API의 KR 인증 정보, 없으면 "정보없음" |
| createdAt | LocalDateTime | 등록일시 |

### Screen
| 필드 | 타입 | 설명 |
|---|---|---|
| id | Long | PK |
| name | String | 예: "1관" |
| createdAt | LocalDateTime | 등록일시 |

### Seat
| 필드 | 타입 | 설명 |
|---|---|---|
| id | Long | PK |
| screen | Screen (FK) | 소속 상영관 |
| rowLabel | String | 예: "A" |
| seatNumber | Integer | 예: 7 |
| grade | Enum(`STANDARD`, `PREMIUM`, `COUPLE`) | 이번 스코프에선 전부 STANDARD로 생성 |
| createdAt | LocalDateTime | 등록일시 |

### Showtime
| 필드 | 타입 | 설명 |
|---|---|---|
| id | Long | PK |
| movie | Movie (FK) | 상영 영화 |
| screen | Screen (FK) | 상영관 |
| format | Enum(`TWO_D`, `THREE_D`, `IMAX`, `FOUR_DX`) | 상영 포맷 |
| startTime | LocalDateTime | 시작시간 (관리자 입력) |
| endTime | LocalDateTime | 종료시간 — **서버가 `startTime + movie.runningTime(분)`으로 자동 계산** |
| basePrice | Long | 기본 가격 |
| createdAt | LocalDateTime | 등록일시 |

가격 계산 공식(3번 서브프로젝트에서 예매 시점에 적용): `basePrice + 좌석등급별 추가요금 + 상영포맷별 추가요금`
- 좌석등급: PREMIUM +3000원, COUPLE +5000원, STANDARD +0원
- 상영포맷: THREE_D +2000원, IMAX +6000원, FOUR_DX +8000원, TWO_D +0원

## TMDB 연동

- `application.yaml`에 `tmdb.api-key: ${TMDB_API_KEY}` (환경변수로 분리, IntelliJ Run Configuration에 `DB_PASSWORD`/`JWT_SECRET`과 같은 방식으로 등록)
- `TmdbClient`가 TMDB API를 두 번 호출:
  1. `GET /movie/{tmdbId}` → title, poster_path, overview, release_date, runtime
  2. `GET /movie/{tmdbId}/release_dates` → KR 인증 정보(관람등급). 없으면 `ageRating = "정보없음"`
- 이미 등록된 `tmdbId` 재등록 시도 → 409 Conflict (`DuplicateTmdbIdException`, 회원가입 이메일 중복 체크와 동일 패턴)
- TMDB에 없는 ID거나 API 호출 실패 → `TmdbMovieNotFoundException` → 404 Not Found

## Screen 좌석 자동 생성

`ScreenService.createScreen(name, rowCount, colCount)`:
1. `Screen` 저장
2. `rowCount × colCount`개의 `Seat` 일괄 생성: `rowLabel`은 행 순서대로 A, B, C..., `seatNumber`는 1~`colCount`, `grade`는 전부 `STANDARD`

## Showtime 생성

`ShowtimeService.createShowtime(movieId, screenId, format, startTime, basePrice)`:
1. `movieId`, `screenId`로 `Movie`, `Screen` 조회 (없으면 404)
2. `endTime = startTime.plusMinutes(movie.getRunningTime())`으로 자동 계산
3. 저장

## 관리자 권한 체크

- **기존 버그 수정**: `JwtAuthenticationFilter`가 지금 role을 `USER`로 하드코딩하고 있음 — 토큰의 `role` claim을 실제로 읽어서 `SimpleGrantedAuthority("ROLE_" + role)`을 구성하도록 수정
- `SecurityConfig`에 `@EnableMethodSecurity` 추가
- 각 관리자 API 메서드에 `@PreAuthorize("hasRole('ADMIN')")` 적용
- ADMIN이 아닌 사용자가 접근 시 → 403 Forbidden (서브프로젝트 1 설계 문서의 에러 처리 규칙과 일치)

## API 엔드포인트

모두 인증 필요 + ADMIN 권한 필요.

| Method | URL | 설명 |
|---|---|---|
| POST | `/api/admin/movies` | TMDB ID로 영화 등록 |
| GET | `/api/admin/movies` | 영화 목록 |
| GET | `/api/admin/movies/{id}` | 영화 상세 |
| POST | `/api/admin/screens` | 상영관 등록 (좌석 자동 생성) |
| GET | `/api/admin/screens` | 상영관 목록 |
| POST | `/api/admin/showtimes` | 상영시간표 등록 |
| GET | `/api/admin/showtimes` | 상영시간표 목록 |

## 테스트

Postman으로 시나리오 테스트: ADMIN 계정 로그인 → 영화 등록(TMDB ID) → 조회 → 상영관 등록(좌석 자동 생성 확인) → 상영시간표 등록(endTime 자동계산 확인) → USER 권한 계정으로 같은 API 접근 시 403 확인.

## 스코프 제외 사항

- Movie/Screen/Showtime 수정·삭제 API
- Screen 좌석 개별 등급(PREMIUM/COUPLE) 지정 API
- 좌석 예약, Redis 동시성 제어, 실제 결제 가격 계산 (3번 서브프로젝트)
