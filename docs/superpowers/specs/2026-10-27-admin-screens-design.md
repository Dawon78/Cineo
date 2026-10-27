# 관리자 화면 설계

## 개요

Cineo 프론트엔드 추가 기능 5번. 지금까지 관리자 기능(영화·상영관·상영시간 등록)은 API로만 있고, 데이터는 `LocalDataInitializer`가 자동으로 넣었다. 이번 작업은 관리자가 브라우저에서 직접 서비스를 운영할 수 있게 화면을 붙이고, 그에 필요한 백엔드 API를 최소한으로 추가한다.

### 범위 (선택지 B)

- 영화·상영관·상영시간의 **등록과 조회** 화면
- 영화 등록을 TMDB ID 입력 대신 **TMDB 제목 검색**으로
- **예매 기록이 없는 상영시간만 삭제**

### 범위 밖 (일부러 하지 않음)

- 영화·상영관 수정·삭제: 연결된 상영·예매가 많아 규칙이 복잡해진다
- 예매 현황·매출 대시보드: 나중에 MyBatis 동적 조회를 붙일 때 따로 한다
- 상영시간 겹침 검사: 기존 "알려진 한계" 항목 그대로 둔다
- 관리자 계정 생성·권한 변경: 관리자는 DB에 있는 `admin@test.com` 하나

## 현재 상태

| | 등록 | 조회 | 수정·삭제 |
|---|---|---|---|
| 영화 `/api/admin/movies` | TMDB ID로만 | O | X |
| 상영관 `/api/admin/screens` | 이름, 행·열 수 | O (id, 이름만) | X |
| 상영시간 `/api/admin/showtimes` | 영화, 상영관, 포맷, 시작 시각, 기본 가격 | O | X |

- 서버는 `/api/admin/**`를 `hasRole('ADMIN')`으로 막고 있다.
- 화면은 지금 로그인한 사람이 관리자인지 알 방법이 없다 (`/api/members/me`에 role이 없다).

## 백엔드

### 1. `/api/members/me`에 role 추가

- 회원 응답 DTO에 `role` (`USER` / `ADMIN`) 필드를 추가한다.
- 화면은 이 값으로 관리자 메뉴 노출과 관리자 화면 진입을 판단한다.
- 화면 쪽 판단은 **편의용**이다. 실제 보안은 서버의 `hasRole('ADMIN')`이 맡는다. 화면을 우회해 API를 직접 호출해도 403이 난다.

### 2. TMDB 영화 검색

`GET /api/admin/movies/search?query={검색어}`

- `/api/admin/**` 아래라서 관리자만 호출할 수 있다.
- `TmdbClient.searchMovies(query)` 추가: TMDB `/search/movie`를 `language=ko-KR`, `include_adult=false`로 호출하고 첫 페이지(최대 20개)만 쓴다.
- 브라우저가 TMDB를 직접 부르지 않고 서버를 거치는 이유: TMDB API 키를 브라우저에 노출하지 않기 위해서다.
- 응답 항목: `tmdbId`, `title`, `releaseDate`(문자열), `posterUrl`(없으면 null), `registered`(이미 등록한 영화인지)
  - `registered`는 `MovieService.searchTmdb(query)`가 채운다. 검색 결과의 TMDB ID 목록으로 `MovieRepository.findByTmdbIdIn(ids)`를 한 번(IN 조회) 호출해, 결과에 있는 ID면 true다.
- **TMDB 검색 결과는 개봉일이 없으면 `null`이 아니라 빈 문자열 `""`을 준다.** 기존 상세 조회처럼 `LocalDate`로 받으면 역직렬화가 실패하므로, 검색 응답의 개봉일은 문자열로 받아 그대로 내려준다.
- 검색어가 비어 있거나 공백뿐이면 TMDB를 부르지 않고 400을 준다 (`@NotBlank` 검증).
- TMDB 호출이 실패하면 기존 TMDB 예외 처리 흐름을 따른다.
- 등록은 기존 `POST /api/admin/movies` (body: `tmdbId`)를 그대로 쓴다.

### 3. 상영시간 삭제

`DELETE /api/admin/showtimes/{id}` → 성공 시 204

처리 순서 (`ShowtimeService.deleteShowtime`, `@Transactional`):

1. `showtimeRepository.findByIdForUpdate(id)`로 상영시간 행에 비관적 락을 건다. 없으면 기존 `ShowtimeNotFoundException`(404).
   - 예매 확정(`ReservationService.confirm`)도 같은 행에 같은 락을 건다. 그래서 확정과 삭제가 동시에 들어와도 순서대로 처리된다.
2. **예매 기록이 하나라도 있으면 거절** (취소된 예매 포함) → 409
   - `reservationRepository.existsByShowtimeId(id)` 추가
   - 이유: 취소된 예매도 `showtime_id`로 상영시간을 참조하고 있어 지우면 FK 제약 위반이다. 결제·환불 이력도 남아 있어야 한다.
3. **누군가 좌석을 선점 중이면 거절** → 409
   - `SeatHoldService.hasAnyHold(showtimeId, seatIds)` 추가: `seatRepository.findByScreen_Id(상영관 id)`로 얻은 좌석 id로 `seat-hold:{showtimeId}:{seatId}` 키 목록을 만들어 Redis `multiGet` 한 번으로 확인한다.
   - 이유: 사용자가 좌석을 잡고 결제하는 사이 상영이 지워지면, 예매 확정에서 "상영시간 없음" 예외가 **결제 확인 전에** 나서 자동 환불이 되지 않는다. 결제는 됐는데 예매도 환불도 없는 상태가 된다.
4. 둘 다 아니면 삭제한다.

예외: `ShowtimeNotDeletableException` (`common/exception`) 하나를 만들고, 메시지로 이유를 구분한다. `GlobalExceptionHandler`에서 409로 변환한다.

- "예매 기록이 있는 상영시간은 삭제할 수 없습니다."
- "좌석을 선점 중인 사용자가 있어 삭제할 수 없습니다. 잠시 후 다시 시도해 주세요."

### 그대로 두는 것

- 상영관 등록·조회 API (좌석 등급은 지금처럼 전부 일반석)
- 상영시간 목록 `GET /api/admin/showtimes` (필터는 화면에서)

## 프론트엔드

### 화면 구조

```
/admin               → /admin/movies로 이동
/admin/movies        영화 관리
/admin/screens       상영관 관리
/admin/showtimes     상영시간 관리
```

- `AdminLayout`: 위에 [영화 | 상영관 | 상영시간] 탭(`NavLink`), 아래에 각 화면(`<Outlet>`). React Router 중첩 라우트를 쓴다.
- 권한 확인은 `AdminLayout`에서 한 번만 한다. 들어올 때 `/api/members/me`를 호출해서
  - 401: 로그인 화면으로 보내고, 로그인 후 원래 관리자 화면으로 돌아온다 (기존 `state.from` 방식)
  - role이 `ADMIN`이 아님: "관리자만 볼 수 있는 화면이에요" 안내
  - `ADMIN`: 하위 화면을 보여준다
- 헤더: 로그인 상태면 `/me`를 불러 role이 `ADMIN`일 때만 [관리자] 메뉴를 보여준다. 로그인 상태가 바뀌면(토큰 변경 이벤트) 다시 확인한다.

### 영화 관리 (`AdminMoviesPage`)

- 등록된 영화 표: 작은 포스터, 제목, 개봉일, 러닝타임, 관람 등급. 러닝타임이 없으면 "러닝타임 없음"으로 표시한다 (그 영화는 상영시간 등록에서 막힌다).
- TMDB 검색: 검색창 + [검색] 버튼. **버튼이나 Enter를 눌렀을 때만** 검색한다 (`<form onSubmit>`). 타이핑마다 TMDB를 부르지 않는다.
- 검색 결과: 포스터 카드 + [등록] 버튼. `registered`면 "등록됨"으로 표시하고 버튼을 막는다. 등록에 성공하면 영화 표를 다시 불러오고 그 카드를 "등록됨"으로 바꾼다.
- 등록 중에는 그 카드의 버튼만 막는다 (중복 등록 방지).

### 상영관 관리 (`AdminScreensPage`)

- 상영관 목록: 이름
- 등록 폼: 이름, 행 수(1~26), 열 수(1~50). 서버 검증과 같은 범위를 `input`의 `min`/`max`로도 건다.
- 입력하는 동안 좌석판 미리보기(A1, A2 …)를 그린다. 등록 전에 모양을 확인하는 용도다.

### 상영시간 관리 (`AdminShowtimesPage`)

등록 폼:

- 영화 선택, 상영관 선택, 포맷(2D / 3D / IMAX / 4DX), 시작 일시(`datetime-local`), 기본 가격(기본값 12,000원)
- 영화와 시작 일시를 고르면 "종료 예정 hh:mm"을 미리 보여준다 (러닝타임으로 계산).
- 러닝타임 없는 영화를 고르면 등록 버튼 위에 안내를 띄운다. 최종 판단은 서버 검증이 한다.

목록:

- 영화 선택·날짜로 화면에서 필터링한다.
- 행: 날짜, 시작~종료, 영화, 상영관, 포맷, 기본 가격, [삭제]
- [삭제]는 확인 창 후 호출한다. 409면 서버 메시지를 그대로 보여준다. 성공하면 목록을 다시 불러온다.
- 삭제 가능 여부를 미리 계산해서 버튼을 숨기지 않는다. 상영마다 예매 수를 조회해야 해서 쿼리가 늘어난다. 판단은 서버 한 곳에서만 한다.

### 스타일

- 기존 `.btn`, `.field`, `.notice`를 그대로 쓴다.
- 관리자 표(`.admin-table`), 관리자 탭, 폼 배치 스타일만 추가한다.

### 파일

- 새 파일: `frontend/src/pages/admin/AdminLayout.jsx`, `AdminMoviesPage.jsx`, `AdminScreensPage.jsx`, `AdminShowtimesPage.jsx`
- 수정: `frontend/src/App.jsx` (라우트, 헤더 메뉴), `frontend/src/App.css`

## 테스트

백엔드 단위 테스트 (Mockito, 기존 테스트 스타일):

- `예매가_있는_상영시간은_삭제할_수_없다`
- `좌석을_선점_중인_상영시간은_삭제할_수_없다`
- `예매도_선점도_없으면_상영시간이_삭제된다`
- `TMDB_검색_결과에_이미_등록한_영화를_표시한다`
- `SeatHoldService`: `선점된_좌석이_하나라도_있으면_hasAnyHold가_true다`

직접 확인:

- `admin@test.com`으로 로그인해서 세 화면의 등록·조회·삭제
- 예매가 있는 상영시간 삭제 → 409 메시지
- 일반 회원으로 `/admin` 진입 → 안내 문구, 관리자 API 직접 호출 → 403
- `npm run lint`, `npm run build`

## 알려진 한계

- 상영시간 겹침 검사 없음: 같은 상영관에 시간이 겹치는 상영을 등록할 수 있다.
- 선점이 만료된 뒤에도 결제는 진행될 수 있다 (기존 정책). 이 틈에 상영이 지워지면 예매 확정이 결제 확인 전에 실패해 자동 환불이 안 된다. 선점 확인(3번)으로 대부분 막지만 이 좁은 경우는 남는다.
- TMDB 검색은 첫 페이지(20개)만 보여준다.

## 브랜치·커밋

- 브랜치: `feat/admin-screens`
- 커밋 단위 (예시):
  1. `feat: 내 정보 응답에 회원 권한(role) 추가`
  2. `feat: 관리자 TMDB 영화 검색 API 추가`
  3. `feat: 예매 기록과 선점이 없는 상영시간 삭제 API 추가`
  4. `feat: 관리자 화면 틀과 권한 확인 추가`
  5. `feat: 관리자 영화·상영관·상영시간 관리 화면 추가`
