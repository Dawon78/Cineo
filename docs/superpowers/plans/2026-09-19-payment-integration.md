# 결제 연동 Implementation Plan

> **협업 방식 안내:** 이 프로젝트는 사용자가 직접 코드를 타이핑하며 학습하는 포트폴리오 프로젝트다. 아래 태스크는 Claude가 자동 실행하지 않고, 세션 안에서 한 태스크씩 같이 짚어가며 진행한다 (subagent-driven-development / executing-plans 미사용). 체크박스는 진행 상황 추적용.

**Goal:** 3번 서브프로젝트의 "가짜 결제"를 포트원(구 아임포트) 실제 결제 검증으로 교체하고, 예약 취소 시 실제 환불까지 연결한다.

**Architecture:** `PortOneClient`(신규, `TmdbClient`와 동일한 외부 API 클라이언트 패턴)가 포트원 서버 API 호출을 전담한다. `ReservationService`가 이 클라이언트를 주입받아 `confirm()`에서는 결제 검증 후 예약 생성을, `cancel()`에서는 환불 후 취소를 처리한다. 서버가 클라이언트를 신뢰하지 않고 포트원 서버에 직접 조회해서 검증하는 게 핵심.

**Tech Stack:** Spring Boot, Spring `RestClient`(TmdbClient와 동일), Jackson

**Spec:** [docs/superpowers/specs/2026-09-19-payment-integration-design.md](../specs/2026-09-19-payment-integration-design.md)

## Global Constraints

- 클라이언트가 보낸 결제 금액/상태를 절대 그대로 신뢰하지 않는다 — 반드시 포트원 서버 API로 직접 조회해서 검증
- 별도 `Payment` 엔티티는 만들지 않는다 — `Reservation`에 `impUid`/`paidAmount` 필드만 추가 (스펙에서 확정)
- 환불 = 예약 전체 취소만 지원, 부분 환불 없음
- `test-payment.html`은 `src/` 바깥(`scratch/` 폴더)에 둔다 — 실제 애플리케이션 코드가 아닌 테스트 도구
- 포트원 API는 V1(imp_uid 기반): `/users/getToken`, `/payments/{imp_uid}`, `/payments/cancel`

---

### Task 1: 포트원 가입 + 테스트 키 발급 + 환경변수 설정

**Files:**
- Modify: `src/main/resources/application.yaml`

**Interfaces:**
- Produces: `${portone.api-key}`, `${portone.api-secret}` 프로퍼티 — Task 4(PortOneClient)에서 `@Value`로 주입받아 사용

- [ ] **Step 1: 포트원 가입 + 테스트 연동 키 발급**

[portone.io](https://portone.io) 접속 → 회원가입 → 콘솔에서 "연동 정보" 또는 "테스트 연동" 메뉴에서 **아임포트(구버전, V1) 방식**의 `imp_key`(API 키)와 `imp_secret`(API 시크릿) 발급. 테스트 모드는 사업자 등록 없이 바로 사용 가능.

- [ ] **Step 2: `application.yaml`에 설정 추가**

`tmdb:` 블록 옆에 추가:

```yaml
portone:
  api-key: ${PORTONE_API_KEY}
  api-secret: ${PORTONE_API_SECRET}
```

- [ ] **Step 3: IntelliJ Run Configuration에 환경변수 추가**

Run → Edit Configurations → 메인 앱 실행 설정(그리고 나중에 테스트용 설정에도) → Environment variables에 추가:
- `PORTONE_API_KEY=발급받은 imp_key`
- `PORTONE_API_SECRET=발급받은 imp_secret`

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/application.yaml
git commit -m "chore: 포트원 연동 설정 추가"
```

---

### Task 2: Reservation / Member 엔티티 변경

**Files:**
- Modify: `src/main/java/com/dw/movie/reservation/Reservation.java`
- Modify: `src/main/java/com/dw/movie/auth/Member.java`
- Test: `src/test/java/com/dw/movie/reservation/ReservationTest.java`

**Interfaces:**
- Produces: `Reservation.applyPayment(String impUid, Long paidAmount): void`, `Reservation.getImpUid(): String`, `Reservation.getPaidAmount(): Long`, `Member.addSpent(long amount): void` — Task 6(예약 확정), Task 8(예약 취소)에서 사용

- [ ] **Step 1: 실패하는 테스트 추가**

`ReservationTest.java`에 새 테스트 메서드 추가:

```java
    @Test
    void applyPayment_호출하면_impUid와_결제금액이_저장된다() {
        Reservation reservation = new Reservation(new Member(), new Showtime(), 15000L);

        reservation.applyPayment("imp_123456789", 15000L);

        assertEquals("imp_123456789", reservation.getImpUid());
        assertEquals(15000L, reservation.getPaidAmount());
    }
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `./gradlew test --tests "com.dw.movie.reservation.ReservationTest"`
Expected: FAIL — `applyPayment`, `getImpUid`, `getPaidAmount`가 없어서 컴파일 에러

- [ ] **Step 3: Reservation에 필드/메서드 추가**

`Reservation.java`의 기존 필드 선언부(`private Long totalPrice;` 근처)에 추가:

```java
    private String impUid;
    private Long paidAmount;
```

클래스 안 아무 곳에 메서드 추가 (`applyTotalPrice` 근처가 자연스러움):

```java
    public void applyPayment(String impUid, Long paidAmount) {
        this.impUid = impUid;
        this.paidAmount = paidAmount;
    }
```

`@Getter`가 이미 클래스에 붙어있어서 `getImpUid()`, `getPaidAmount()`는 필드 추가만으로 자동 생성된다.

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `./gradlew test --tests "com.dw.movie.reservation.ReservationTest"`
Expected: PASS

- [ ] **Step 5: Member에 addSpent 메서드 추가**

`Member.java`에 (직접 해봐 — `Reservation.cancel()`이랑 똑같은 패턴, 파라미터 받아서 필드 갱신만 하면 됨):

요구사항: `public void addSpent(long amount)` — 호출하면 `totalSpent` 필드에 `amount`를 더한다.

- [ ] **Step 6: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/dw/movie/reservation/Reservation.java src/main/java/com/dw/movie/auth/Member.java src/test/java/com/dw/movie/reservation/ReservationTest.java
git commit -m "feat: Reservation 결제정보 필드, Member 누적결제금액 갱신 메서드 추가"
```

---

### Task 3: 예외 클래스 3개

**Files:**
- Create: `src/main/java/com/dw/movie/payment/PaymentVerificationFailedException.java`
- Create: `src/main/java/com/dw/movie/payment/RefundFailedException.java`
- Create: `src/main/java/com/dw/movie/payment/PortOneApiException.java`
- Modify: `src/main/java/com/dw/movie/common/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Produces: 3개 예외 클래스 — Task 4(PortOneClient), Task 6(예약 확정), Task 8(예약 취소)에서 사용

새 패키지 `com.dw.movie.payment`를 쓴다는 점만 다르고, 만드는 방식은 지금까지와 똑같으니 직접 해봐:

1. `PaymentVerificationFailedException` — 결제 검증 실패(금액 불일치, 결제상태 이상, imp_uid 없음)용, `GlobalExceptionHandler`에 **400 BAD_REQUEST**로 매핑
2. `RefundFailedException` — 환불 실패용, **500 INTERNAL_SERVER_ERROR**로 매핑
3. `PortOneApiException` — 포트원 API 호출 자체가 실패(네트워크, 인증 등)했을 때용, **500 INTERNAL_SERVER_ERROR**로 매핑

패키지 선언은 `package com.dw.movie.payment;`이고, `GlobalExceptionHandler`에서 이 클래스들을 쓰려면 각각 `import com.dw.movie.payment.PaymentVerificationFailedException;` 같은 import를 추가해야 해 (지금까지는 전부 `common.exception` 패키지 안이라 import가 필요 없었는데, 이번엔 다른 패키지라 필요함).

- [ ] **Step 1: 3개 예외 클래스 작성 (직접)**
- [ ] **Step 2: GlobalExceptionHandler에 매핑 3개 추가 (직접, import 포함)**
- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/dw/movie/payment/ src/main/java/com/dw/movie/common/exception/GlobalExceptionHandler.java
git commit -m "feat: 결제 관련 예외 클래스 추가"
```

---

### Task 4: PortOneClient — 결제 조회

**Files:**
- Create: `src/main/java/com/dw/movie/payment/PortOneClient.java`

**Interfaces:**
- Consumes: `PaymentVerificationFailedException`, `PortOneApiException` (Task 3)
- Produces: `PortOneClient.getPaymentInfo(String impUid): PortOneClient.PaymentInfo` — Task 6에서 사용. `PaymentInfo(String impUid, Long amount, String status)` record.

새로운 외부 API 연동이라 전체 코드를 줄게. `TmdbClient.java`(`src/main/java/com/dw/movie/movie/TmdbClient.java`)를 옆에 띄워두고 비교해보면 구조가 거의 똑같다는 걸 알 수 있을 거야.

- [ ] **Step 1: PortOneClient 작성**

```java
package com.dw.movie.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PortOneClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String apiSecret;

    public PortOneClient(@Value("${portone.api-key}") String apiKey, @Value("${portone.api-secret}") String apiSecret) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.restClient = RestClient.create("https://api.iamport.kr");
    }

    public PaymentInfo getPaymentInfo(String impUid) {
        String accessToken = getAccessToken();

        PaymentApiResponse response;
        try {
            response = restClient.get()
                    .uri("/payments/{impUid}", impUid)
                    .header("Authorization", accessToken)
                    .retrieve()
                    .body(PaymentApiResponse.class);
        } catch (RestClientException e) {
            throw new PortOneApiException("포트원 결제 조회에 실패했습니다.");
        }

        if (response == null || response.code() != 0 || response.response() == null) {
            throw new PaymentVerificationFailedException("존재하지 않는 결제 정보입니다: " + impUid);
        }

        return response.response();
    }

    private String getAccessToken() {
        TokenRequest request = new TokenRequest(apiKey, apiSecret);

        TokenApiResponse response;
        try {
            response = restClient.post()
                    .uri("/users/getToken")
                    .body(request)
                    .retrieve()
                    .body(TokenApiResponse.class);
        } catch (RestClientException e) {
            throw new PortOneApiException("포트원 인증 토큰 발급에 실패했습니다.");
        }

        if (response == null || response.code() != 0 || response.response() == null) {
            throw new PortOneApiException("포트원 인증 토큰 발급에 실패했습니다.");
        }

        return response.response().accessToken();
    }

    private record TokenRequest(
            @JsonProperty("imp_key") String impKey,
            @JsonProperty("imp_secret") String impSecret
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenApiResponse(Integer code, String message, TokenResponse response) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PaymentApiResponse(Integer code, String message, PaymentInfo response) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentInfo(
            @JsonProperty("imp_uid") String impUid,
            Long amount,
            String status
    ) {}
}
```

**코드 설명:**
- `getAccessToken()`이 매번 호출될 때마다 새로 토큰을 발급받아 — 포트원 토큰엔 유효기간이 있어서 원래는 캐싱해서 재사용하는 게 효율적인데, 지금은 트래픽이 적은 학습 프로젝트라 매번 새로 발급받는 걸로 단순하게 감 (스펙 문서에 이 결정 이유 적어뒀어).
- `Authorization` 헤더에 `"Bearer " + accessToken`이 아니라 **토큰 값 그대로**만 넣는 게 포인트야 — 포트원 V1 API의 특이한 규칙인데, JWT 인증(`Bearer` 붙이는 방식)이랑 헷갈리기 쉬운 부분이라 짚어줄게.
- `TokenApiResponse`, `PaymentApiResponse`처럼 응답을 감싸는 record가 중첩된 이유는, 포트원 API가 항상 `{ "code": 0, "message": null, "response": {...} }` 형태로 감싸서 응답하기 때문이야 — `code`가 0이면 성공, 아니면 실패라는 규칙.
- `record`는 자바의 불변 데이터 클래스 문법이야 — 필드 선언만 하면 생성자/getter가 자동으로 생기는, `@AllArgsConstructor @Getter`를 합쳐놓은 것 같은 문법이라고 생각하면 돼. `TmdbClient`에서도 이미 썼던 패턴.

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/dw/movie/payment/PortOneClient.java
git commit -m "feat: 포트원 결제 조회 클라이언트(PortOneClient) 추가"
```

---

### Task 5: 가격 미리보기 API

**Files:**
- Create: `src/main/java/com/dw/movie/reservation/dto/SeatPriceResponse.java`
- Modify: `src/main/java/com/dw/movie/reservation/SeatQueryService.java`
- Modify: `src/main/java/com/dw/movie/reservation/SeatController.java`

**Interfaces:**
- Produces: `GET /api/showtimes/{showtimeId}/seats/price?seatIds=1,2` → `SeatPriceResponse { Long totalPrice }`

반복 패턴(Service 메서드 + Controller 엔드포인트 + 간단한 DTO)이니 직접 해봐:

1. **`SeatPriceResponse`** DTO (`reservation/dto`) — 필드 `totalPrice(Long)` 하나, `@Getter @AllArgsConstructor`
2. **`SeatQueryService`에 메서드 추가**: `calculateTotalPrice(Long showtimeId, List<Long> seatIds): Long`
   - `showtimeRepository.findById(showtimeId)`로 Showtime 조회 (없으면 `ShowtimeNotFoundException`) — 이미 `SeatQueryService`에 `showtimeRepository`가 주입돼있으니 그대로 씀
   - `seatRepository.findAllById(seatIds)`로 좌석들 조회 — `seatRepository`도 이미 주입돼있음
   - 각 좌석마다 `SeatPriceCalculator.calculate(showtime.getBasePrice(), seat.getGrade(), showtime.getFormat())`로 가격 계산해서 합산 (Task 9의 `ReservationService.confirm()`에 있는 반복문이랑 똑같은 로직)
   - 합산한 `long`을 `Long`으로 반환
3. **`SeatController`에 엔드포인트 추가**:
```java
@GetMapping("/{showtimeId}/seats/price")
```
   - `@PathVariable Long showtimeId`, `@RequestParam List<Long> seatIds` 받기 (`?seatIds=1,2` 형태의 쿼리 파라미터는 Spring이 자동으로 `List<Long>`으로 변환해줌, 콤마로 구분된 값을 알아서 쪼개줌)
   - `seatQueryService.calculateTotalPrice(showtimeId, seatIds)` 호출해서 `new SeatPriceResponse(totalPrice)`로 감싸 `ResponseEntity.ok(...)`로 응답

만들어보고 컴파일 확인하자.

- [ ] **Step 1: 작성 (직접)**
- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Postman으로 테스트**

앱 재시작(새 엔드포인트) → `GET /api/showtimes/1/seats/price?seatIds=3,4` (헤더에 토큰) → 200 OK, `totalPrice`가 좌석 등급/포맷에 맞게 계산돼서 나오는지 확인

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/dw/movie/reservation/dto/SeatPriceResponse.java src/main/java/com/dw/movie/reservation/SeatQueryService.java src/main/java/com/dw/movie/reservation/SeatController.java
git commit -m "feat: 좌석 가격 미리보기 API 추가"
```

---

### Task 6: 예약 확정 API — 결제 검증 연동

**Files:**
- Modify: `src/main/java/com/dw/movie/reservation/dto/ReservationConfirmRequest.java`
- Modify: `src/main/java/com/dw/movie/reservation/ReservationService.java`
- Modify: `src/main/java/com/dw/movie/reservation/ReservationController.java`

**Interfaces:**
- Consumes: `PortOneClient.getPaymentInfo(String impUid): PortOneClient.PaymentInfo` (Task 4), `Reservation.applyPayment(String, Long)`, `Member.addSpent(long)` (Task 2)
- Produces: `ReservationService.confirm(Long memberId, Long showtimeId, List<Long> seatIds, String impUid): Reservation` (시그니처 변경 — impUid 파라미터 추가)

지금까지 가짜 결제였던 부분을 실제 검증으로 바꾸는, 이번 서브프로젝트의 핵심 태스크야. 핵심 로직은 코드로 줄게.

- [ ] **Step 1: ReservationConfirmRequest에 필드 추가 (직접)**

`impUid(String)` 필드 하나 추가.

- [ ] **Step 2: ReservationService.confirm() 수정 (제공 코드)**

`ReservationService.java` 상단 import에 추가:
```java
import com.dw.movie.payment.PaymentVerificationFailedException;
import com.dw.movie.payment.PortOneClient;
```

생성자에 `PortOneClient portOneClient` 파라미터 추가하고 필드/할당도 추가:
```java
    private final PortOneClient portOneClient;

    public ReservationService(ReservationRepository reservationRepository, ShowtimeRepository showtimeRepository,
                               SeatRepository seatRepository, MemberRepository memberRepository, SeatHoldService seatHoldService,
                               PortOneClient portOneClient) {
        this.reservationRepository = reservationRepository;
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
        this.memberRepository = memberRepository;
        this.seatHoldService = seatHoldService;
        this.portOneClient = portOneClient;
    }
```

`confirm()` 메서드 전체를 이걸로 교체:

```java
    @Transactional
    public Reservation confirm(Long memberId, Long showtimeId, List<Long> seatIds, String impUid) {
        seatHoldService.validateHolder(showtimeId, seatIds, memberId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException("해당 회원을 찾을 수 없습니다."));
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ShowtimeNotFoundException("해당 상영시간표를 찾을 수 없습니다."));
        List<Seat> seats = seatRepository.findAllById(seatIds);

        long totalPrice = 0L;
        for (Seat seat : seats) {
            totalPrice += SeatPriceCalculator.calculate(showtime.getBasePrice(), seat.getGrade(), showtime.getFormat());
        }

        PortOneClient.PaymentInfo paymentInfo = portOneClient.getPaymentInfo(impUid);
        if (!"paid".equals(paymentInfo.status())) {
            throw new PaymentVerificationFailedException("결제가 완료되지 않았습니다: " + impUid);
        }
        if (!Long.valueOf(totalPrice).equals(paymentInfo.amount())) {
            throw new PaymentVerificationFailedException("결제 금액이 일치하지 않습니다. 예상: " + totalPrice + ", 실제: " + paymentInfo.amount());
        }

        Reservation reservation = new Reservation(member, showtime, 0L);
        for (Seat seat : seats) {
            long price = SeatPriceCalculator.calculate(showtime.getBasePrice(), seat.getGrade(), showtime.getFormat());
            reservation.addSeat(seat, price);
        }
        reservation.applyTotalPrice(totalPrice);
        reservation.applyPayment(impUid, paymentInfo.amount());

        Reservation saved = reservationRepository.save(reservation);
        seatHoldService.release(showtimeId, seatIds, memberId);
        member.addSpent(totalPrice);

        return saved;
    }
```

**로직 설명 (달라진 부분만):**
- 좌석 가격 합계(`totalPrice`)를 **먼저** 계산해놔.
- `portOneClient.getPaymentInfo(impUid)`로 포트원 서버에 **직접 물어봐서** 진짜 결제 정보를 가져와.
- 결제 상태가 `"paid"`가 아니면(취소/실패된 결제) 예외.
- 우리가 계산한 `totalPrice`랑 포트원이 알려준 실제 결제금액(`paymentInfo.amount()`)이 다르면 예외 — **여기가 보안의 핵심**이야, 클라이언트가 뭐라고 주장하든 상관없이 우리 계산과 포트원의 실제 기록이 일치해야만 통과돼.
- 검증 통과하면 그제서야 `Reservation`을 만들고, `reservation.applyPayment(...)`로 결제 정보 저장.
- `member.addSpent(totalPrice)` — `@Transactional` 메서드 안에서 DB로 조회해온 `member` 객체(영속 상태)의 필드를 바꾸면, 메서드 끝나고 트랜잭션 커밋될 때 **JPA가 자동으로 변경을 감지해서 UPDATE 쿼리를 날려줘** (이걸 "더티 체킹"이라고 불러) — 그래서 `memberRepository.save(member)`를 따로 안 불러도 DB에 반영돼.

- [ ] **Step 3: ReservationController.confirm() 수정 (직접)**

`request.getImpUid()`를 꺼내서 `reservationService.confirm(memberId, request.getShowtimeId(), request.getSeatIds(), request.getImpUid())`로 넘기도록 호출부만 수정.

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/dw/movie/reservation/dto/ReservationConfirmRequest.java src/main/java/com/dw/movie/reservation/ReservationService.java src/main/java/com/dw/movie/reservation/ReservationController.java
git commit -m "feat: 예약 확정 시 포트원 결제 검증 연동"
```

(Postman 테스트는 Task 9에서 `test-payment.html`로 실제 imp_uid를 받은 뒤 한꺼번에 진행)

---

### Task 7: PortOneClient — 환불

**Files:**
- Modify: `src/main/java/com/dw/movie/payment/PortOneClient.java`

**Interfaces:**
- Consumes: `RefundFailedException` (Task 3)
- Produces: `PortOneClient.cancelPayment(String impUid, String reason): void` — Task 8에서 사용

이번엔 반복 패턴(같은 클라이언트에 메서드 하나 추가, `getPaymentInfo`랑 구조 거의 동일)이니 직접 해봐:

`PortOneClient`에 `public void cancelPayment(String impUid, String reason)` 메서드 추가:
- `getAccessToken()` 호출해서 토큰 받기 (이미 있는 private 메서드 재사용)
- `restClient.post().uri("/payments/cancel")`로 POST 요청, `Authorization` 헤더에 토큰(Bearer 없이), body는 `{"imp_uid": impUid, "reason": reason}` — 이걸 위한 새 record `CancelRequest(@JsonProperty("imp_uid") String impUid, String reason)`를 만들어서 `.body(new CancelRequest(impUid, reason))`로 넘기면 됨
- 응답은 `PaymentApiResponse`(이미 있는 record 재사용 가능 — 응답 구조가 결제조회랑 똑같이 `{code, message, response}` 형태라서)
- `RestClientException` 잡히면 `RefundFailedException` 던지기
- 응답의 `code`가 0이 아니면 `RefundFailedException` 던지기

- [ ] **Step 1: 작성 (직접)**
- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/dw/movie/payment/PortOneClient.java
git commit -m "feat: 포트원 환불 API 연동"
```

---

### Task 8: 예약 취소 API — 환불 연동

**Files:**
- Modify: `src/main/java/com/dw/movie/reservation/ReservationService.java`

**Interfaces:**
- Consumes: `PortOneClient.cancelPayment(String, String)` (Task 7)

`ReservationService.cancel()` 메서드를 이걸로 교체 (직접 해봐, 아래 요구사항대로):

- 기존 로직(예약 조회, 본인 소유 확인, 이미 취소됐는지 확인)은 그대로 둔다
- `reservation.cancel()` 호출 **전에** `portOneClient.cancelPayment(reservation.getImpUid(), "고객 예약 취소")` 호출 추가
- 환불이 실패하면(`RefundFailedException`이 던져지면) 그대로 전파되게 두고, `reservation.cancel()`은 실행되지 않아야 함 (즉 환불 호출을 `reservation.cancel()`보다 먼저 배치)

- [ ] **Step 1: 작성 (직접)**
- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/dw/movie/reservation/ReservationService.java
git commit -m "feat: 예약 취소 시 포트원 환불 연동"
```

---

### Task 9: 결제 테스트 페이지 + 전체 플로우 테스트

**Files:**
- Create: `scratch/test-payment.html`

**Interfaces:** 없음 (테스트 도구)

포트원 결제창을 띄우는 스크래치 HTML. 포트원 공식 JS SDK(`iamport.payment.js`)를 CDN으로 불러와서 테스트 결제를 진행한다.

- [ ] **Step 1: 스크래치 폴더 생성 + HTML 작성**

`scratch/test-payment.html` (프로젝트 루트에 `scratch/` 폴더 새로 만들고 그 안에):

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Cineo 결제 테스트</title>
    <script src="https://cdn.iamport.kr/v1/iamport.js"></script>
</head>
<body>
    <h1>Cineo 결제 테스트</h1>
    <p>상영시간표ID, 좌석ID, 결제금액을 입력하고 결제 버튼을 누르세요.</p>
    <div>
        <label>가맹점 식별코드(imp_xxxxxxxx): <input type="text" id="impCode" value="여기에_포트원_가맹점코드_입력"></label>
    </div>
    <div>
        <label>결제 금액: <input type="number" id="amount" value="20000"></label>
    </div>
    <button onclick="requestPay()">결제하기</button>
    <pre id="result"></pre>

    <script>
        function requestPay() {
            const IMP = window.IMP;
            IMP.init(document.getElementById('impCode').value);

            IMP.request_pay({
                pg: 'html5_inicis.INIpayTest',
                pay_method: 'card',
                merchant_uid: 'order_' + new Date().getTime(),
                name: 'Cineo 영화 예매',
                amount: Number(document.getElementById('amount').value),
                buyer_email: 'test@test.com',
                buyer_name: '테스트',
                buyer_tel: '01000000000'
            }, function (response) {
                document.getElementById('result').textContent = JSON.stringify(response, null, 2);
            });
        }
    </script>
</body>
</html>
```

`impCode`엔 Task 1에서 발급받은 가맹점 식별코드(포트원 콘솔에서 확인 가능, `imp`로 시작하는 코드)를 넣는다. `pg: 'html5_inicis.INIpayTest'`는 이니시스 테스트 PG 설정값 — 포트원 콘솔에서 테스트용으로 안내하는 PG사 값을 그대로 쓰면 된다(콘솔 화면에 정확한 값이 안내돼 있을 것).

- [ ] **Step 2: 브라우저에서 열어서 테스트 결제**

`scratch/test-payment.html` 파일을 더블클릭하거나 브라우저로 직접 열기 → 가맹점코드/금액 입력 → 결제하기 → 테스트 카드 정보(포트원 콘솔에 안내된 테스트 카드번호)로 결제 → 결과 JSON에서 `imp_uid` 값 확인 및 복사

- [ ] **Step 3: 전체 플로우 Postman 테스트**

1. 좌석 선점: `POST /api/showtimes/1/seats/hold` body `{"seatIds": [5, 6]}`
2. 가격 미리보기: `GET /api/showtimes/1/seats/price?seatIds=5,6` → 나온 `totalPrice`를 test-payment.html의 결제 금액에 똑같이 입력해서 결제 진행
3. 결제 완료 후 받은 `imp_uid`로 예약 확정: `POST /api/reservations` body `{"showtimeId": 1, "seatIds": [5, 6], "impUid": "받은값"}` → **201 Created** 확인
4. `GET /api/reservations/me`로 확인, `Member.totalSpent`가 증가했는지는 DB에서 직접 확인 (`SELECT total_spent FROM member WHERE id = ...`)
5. 예약 취소: `DELETE /api/reservations/{id}` → **204 No Content**, 포트원 콘솔에서 해당 결제가 취소 처리됐는지 확인
6. **금액 불일치 테스트**: 다시 좌석 선점 → 다른 금액으로 결제(예: 실제론 20000원짜리 좌석인데 10000원만 결제) → 예약 확정 시도 → **400 Bad Request** 확인

- [ ] **Step 4: `.gitignore`에 scratch 폴더 제외 여부 결정**

`scratch/`를 git에 커밋할지 말지 정하기 — 이건 실제 프로젝트 코드가 아니라 개인 테스트 도구라 `.gitignore`에 추가해서 커밋 대상에서 빼는 것도 방법이고, 나중에 다시 테스트할 때 편하게 남겨두고 싶으면 커밋해도 무방. 결정해서 진행.

- [ ] **Step 5: Commit**

```bash
git add scratch/ .gitignore
git commit -m "test: 결제 테스트용 스크래치 페이지 추가"
```

---

## 완료 후

전체 태스크가 끝나면 서브프로젝트 4번이 완료된다. 다음은 5번(적립금 + 등급 시스템) 서브프로젝트 브레인스토밍으로 넘어가면 된다.
