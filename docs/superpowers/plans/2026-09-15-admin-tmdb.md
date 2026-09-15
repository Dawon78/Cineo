# 관리자 기능 + TMDB 연동 Implementation Plan

**Goal:** 관리자가 TMDB ID로 영화를 등록하고, 상영관(좌석 자동생성) · 상영시간표를 생성/조회할 수 있는 ADMIN 전용 API 완성.

**Architecture:** `auth` 패키지와 동일한 Controller→Service→Repository→Entity 계층을 `movie`/`screen`/`showtime` 세 패키지에 반복. `JwtAuthenticationFilter`의 role 하드코딩 버그를 먼저 고치고, `@PreAuthorize`로 ADMIN 권한을 강제한다.

**Tech Stack:** Spring Boot, Spring Data JPA, Spring Security(`@PreAuthorize`), `RestClient`(TMDB 호출, 별도 의존성 불필요 — `spring-boot-starter-webmvc`에 포함됨)

**Spec:** [docs/superpowers/specs/2026-09-15-admin-tmdb-design.md](../specs/2026-09-15-admin-tmdb-design.md)

**실행 방식(이 프로젝트 고유 규칙):** 이 계획은 서브프로젝트 1(인증) 때와 동일하게 **직접 손으로 타이핑하며 이해하는 방식**으로 실행한다. 자동화된 subagent 실행이나 Claude의 자동 git 커밋은 쓰지 않는다 — 각 태스크는 "요구사항 확인 → 직접 작성 → 컴파일 확인 → 다음"으로 진행하고, 커밋은 사용자가 직접 한다.

## Global Constraints

- 이번 스코프는 생성(POST) + 조회(GET)만 — 수정/삭제 없음
- 모든 admin API는 인증 필요 + `ROLE_ADMIN` 필요
- Seat 등급은 생성 시 전부 `STANDARD` 고정
- Showtime의 `endTime`은 관리자가 입력하지 않고 서버가 자동 계산
- JUnit 테스트는 이번 스코프에서도 선택사항 — 컴파일 확인 + Postman 시나리오 테스트로 검증 (서브프로젝트 1과 동일한 검증 방식)

---

## 파일 구조

```
com.dw.movie.movie
├── Movie.java                      [Task 3]
├── MovieController.java            [Task 9]
├── MovieService.java               [Task 7]
├── MovieRepository.java            [Task 4]
├── TmdbClient.java                 [Task 5]
└── dto/
    ├── MovieRegisterRequest.java   [Task 8]
    └── MovieResponse.java          [Task 8]

com.dw.movie.screen
├── Screen.java                     [Task 12]
├── Seat.java                       [Task 12]
├── ScreenController.java           [Task 15]
├── ScreenService.java              [Task 14]
├── ScreenRepository.java           [Task 13]
├── SeatRepository.java             [Task 13]
└── dto/
    ├── ScreenRegisterRequest.java  [Task 15]
    └── ScreenResponse.java         [Task 15]

com.dw.movie.showtime
├── Showtime.java                   [Task 16]
├── ShowtimeController.java         [Task 19]
├── ShowtimeService.java            [Task 18]
├── ShowtimeRepository.java         [Task 17]
└── dto/
    ├── ShowtimeRegisterRequest.java [Task 19]
    └── ShowtimeResponse.java        [Task 19]

com.dw.movie.common.exception
├── DuplicateTmdbIdException.java   [Task 6]
└── TmdbMovieNotFoundException.java [Task 6]

수정: config/JwtAuthenticationFilter.java  [Task 10]
수정: config/SecurityConfig.java           [Task 11]
수정: application.yaml                     [Task 1]
```

---

## Task 1: TMDB API 키 설정

**Files:** Modify `src/main/resources/application.yaml`

- `jwt:` 블록 아래에 `tmdb.api-key: ${TMDB_API_KEY}` 추가
- IntelliJ Run Configuration에 `TMDB_API_KEY` 환경변수 등록 (TMDB 사이트에서 발급받은 키). https://www.themoviedb.org/settings/api 에서 계정 만들고 API 키(v3 auth) 발급
- 컴파일 확인 불필요(설정 파일만 변경) — 앱 재시작 시 정상 기동되는지만 확인

## Task 2: Movie 엔티티

**Files:** Create `src/main/java/com/dw/movie/movie/Movie.java`

**Interfaces:**
- Produces: `Movie(Long tmdbId, String title, String posterUrl, String overview, LocalDate releaseDate, Integer runningTime, String ageRating)` 생성자, `@NoArgsConstructor`, `@Getter`

필드: `id`(PK), `tmdbId`(Long, unique), `title`, `posterUrl`, `overview`, `releaseDate`(LocalDate), `runningTime`(Integer), `ageRating`(String), `createdAt`(LocalDateTime, 생성자에서 `LocalDateTime.now()`로 세팅).

`Member.java`와 같은 패턴(`@Entity @Getter @NoArgsConstructor`, 커스텀 생성자에서 필드 세팅)으로 작성.

## Task 3: MovieRepository

**Files:** Create `src/main/java/com/dw/movie/movie/MovieRepository.java`

**Interfaces:**
- Consumes: `Movie` (Task 2)
- Produces: `boolean existsByTmdbId(Long tmdbId)`, `Optional<Movie> findByTmdbId(Long tmdbId)`, 그 외 `JpaRepository<Movie, Long>` 기본 메서드(`save`, `findAll`, `findById`)

`auth`처럼 Repository 패턴(인터페이스+Impl)까지는 이번 스코프에서 생략 — Movie는 복잡한 조회가 없어서 `JpaRepository` 직접 상속으로 충분 (YAGNI, `auth`의 3단 분리는 향후 MyBatis 필요해지면 그때 리팩터링).

## Task 4: TMDB 연동 예외

**Files:**
- Create: `src/main/java/com/dw/movie/common/exception/DuplicateTmdbIdException.java`
- Create: `src/main/java/com/dw/movie/common/exception/TmdbMovieNotFoundException.java`
- Modify: `src/main/java/com/dw/movie/common/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Produces: `DuplicateTmdbIdException(String message)` → 409, `TmdbMovieNotFoundException(String message)` → 404

둘 다 `DuplicateEmailException`/`InvalidTokenException`과 동일한 형태(`RuntimeException` 상속, 생성자에서 `super(message)`). `GlobalExceptionHandler`에 `@ExceptionHandler` 두 개 추가 — 각각 `HttpStatus.CONFLICT`, `HttpStatus.NOT_FOUND`로 매핑.

## Task 5: TmdbClient

**Files:** Create `src/main/java/com/dw/movie/movie/TmdbClient.java`

**Interfaces:**
- Consumes: `tmdb.api-key`(Task 1의 설정값), `TmdbMovieNotFoundException`(Task 4)
- Produces: `TmdbMovieInfo fetchMovieInfo(Long tmdbId)` — 내부 레코드/클래스 `TmdbMovieInfo(String title, String posterUrl, String overview, LocalDate releaseDate, Integer runningTime, String ageRating)`

`RestClient`(Spring 내장, 별도 의존성 불필요)로 두 번 호출:
1. `GET https://api.themoviedb.org/3/movie/{tmdbId}?api_key={key}&language=ko-KR` → title, poster_path, overview, release_date, runtime
2. `GET https://api.themoviedb.org/3/movie/{tmdbId}/release_dates?api_key={key}` → `results` 배열에서 `iso_3166_1 == "KR"`인 항목 찾아 관람등급 추출, 없으면 `"정보없음"`

첫 번째 호출이 404면 `TmdbMovieNotFoundException` 던짐.

## Task 6: MovieService

**Files:** Create `src/main/java/com/dw/movie/movie/MovieService.java`

**Interfaces:**
- Consumes: `MovieRepository`(Task 3), `TmdbClient`(Task 5), `Movie` 생성자(Task 2)
- Produces: `Movie registerMovie(Long tmdbId)`, `List<Movie> getAllMovies()`, `Movie getMovie(Long id)`

`registerMovie`: `existsByTmdbId` 체크(중복이면 `DuplicateTmdbIdException`) → `tmdbClient.fetchMovieInfo(tmdbId)` → `Movie` 생성·저장.
`getMovie`: `findById().orElseThrow(...)` — 못 찾으면 간단한 404 처리(기존 `InvalidTokenException` 재사용하지 말고, 없으면 적당한 이름의 새 예외를 이 태스크에서 판단해 추가해도 됨 — 혹은 `TmdbMovieNotFoundException` 이름이 안 맞으니 여기선 그냥 `NoSuchElementException` 정도로 간단히 처리하고 `GlobalExceptionHandler`에 404 매핑 추가).

## Task 7: Movie DTO + Controller

**Files:**
- Create: `src/main/java/com/dw/movie/movie/dto/MovieRegisterRequest.java` (필드: `Long tmdbId`)
- Create: `src/main/java/com/dw/movie/movie/dto/MovieResponse.java` (Movie의 모든 조회용 필드 + `@AllArgsConstructor`)
- Create: `src/main/java/com/dw/movie/movie/MovieController.java`

**Interfaces:**
- Consumes: `MovieService`(Task 6)

```
POST /api/admin/movies       @PreAuthorize("hasRole('ADMIN')")
GET  /api/admin/movies       @PreAuthorize("hasRole('ADMIN')")
GET  /api/admin/movies/{id}  @PreAuthorize("hasRole('ADMIN')")
```

`@RequestMapping("/api/admin/movies")`, `MemberController` 패턴 그대로.

## Task 8: JwtAuthenticationFilter role 버그 수정

**Files:** Modify `src/main/java/com/dw/movie/config/JwtAuthenticationFilter.java`

지금 하드코딩된 `List.of(new SimpleGrantedAuthority("ROLE_" + Role.USER))`를, 토큰의 `role` claim을 실제로 읽어서 구성하도록 수정. `JwtTokenProvider`에 `String getRole(String token)` 메서드 추가(`claims.get("role", String.class)`) 필요 — 이 태스크에 포함.

**Interfaces:**
- Modifies: `JwtTokenProvider`에 `getRole(String token)` 추가
- Produces: 필터가 실제 role로 `SimpleGrantedAuthority` 구성

## Task 9: SecurityConfig에 메서드 보안 활성화

**Files:** Modify `src/main/java/com/dw/movie/config/SecurityConfig.java`

`@EnableMethodSecurity` 어노테이션 클래스에 추가 (이게 있어야 `@PreAuthorize`가 동작함). `/api/admin/**`은 기존 `.anyRequest().authenticated()` 규칙에 이미 포함되니 URL 레벨 규칙은 그대로 두고, 실제 ADMIN 체크는 `@PreAuthorize`로.

## Task 10: 테스트용 ADMIN 계정 준비

DB에서 직접 회원 하나를 ADMIN으로 바꿔서 테스트 — 코드 작성 없음:
```sql
UPDATE member SET role = 'ADMIN' WHERE email = 'test@test.com';
```
MySQL 클라이언트(또는 IntelliJ Database 툴)로 직접 실행.

## Task 11: Screen, Seat 엔티티

**Files:** Create `src/main/java/com/dw/movie/screen/Screen.java`, `src/main/java/com/dw/movie/screen/Seat.java`, `src/main/java/com/dw/movie/screen/SeatGrade.java`(enum: `STANDARD`, `PREMIUM`, `COUPLE`)

`Screen`: `id`, `name`(String), `createdAt`, 생성자 `Screen(String name)`.
`Seat`: `id`, `screen`(`@ManyToOne` FK, `Screen`), `rowLabel`(String), `seatNumber`(Integer), `grade`(Enum `SeatGrade`), `createdAt`, 생성자 `Seat(Screen screen, String rowLabel, Integer seatNumber, SeatGrade grade)`.

## Task 12: ScreenRepository, SeatRepository

**Files:** Create `src/main/java/com/dw/movie/screen/ScreenRepository.java`, `src/main/java/com/dw/movie/screen/SeatRepository.java`

**Interfaces:**
- Produces: 둘 다 `JpaRepository` 기본 메서드만 (`save`, `findAll`, `findById`) — Task 11의 `Screen`/`Seat` 대상.

## Task 13: ScreenService (좌석 자동생성)

**Files:** Create `src/main/java/com/dw/movie/screen/ScreenService.java`

**Interfaces:**
- Consumes: `ScreenRepository`, `SeatRepository`(Task 12), `Screen`/`Seat`(Task 11)
- Produces: `Screen createScreen(String name, int rowCount, int colCount)`, `List<Screen> getAllScreens()`

`createScreen`: `Screen` 저장 → 이중 for문(`row 0..rowCount`, `col 1..colCount`)으로 `Seat` 생성. `rowLabel`은 `(char)('A' + row)`로 변환, `grade`는 항상 `SeatGrade.STANDARD`. 생성된 `Seat` 리스트를 `SeatRepository`에 저장(`saveAll`).

## Task 14: Screen DTO + Controller

**Files:**
- Create: `src/main/java/com/dw/movie/screen/dto/ScreenRegisterRequest.java` (필드: `String name`, `int rowCount`, `int colCount`)
- Create: `src/main/java/com/dw/movie/screen/dto/ScreenResponse.java`
- Create: `src/main/java/com/dw/movie/screen/ScreenController.java`

```
POST /api/admin/screens   @PreAuthorize("hasRole('ADMIN')")
GET  /api/admin/screens   @PreAuthorize("hasRole('ADMIN')")
```

## Task 15: Showtime 엔티티

**Files:** Create `src/main/java/com/dw/movie/showtime/Showtime.java`, `src/main/java/com/dw/movie/showtime/ShowtimeFormat.java`(enum: `TWO_D`, `THREE_D`, `IMAX`, `FOUR_DX`)

필드: `id`, `movie`(`@ManyToOne` FK), `screen`(`@ManyToOne` FK), `format`(Enum), `startTime`(LocalDateTime), `endTime`(LocalDateTime), `basePrice`(Long), `createdAt`.
생성자: `Showtime(Movie movie, Screen screen, ShowtimeFormat format, LocalDateTime startTime, LocalDateTime endTime, Long basePrice)` — `endTime`은 이 생성자에 인자로 받되, **실제 계산은 Service에서** 해서 넘겨줌(엔티티는 계산 로직을 갖지 않고 Service가 계산해서 전달 — 책임 분리).

## Task 16: ShowtimeRepository

**Files:** Create `src/main/java/com/dw/movie/showtime/ShowtimeRepository.java`

`JpaRepository<Showtime, Long>` 기본 메서드만.

## Task 17: ShowtimeService (endTime 자동계산)

**Files:** Create `src/main/java/com/dw/movie/showtime/ShowtimeService.java`

**Interfaces:**
- Consumes: `ShowtimeRepository`(Task 16), `MovieRepository`(Task 3), `ScreenRepository`(Task 12)
- Produces: `Showtime createShowtime(Long movieId, Long screenId, ShowtimeFormat format, LocalDateTime startTime, Long basePrice)`, `List<Showtime> getAllShowtimes()`

`movieId`/`screenId`로 각각 조회(없으면 간단한 404), `endTime = startTime.plusMinutes(movie.getRunningTime())` 계산 후 `Showtime` 생성·저장.

## Task 18: Showtime DTO + Controller

**Files:**
- Create: `src/main/java/com/dw/movie/showtime/dto/ShowtimeRegisterRequest.java` (필드: `Long movieId`, `Long screenId`, `ShowtimeFormat format`, `LocalDateTime startTime`, `Long basePrice`)
- Create: `src/main/java/com/dw/movie/showtime/dto/ShowtimeResponse.java`
- Create: `src/main/java/com/dw/movie/showtime/ShowtimeController.java`

```
POST /api/admin/showtimes   @PreAuthorize("hasRole('ADMIN')")
GET  /api/admin/showtimes   @PreAuthorize("hasRole('ADMIN')")
```

## Task 19: 전체 Postman 시나리오 테스트

1. Task 10에서 ADMIN으로 바꾼 계정으로 로그인 → accessToken 확보
2. `POST /api/admin/movies` (실제 TMDB 영화 ID로, 예: 27205 = 인셉션) → 201, TMDB 정보 자동으로 채워졌는지 확인
3. `GET /api/admin/movies` → 목록에 방금 등록한 영화 있는지
4. `POST /api/admin/screens` (`{"name":"1관","rowCount":5,"colCount":8}`) → 201, DB에서 Seat 40개 생성됐는지 확인
5. `POST /api/admin/showtimes` (movieId, screenId, format, startTime, basePrice) → 201, `endTime`이 `startTime + runningTime`인지 확인
6. 일반 USER 계정으로 로그인해서 같은 admin API 접근 → 403 확인

---

## Self-Review 메모

- 스펙의 모든 섹션(패키지 구조, 엔티티 4종, TMDB 연동, 좌석 자동생성, Showtime endTime 자동계산, 권한체크, 엔드포인트 7개, 테스트)에 대응하는 태스크 있음 — Task 2~19가 각각 커버.
- Task 6에서 `MovieService.getMovie`의 not-found 예외 이름이 확정 안 되어 있음 — 실제 작성 시점에 적절한 이름으로 정하고 `GlobalExceptionHandler`에 매핑 추가(Task 6 안에서 처리).
- 각 태스크의 파일 경로/메서드 시그니처는 이후 태스크가 참조하는 이름과 일치하도록 작성 — `MovieRepository`, `ScreenRepository`, `SeatRepository`, `ShowtimeRepository`, `TmdbClient.fetchMovieInfo`, `ScreenService.createScreen`, `ShowtimeService.createShowtime` 이름 통일 확인.
