Cineo
영화관 좌석 예약 시스템이다. 단일 지점에 여러 상영관을 운영하는 멀티플렉스를 가정하고,
회원가입부터 영화 예매, 결제까지 이어지는 흐름을 백엔드부터 프론트엔드,
배포까지 직접 구현했다. 스프링 부트 포트폴리오 프로젝트로 시작했고,
JWT 인증, TMDB 연동, Redis를 이용한 좌석 동시성 제어 같은 실제 서비스에서 마주치는 문제들을 하나씩 풀어가는 걸 목표로 잡았다.

기술 스택
Backend: Spring Boot, Spring Security, Spring Data JPA, MyBatis
DB: PostgreSQL
인증: JWT (access/refresh token)
외부 API: TMDB (영화 정보 자동 조회)

JPA와 MyBatis를 같이 쓴다. 단순 CRUD는 JPA, 나중에 나올 복잡한 동적 조회(좌석 현황 검색 등)는 MyBatis로 처리할 예정.

