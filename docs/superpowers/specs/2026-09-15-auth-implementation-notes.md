# 인증 구현 — 설계 이유 정리 (2026-09-15)

회원가입/로그인 기능을 만들면서 "왜 이렇게 짰는지"를 나중에 면접에서 설명할 수 있도록 남겨두는 메모. 코드 자체는 각 파일 참고, 여기는 판단 근거만.

## 1. Repository 패턴 (`MemberRepository` / `MemberJpaRepository` / `MemberRepositoryImpl`)

- **왜 3개로 쪼갰나**: `MemberService`가 "데이터가 JPA로 조회되는지 MyBatis로 조회되는지"를 몰라도 되게 하려고. `MemberService`는 `MemberRepository`(도메인 인터페이스)에만 의존하고, 실제 구현(`MemberRepositoryImpl`)이 내부에서 JPA(`MemberJpaRepository`)나 나중에 추가될 MyBatis Mapper 중 뭘 쓸지 결정.
- **적용 기준**: 단순 CRUD(회원 저장/조회)는 JPA, 나중에 나올 복잡한 동적 조회(좌석 검색 등)는 MyBatis. 이 프로젝트 전체에서 반복될 패턴이라 처음부터 이 구조로 잡음.
- **인터페이스 vs 구현체 관계 실수 포인트**: `MemberRepository`가 `MemberRepositoryImpl`을 알면(참조하면) 순환 참조가 생김. 화살표는 `Impl → 인터페이스` 방향 하나만 있어야 함.

## 2. 비밀번호 저장 — BCrypt

- 평문 저장 절대 금지. `PasswordEncoder.encode()`로 해싱.
- 로그인 시 검증은 복호화가 아니라 `matches(평문, 해시값)` — BCrypt는 단방향이라 되돌릴 수 없음.

## 3. DTO를 따로 쓰는 이유

- 엔티티(`Member`)를 요청/응답에 직접 노출하면: 가입 요청에 `role: ADMIN`을 클라이언트가 몰래 끼워넣을 수 있고, 응답에 비밀번호 해시값까지 노출될 위험.
- `SignupRequest`/`LoginRequest`(요청 전용), `TokenResponse`(응답 전용)로 필요한 필드만 노출.

## 4. 예외 처리 — 커스텀 예외 + `@RestControllerAdvice`

- `DuplicateEmailException`(409), `InvalidCredentialsException`(401)을 `RuntimeException` 상속으로 만들고, `GlobalExceptionHandler`가 전역으로 잡아서 상태코드 매핑.
- 로그인 실패 시 "이메일 없음"과 "비밀번호 틀림"을 **구분하지 않고 같은 메시지**로 응답 — 공격자가 이메일 존재 여부를 추측하지 못하게(계정 열거 공격 방지).
- `com.dw.movie.common.exception` 패키지에 둔 이유: auth뿐 아니라 movie/screen/showtime에서도 재사용할 공용 예외 처리라서.

## 5. JWT — Access/Refresh 토큰 분리

- **Access Token(30분)**: 매 요청마다 헤더에 실려 다녀서 탈취 위험이 상대적으로 높음 → 수명 짧게 해서 탈취돼도 피해를 30분으로 제한.
- **Refresh Token(14일)**: 자주 노출 안 되고 DB(`Member.refreshToken`)에 저장해둠 → 로그아웃 시 DB에서 지우면 즉시 무효화 가능. accessToken은 서버가 강제로 무효화 못 함(stateless라서), refreshToken은 DB에 있어서 통제 가능.
- 시크릿 키는 `.gitignore`된 IntelliJ Run Configuration 환경변수(`JWT_SECRET`)로 분리 — 코드/설정파일에 하드코딩하면 git에 노출됨.

## 6. `SecurityConfig` — stateless + CSRF 비활성화

- **CSRF를 끈 이유**: CSRF는 "브라우저가 쿠키를 자동으로 붙여 보내는" 걸 노린 공격인데, 우리는 세션/쿠키 대신 매 요청마다 코드가 직접 `Authorization` 헤더에 토큰을 실어 보내는 방식이라 이 공격 자체가 성립 안 함. (대신 XSS로 토큰이 탈취될 위험은 있음 — trade-off를 인지하고 선택한 것.)
- **`SessionCreationPolicy.STATELESS`**: 서버가 로그인 상태를 세션으로 안 만듦. JWT 자체가 매 요청 인증 수단이라 세션이 필요 없고, 오히려 서버 자원 낭비.
- `/api/auth/**`만 `permitAll()`, 나머지는 `authenticated()` — 인증 관련 API는 로그인 전에도 접근 가능해야 하니까.

## 7. JWT 인증 필터 (`JwtAuthenticationFilter`) + `SecurityContextHolder`

- 비유: 필터 = 건물 로비 검문소, `SecurityContextHolder` = 검문 통과 후 받는 출입증(명찰).
- 요청이 컨트롤러에 도달하기 전에 필터가 먼저 `Authorization` 헤더의 토큰을 검증하고, 유효하면 "이 요청은 몇 번 회원이 보낸 것"이라는 인증 정보를 `SecurityContextHolder`에 등록.
- 토큰이 없거나 무효면 그냥 통과시키되(인증 정보 없이), 이후 `SecurityConfig`의 `authenticated()` 규칙에 걸려서 `JwtAuthenticationEntryPoint`가 401 응답.
- **401 vs 403 구분**: 스프링 시큐리티 기본값은 인증 안 된 접근도 403을 주는데, 스펙상 "토큰 없음 = 401", "권한 부족 = 403"으로 구분해야 해서 `AuthenticationEntryPoint`를 직접 구현해 401로 오버라이드.

## 8. 디버거로 코드 흐름 직접 관찰

- 코드를 읽고 상상하는 것과, 브레이크포인트+Step Over(F8)로 변수 값이 실제로 바뀌는 걸 보는 건 완전히 다름. 막막할 땐 디버거로 실제 값을 관찰하는 게 제일 확실한 이해 방법.
- 디버깅 절차: 재현 → 에러 메시지 읽기 → 격리(어디까지 정상인지) → 진단(값이 예상과 다른 지점 찾기) → 수정.

## 오늘 완성 vs 미완성

- ✅ **이해하고 완성 + Postman 전체 시나리오 테스트 통과**: 회원가입 → 로그인 → 토큰갱신 → 로그아웃 → (로그아웃 후 refreshToken 재사용 시도 시 401 확인)까지 전부.
  - Repository 패턴 (JPA/MyBatis 분리 대비), DTO, 커스텀 예외 + 전역 핸들러, BCrypt, JWT access/refresh 발급, `JwtAuthenticationFilter` + `SecurityContextHolder` + `AuthenticationEntryPoint`(401/403 구분)
- 이걸로 서브프로젝트 1번(도메인 모델 + 인증) 스펙의 API 4개(`/signup`, `/login`, `/refresh`, `/logout`) 전부 구현 완료.
- **다음 서브프로젝트**: 관리자 기능 + TMDB 연동 (로드맵 2번)
