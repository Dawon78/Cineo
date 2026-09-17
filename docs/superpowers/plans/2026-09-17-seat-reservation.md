# 좌석 예약 + Redis 동시성 제어 Implementation Plan

> **협업 방식 안내:** 이 프로젝트는 사용자가 직접 코드를 타이핑하며 학습하는 포트폴리오 프로젝트다. 아래 태스크는 Claude가 자동 실행하지 않고, 세션 안에서 한 태스크씩 같이 짚어가며 진행한다 (subagent-driven-development / executing-plans 미사용). 체크박스는 진행 상황 추적용.

**Goal:** 좌석 선점(Redis Hold) → 예약 확정까지의 플로우를 구현하고, 동시에 같은 좌석을 노리는 요청 중 하나만 성공하도록 Redis `SETNX` 기반 동시성 제어를 적용한다.

**Architecture:** 좌석 선점 상태는 DB가 아닌 Redis에 TTL 5분짜리 키로만 존재한다(`seat-hold:{showtimeId}:{seatId}`). 예약 확정 시에만 `Reservation`/`ReservationSeat`를 DB에 CONFIRMED로 생성하고 Redis 키를 정리한다. 결제는 실제 PG 없이 확정 시 바로 CONFIRMED 처리(가짜 결제, 4번 서브프로젝트에서 교체 예정).

**Tech Stack:** Spring Boot, Spring Data JPA, Spring Data Redis(`StringRedisTemplate`), PostgreSQL, JUnit 5

**Spec:** [docs/superpowers/specs/2026-09-17-seat-reservation-design.md](../specs/2026-09-17-seat-reservation-design.md)

## Global Constraints

- Redis hold TTL: 300초(5분) — 스펙에서 확정
- 여러 좌석 선점은 all-or-nothing (하나라도 실패하면 전체 롤백) — 스펙에서 확정
- 예약 확정은 가짜 결제(PG 연동 없음), 확정 즉시 `Reservation.status = CONFIRMED`
- 예약 취소는 환불 로직 없이 상태만 `CANCELLED`로 변경
- 패키지: 전부 `com.dw.movie.reservation` 아래 (기존 `screen`/`showtime`/`auth` 패키지와 동일한 레벨)
- `Reservation`/`ReservationSeat`는 `JpaRepository` 직접 상속 (Repository/RepositoryImpl 3계층 분리 안 함, YAGNI)
- 컨트롤러의 로그인 사용자 식별은 기존 `MemberController.logout`과 동일하게 `@AuthenticationPrincipal Long memberId` 사용
- 신규 엔드포인트는 `SecurityConfig`의 `.anyRequest().authenticated()`에 이미 포함되므로 별도 보안 설정 불필요

---

### Task 1: Redis 의존성 + 로컬 Redis 연결 설정

**Files:**
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/application.yaml`

**Interfaces:**
- Produces: `StringRedisTemplate` 빈 (Spring Boot가 `spring-boot-starter-data-redis` + connection factory만 있으면 자동 생성 — 별도 `@Configuration` 불필요)

- [ ] **Step 1: `build.gradle.kts`에 Redis 의존성 추가**

`dependencies { ... }` 블록 안, 기존 `implementation("org.springframework.boot:spring-boot-starter-data-jpa")` 아래에 추가:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-redis")
```

- [ ] **Step 2: `application.yaml`에 Redis 연결 설정 추가**

`spring:` 블록 안, `datasource:` 옆에 추가 (들여쓰기 주의 — `datasource`와 같은 레벨):

```yaml
  data:
    redis:
      host: localhost
      port: 6379
```

- [ ] **Step 3: 로컬 Redis를 Docker로 실행**

Redis가 로컬에 떠 있지 않으면 아래 명령으로 실행 (이미 떠 있으면 스킵):

```bash
docker run -d -p 6379:6379 --name cineo-redis redis
```

이미 컨테이너가 있는데 꺼져있다면: `docker start cineo-redis`

- [ ] **Step 4: 애플리케이션 실행해서 Redis 연결 확인**

IntelliJ에서 앱을 실행(`bootRun` 또는 Run 버튼)하고 콘솔 로그에 Redis 관련 연결 에러(`Unable to connect to Redis` 등)가 없는지 확인한다. 에러가 있으면 Docker 컨테이너가 실제로 떠 있는지(`docker ps`) 먼저 확인한다.

Expected: 앱이 정상적으로 8082 포트에서 시작됨, Redis 에러 없음

- [ ] **Step 5: Commit**

```bash
git add build.gradle.kts src/main/resources/application.yaml
git commit -m "chore: Redis 의존성 및 로컬 연결 설정 추가"
```

---

### Task 2: SeatPriceCalculator (좌석 가격 계산 유틸)

**Files:**
- Create: `src/main/java/com/dw/movie/reservation/SeatPriceCalculator.java`
- Test: `src/test/java/com/dw/movie/reservation/SeatPriceCalculatorTest.java`

**Interfaces:**
- Produces: `SeatPriceCalculator.calculate(Long basePrice, SeatGrade grade, ShowtimeFormat format): Long` — 이후 Task 9(예약 확정)에서 좌석별 가격 스냅샷 계산에 사용

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.dw.movie.reservation;

import com.dw.movie.screen.SeatGrade;
import com.dw.movie.showtime.ShowtimeFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeatPriceCalculatorTest {

    @Test
    void STANDARD_좌석_TWO_D는_기본가격_그대로() {
        Long price = SeatPriceCalculator.calculate(10000L, SeatGrade.STANDARD, ShowtimeFormat.TWO_D);
        assertEquals(10000L, price);
    }

    @Test
    void PREMIUM_좌석_IMAX는_추가요금_합산() {
        Long price = SeatPriceCalculator.calculate(10000L, SeatGrade.PREMIUM, ShowtimeFormat.IMAX);
        assertEquals(19000L, price); // 10000 + 3000(PREMIUM) + 6000(IMAX)
    }

    @Test
    void COUPLE_좌석_FOUR_DX는_추가요금_합산() {
        Long price = SeatPriceCalculator.calculate(10000L, SeatGrade.COUPLE, ShowtimeFormat.FOUR_DX);
        assertEquals(23000L, price); // 10000 + 5000(COUPLE) + 8000(FOUR_DX)
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `./gradlew test --tests "com.dw.movie.reservation.SeatPriceCalculatorTest"`
Expected: FAIL — `SeatPriceCalculator` 클래스가 없어서 컴파일 에러

- [ ] **Step 3: 최소 구현 작성**

```java
package com.dw.movie.reservation;

import com.dw.movie.screen.SeatGrade;
import com.dw.movie.showtime.ShowtimeFormat;

public class SeatPriceCalculator {

    public static Long calculate(Long basePrice, SeatGrade grade, ShowtimeFormat format) {
        return basePrice + gradeSurcharge(grade) + formatSurcharge(format);
    }

    private static long gradeSurcharge(SeatGrade grade) {
        return switch (grade) {
            case PREMIUM -> 3000L;
            case COUPLE -> 5000L;
            case STANDARD -> 0L;
        };
    }

    private static long formatSurcharge(ShowtimeFormat format) {
        return switch (format) {
            case THREE_D -> 2000L;
            case IMAX -> 6000L;
            case FOUR_DX -> 8000L;
            case TWO_D -> 0L;
        };
    }
}
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `./gradlew test --tests "com.dw.movie.reservation.SeatPriceCalculatorTest"`
Expected: PASS (3개 테스트 모두 통과)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/dw/movie/reservation/SeatPriceCalculator.java src/test/java/com/dw/movie/reservation/SeatPriceCalculatorTest.java
git commit -m "feat: 좌석 가격 계산 유틸(SeatPriceCalculator) 추가"
```

---

### Task 3: Reservation / ReservationSeat 엔티티 + Enum

**Files:**
- Create: `src/main/java/com/dw/movie/reservation/ReservationStatus.java`
- Create: `src/main/java/com/dw/movie/reservation/SeatStatus.java`
- Create: `src/main/java/com/dw/movie/reservation/Reservation.java`
- Create: `src/main/java/com/dw/movie/reservation/ReservationSeat.java`
- Test: `src/test/java/com/dw/movie/reservation/ReservationTest.java`

**Interfaces:**
- Consumes: `Member`(`com.dw.movie.auth.Member`), `Showtime`(`com.dw.movie.showtime.Showtime`), `Seat`(`com.dw.movie.screen.Seat`) — 이미 존재하는 엔티티
- Produces: `Reservation(Member member, Showtime showtime, Long totalPrice)` 생성자, `Reservation.cancel()`, `ReservationSeat(Reservation reservation, Seat seat, Long price)` 생성자 — Task 9~11에서 사용

- [ ] **Step 1: Enum 2개 작성**

```java
package com.dw.movie.reservation;

public enum ReservationStatus {
    CONFIRMED, CANCELLED
}
```

```java
package com.dw.movie.reservation;

public enum SeatStatus {
    AVAILABLE, HELD, CONFIRMED
}
```

- [ ] **Step 2: 실패하는 생성자 테스트 작성**

```java
package com.dw.movie.reservation;

import com.dw.movie.auth.Member;
import com.dw.movie.screen.Screen;
import com.dw.movie.screen.Seat;
import com.dw.movie.screen.SeatGrade;
import com.dw.movie.showtime.Showtime;
import com.dw.movie.showtime.ShowtimeFormat;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReservationTest {

    @Test
    void Reservation은_생성되면_CONFIRMED_상태다() {
        Reservation reservation = new Reservation(new Member(), new Showtime(), 15000L);

        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        assertEquals(15000L, reservation.getTotalPrice());
        assertNotNull(reservation.getCreatedAt());
    }

    @Test
    void cancel_호출하면_CANCELLED로_바뀐다() {
        Reservation reservation = new Reservation(new Member(), new Showtime(), 15000L);

        reservation.cancel();

        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertNotNull(reservation.getCanceledAt());
    }

    @Test
    void ReservationSeat은_가격_스냅샷을_들고있다() {
        Reservation reservation = new Reservation(new Member(), new Showtime(), 15000L);
        Screen screen = new Screen("1관");
        Seat seat = new Seat(screen, "A", 1, SeatGrade.PREMIUM);

        ReservationSeat reservationSeat = new ReservationSeat(reservation, seat, 13000L);

        assertEquals(13000L, reservationSeat.getPrice());
        assertEquals(seat, reservationSeat.getSeat());
    }
}
```

주의: `Member`와 `Showtime`에 `@NoArgsConstructor`가 이미 있어서 `new Member()`, `new Showtime()`처럼 빈 생성자로 만들 수 있다 (테스트용).

- [ ] **Step 3: 테스트 실행해서 실패 확인**

Run: `./gradlew test --tests "com.dw.movie.reservation.ReservationTest"`
Expected: FAIL — `Reservation`, `ReservationSeat` 클래스가 없어서 컴파일 에러

- [ ] **Step 4: Reservation 엔티티 작성**

```java
package com.dw.movie.reservation;

import com.dw.movie.auth.Member;
import com.dw.movie.showtime.Showtime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Member member;

    @ManyToOne
    private Showtime showtime;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    private Long totalPrice;
    private LocalDateTime createdAt;
    private LocalDateTime canceledAt;

    public Reservation(Member member, Showtime showtime, Long totalPrice) {
        this.member = member;
        this.showtime = showtime;
        this.totalPrice = totalPrice;
        this.status = ReservationStatus.CONFIRMED;
        this.createdAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
        this.canceledAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 5: ReservationSeat 엔티티 작성**

```java
package com.dw.movie.reservation;

import com.dw.movie.screen.Seat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
public class ReservationSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Reservation reservation;

    @ManyToOne
    private Seat seat;

    private Long price;

    public ReservationSeat(Reservation reservation, Seat seat, Long price) {
        this.reservation = reservation;
        this.seat = seat;
        this.price = price;
    }
}
```

- [ ] **Step 6: 테스트 실행해서 통과 확인**

Run: `./gradlew test --tests "com.dw.movie.reservation.ReservationTest"`
Expected: PASS (3개 테스트 모두 통과)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/dw/movie/reservation/ReservationStatus.java src/main/java/com/dw/movie/reservation/SeatStatus.java src/main/java/com/dw/movie/reservation/Reservation.java src/main/java/com/dw/movie/reservation/ReservationSeat.java src/test/java/com/dw/movie/reservation/ReservationTest.java
git commit -m "feat: Reservation/ReservationSeat 엔티티 추가"
```

---

### Task 4: Repository 2개 생성

**Files:**
- Create: `src/main/java/com/dw/movie/reservation/ReservationRepository.java`
- Create: `src/main/java/com/dw/movie/reservation/ReservationSeatRepository.java`

**Interfaces:**
- Produces: `ReservationRepository.findByMemberId(Long memberId): List<Reservation>` (Task 10에서 사용), `ReservationSeatRepository.findByReservation_Showtime_IdAndReservation_Status(Long showtimeId, ReservationStatus status): List<ReservationSeat>` (Task 6에서 사용)

- [ ] **Step 1: ReservationRepository 작성**

```java
package com.dw.movie.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByMemberId(Long memberId);
}
```

- [ ] **Step 2: ReservationSeatRepository 작성**

```java
package com.dw.movie.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {
    List<ReservationSeat> findByReservation_Showtime_IdAndReservation_Status(Long showtimeId, ReservationStatus status);
}
```

이건 Spring Data JPA의 메서드 이름 기반 쿼리 생성 기능이다 — `Reservation_Showtime_Id`는 `ReservationSeat.reservation.showtime.id`를, `Reservation_Status`는 `ReservationSeat.reservation.status`를 따라가서 조건을 만든다. 따로 SQL을 안 짜도 메서드 이름만 맞으면 Spring이 알아서 쿼리를 만들어준다.

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/dw/movie/reservation/ReservationRepository.java src/main/java/com/dw/movie/reservation/ReservationSeatRepository.java
git commit -m "feat: Reservation/ReservationSeat Repository 추가"
```

---

### Task 5: SeatHoldService (Redis 선점/취소/검증)

**Files:**
- Create: `src/main/java/com/dw/movie/reservation/SeatHoldService.java`
- Create: `src/main/java/com/dw/movie/common/exception/SeatAlreadyHeldException.java`
- Create: `src/main/java/com/dw/movie/common/exception/NotSeatHolderException.java`
- Create: `src/main/java/com/dw/movie/common/exception/SeatHoldExpiredException.java`
- Modify: `src/main/java/com/dw/movie/common/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: `StringRedisTemplate` (Task 1에서 자동 설정된 빈)
- Produces: `SeatHoldService.hold(Long showtimeId, List<Long> seatIds, Long memberId): void`, `.release(Long showtimeId, List<Long> seatIds, Long memberId): void`, `.validateHolder(Long showtimeId, List<Long> seatIds, Long memberId): void`, `.isHeld(Long showtimeId, Long seatId): boolean` — Task 6~9에서 사용

이번 태스크는 이 서브프로젝트의 핵심(Redis 동시성 제어)이라 전체 코드를 직접 줄게. Postman으로는 Task 7(선점 API)까지 만들어야 테스트할 수 있으니, 이번 태스크는 컴파일 확인까지만 한다.

- [ ] **Step 1: 예외 클래스 3개 작성**

```java
package com.dw.movie.common.exception;

public class SeatAlreadyHeldException extends RuntimeException {
    public SeatAlreadyHeldException(String message) {
        super(message);
    }
}
```

```java
package com.dw.movie.common.exception;

public class NotSeatHolderException extends RuntimeException {
    public NotSeatHolderException(String message) {
        super(message);
    }
}
```

```java
package com.dw.movie.common.exception;

public class SeatHoldExpiredException extends RuntimeException {
    public SeatHoldExpiredException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: GlobalExceptionHandler에 매핑 추가**

`src/main/java/com/dw/movie/common/exception/GlobalExceptionHandler.java`의 마지막 `@ExceptionHandler` 뒤에 추가:

```java
    @ExceptionHandler(SeatAlreadyHeldException.class)
    public ResponseEntity<String> handleSeatAlreadyHeld(SeatAlreadyHeldException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
    @ExceptionHandler(NotSeatHolderException.class)
    public ResponseEntity<String> handleNotSeatHolder(NotSeatHolderException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
    @ExceptionHandler(SeatHoldExpiredException.class)
    public ResponseEntity<String> handleSeatHoldExpired(SeatHoldExpiredException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
```

- [ ] **Step 3: SeatHoldService 작성**

```java
package com.dw.movie.reservation;

import com.dw.movie.common.exception.NotSeatHolderException;
import com.dw.movie.common.exception.SeatAlreadyHeldException;
import com.dw.movie.common.exception.SeatHoldExpiredException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class SeatHoldService {

    private static final Duration HOLD_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public SeatHoldService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isHeld(Long showtimeId, Long seatId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(showtimeId, seatId)));
    }

    public void hold(Long showtimeId, List<Long> seatIds, Long memberId) {
        List<String> succeededKeys = new ArrayList<>();
        for (Long seatId : seatIds) {
            String key = key(showtimeId, seatId);
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(key, String.valueOf(memberId), HOLD_TTL);
            if (Boolean.TRUE.equals(success)) {
                succeededKeys.add(key);
            } else {
                succeededKeys.forEach(redisTemplate::delete);
                throw new SeatAlreadyHeldException("이미 선점된 좌석입니다: " + seatId);
            }
        }
    }

    public void release(Long showtimeId, List<Long> seatIds, Long memberId) {
        for (Long seatId : seatIds) {
            String key = key(showtimeId, seatId);
            String holderId = redisTemplate.opsForValue().get(key);
            if (holderId == null) {
                continue;
            }
            if (!holderId.equals(String.valueOf(memberId))) {
                throw new NotSeatHolderException("본인이 선점한 좌석이 아닙니다: " + seatId);
            }
            redisTemplate.delete(key);
        }
    }

    public void validateHolder(Long showtimeId, List<Long> seatIds, Long memberId) {
        for (Long seatId : seatIds) {
            String holderId = redisTemplate.opsForValue().get(key(showtimeId, seatId));
            if (holderId == null || !holderId.equals(String.valueOf(memberId))) {
                throw new SeatHoldExpiredException("선점이 만료되었거나 본인이 선점하지 않은 좌석입니다: " + seatId);
            }
        }
    }

    private String key(Long showtimeId, Long seatId) {
        return "seat-hold:" + showtimeId + ":" + seatId;
    }
}
```

**코드 설명 (왜 이렇게 짰는지):**
- `setIfAbsent`가 Redis의 `SETNX`에 해당하는 메서드다 — "키가 없을 때만 값을 설정"을 원자적으로 처리해줘서, 두 요청이 동시에 같은 좌석을 선점하려 해도 Redis 입장에선 순서대로 처리되기 때문에 딱 하나만 성공한다.
- `hold()`에서 `succeededKeys`에 성공한 것만 쌓아두는 이유는, 3개 중 2개 성공하고 1개 실패했을 때 이미 성공한 2개를 다시 풀어주기(롤백) 위해서다.
- `release()`/`validateHolder()`에서 저장된 값(`memberId`)을 비교하는 이유는, 남의 선점을 내가 함부로 취소하거나 확정시키지 못하게 막기 위해서다.

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/dw/movie/reservation/SeatHoldService.java src/main/java/com/dw/movie/common/exception/SeatAlreadyHeldException.java src/main/java/com/dw/movie/common/exception/NotSeatHolderException.java src/main/java/com/dw/movie/common/exception/SeatHoldExpiredException.java src/main/java/com/dw/movie/common/exception/GlobalExceptionHandler.java
git commit -m "feat: Redis SETNX 기반 좌석 선점 서비스(SeatHoldService) 추가"
```

---

### Task 6: 좌석 현황 조회 API

**Files:**
- Create: `src/main/java/com/dw/movie/common/exception/ShowtimeNotFoundException.java`
- Create: `src/main/java/com/dw/movie/reservation/dto/SeatStatusResponse.java`
- Create: `src/main/java/com/dw/movie/reservation/SeatController.java`
- Create: `src/main/java/com/dw/movie/reservation/SeatQueryService.java`
- Modify: `src/main/java/com/dw/movie/common/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: `ShowtimeRepository.findById`, `SeatRepository`(showtimeId로 좌석 조회는 screenId를 거쳐야 함 — 아래 Step 참고), `ReservationSeatRepository.findByReservation_Showtime_IdAndReservation_Status`, `SeatHoldService.isHeld`
- Produces: `GET /api/showtimes/{showtimeId}/seats` → `List<SeatStatusResponse>`

이건 기존에 반복해온 패턴(Controller + Service + DTO 조합)이라 직접 짜볼 차례야. 아래 요구사항대로 작성해봐:

1. `ShowtimeNotFoundException`을 `ScreenNotFoundException`(`src/main/java/com/dw/movie/common/exception/ScreenNotFoundException.java`)과 똑같은 모양으로 만들어. `GlobalExceptionHandler`에도 404로 매핑 추가하고.
2. `SeatStatusResponse` DTO — 필드: `seatId(Long), rowLabel(String), seatNumber(Integer), grade(SeatGrade), status(SeatStatus)`. `ScreenResponse`(`@Getter @AllArgsConstructor`)랑 똑같은 스타일로.
3. `SeatQueryService.getSeatStatuses(Long showtimeId): List<SeatStatusResponse>` 만들기:
   - `showtimeRepository.findById(showtimeId)`로 Showtime 조회, 없으면 `ShowtimeNotFoundException`
   - `seatRepository.findAll()`로 전체 좌석을 가져온 뒤 `.stream().filter(seat -> seat.getScreen().getId().equals(showtime.getScreen().getId()))`로 해당 상영관 좌석만 필터링 (Seat와 Showtime 둘 다 Screen을 FK로 갖고 있으니 이렇게 연결돼)
   - `reservationSeatRepository.findByReservation_Showtime_IdAndReservation_Status(showtimeId, ReservationStatus.CONFIRMED)`로 이미 확정된 좌석 목록 뽑아서, 그 안에 있는 `seatId` Set을 만들어둠
   - 각 좌석마다: confirmed Set에 있으면 `SeatStatus.CONFIRMED`, 아니면 `seatHoldService.isHeld(showtimeId, seat.getId())`가 true면 `SeatStatus.HELD`, 둘 다 아니면 `SeatStatus.AVAILABLE`
4. `SeatController`— `@RestController @RequestMapping("/api/showtimes")`, `GET /{showtimeId}/seats` 엔드포인트에서 `SeatQueryService.getSeatStatuses` 호출해서 반환. `@PreAuthorize` 불필요(일반 로그인 사용자면 누구나 조회 가능 — `SecurityConfig`가 이미 `authenticated()`로 막아줌).

작성해서 보여주면 컴파일 체크하고 Postman 테스트(로그인 → 토큰으로 GET 요청 → 상태 확인)까지 같이 해보자.

- [ ] **Step 1: 위 요구사항대로 4개 파일 작성 (직접 시도)**
- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Postman으로 테스트**

앱 실행 → `POST /api/auth/login`으로 토큰 발급 → `GET /api/showtimes/{존재하는 showtimeId}/seats`에 `Authorization: Bearer {accessToken}` 헤더 달아서 요청.
Expected: 200 OK, 해당 상영관의 모든 좌석이 `AVAILABLE` 상태로 응답 (아직 선점/예약이 없으니까)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/dw/movie/common/exception/ShowtimeNotFoundException.java src/main/java/com/dw/movie/common/exception/GlobalExceptionHandler.java src/main/java/com/dw/movie/reservation/dto/SeatStatusResponse.java src/main/java/com/dw/movie/reservation/SeatController.java src/main/java/com/dw/movie/reservation/SeatQueryService.java
git commit -m "feat: 좌석 현황 조회 API 추가"
```

---

### Task 7: 좌석 선점 API

**Files:**
- Create: `src/main/java/com/dw/movie/common/exception/ShowtimeAlreadyStartedException.java`
- Create: `src/main/java/com/dw/movie/reservation/dto/SeatHoldRequest.java`
- Modify: `src/main/java/com/dw/movie/reservation/SeatController.java`
- Modify: `src/main/java/com/dw/movie/common/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: `SeatHoldService.hold`, `ShowtimeRepository.findById`
- Produces: `POST /api/showtimes/{showtimeId}/seats/hold` (body: `{"seatIds": [1,2,3]}`) → 200 OK

이것도 반복 패턴(요청 DTO + 컨트롤러 메서드 추가)이라 직접 시도해봐:

1. `ShowtimeAlreadyStartedException`을 다른 예외들과 같은 모양으로 만들고, `GlobalExceptionHandler`에 400으로 매핑
2. `SeatHoldRequest` DTO — 필드 `seatIds(List<Long>)`, `ScreenRegisterRequest`처럼 `@Getter`만 붙은 클래스로 (요청 DTO라 생성자 불필요, Jackson이 기본 생성자+세터리스 필드 바인딩으로 처리 — 이미 프로젝트에서 쓰던 패턴 그대로)
3. `SeatController`에 `POST /{showtimeId}/seats/hold` 추가:
   - `@AuthenticationPrincipal Long memberId`로 로그인 사용자 확인 (`MemberController.logout`처럼)
   - `showtimeRepository.findById(showtimeId)`로 Showtime 조회 (없으면 `ShowtimeNotFoundException`), `showtime.getStartTime().isBefore(LocalDateTime.now())`면 `ShowtimeAlreadyStartedException`
   - `seatHoldService.hold(showtimeId, request.getSeatIds(), memberId)` 호출
   - 성공하면 `ResponseEntity.ok().build()`

- [ ] **Step 1: 위 요구사항대로 작성 (직접 시도)**
- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Postman으로 테스트**

- 계정 A로 로그인 → `POST /{showtimeId}/seats/hold` body `{"seatIds": [1, 2]}` → 200 OK
- 같은 좌석으로 다시 요청(계정 A든 B든) → 409 Conflict 확인
- `GET /{showtimeId}/seats`로 조회했을 때 1, 2번 좌석이 `HELD`로 보이는지 확인

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/dw/movie/common/exception/ShowtimeAlreadyStartedException.java src/main/java/com/dw/movie/common/exception/GlobalExceptionHandler.java src/main/java/com/dw/movie/reservation/dto/SeatHoldRequest.java src/main/java/com/dw/movie/reservation/SeatController.java
git commit -m "feat: 좌석 선점(Hold) API 추가"
```

---

### Task 8: 선점 취소 API

**Files:**
- Modify: `src/main/java/com/dw/movie/reservation/SeatController.java`

**Interfaces:**
- Consumes: `SeatHoldService.release`
- Produces: `DELETE /api/showtimes/{showtimeId}/seats/hold` (body: `{"seatIds": [1,2,3]}`) → 200 OK

같은 패턴 반복이라 직접 시도: `SeatController`에 `DELETE /{showtimeId}/seats/hold` 추가. `SeatHoldRequest`를 그대로 재사용(`@RequestBody`), `@AuthenticationPrincipal Long memberId` 받아서 `seatHoldService.release(showtimeId, request.getSeatIds(), memberId)` 호출 후 `ResponseEntity.ok().build()`.

- [ ] **Step 1: 작성 (직접 시도)**
- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Postman으로 테스트**

계정 A로 좌석 1번 선점 → `DELETE .../seats/hold` body `{"seatIds": [1]}` → 200 OK → `GET .../seats`로 1번이 다시 `AVAILABLE`인지 확인. 계정 B로 (A가 선점한) 다른 좌석을 취소 시도 → 403 Forbidden 확인.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/dw/movie/reservation/SeatController.java
git commit -m "feat: 좌석 선점 취소 API 추가"
```

---

### Task 9: 예약 확정 API

**Files:**
- Create: `src/main/java/com/dw/movie/reservation/dto/ReservationConfirmRequest.java`
- Create: `src/main/java/com/dw/movie/reservation/dto/ReservationResponse.java`
- Create: `src/main/java/com/dw/movie/reservation/ReservationService.java`
- Create: `src/main/java/com/dw/movie/reservation/ReservationController.java`

**Interfaces:**
- Consumes: `SeatHoldService.validateHolder`, `SeatHoldService.release`, `SeatPriceCalculator.calculate`, `ReservationRepository.save`, `MemberRepository.findById`(이미 존재), `ShowtimeRepository.findById`, `SeatRepository.findAllById`(`JpaRepository`에 이미 내장된 메서드라 `SeatRepository`는 수정할 필요 없음)
- Produces: `POST /api/reservations` (body: `{"showtimeId": 1, "seatIds": [1,2]}`) → `ReservationResponse`

이번 태스크는 여러 개를 조합하는 이 서브프로젝트의 클라이맥스라 핵심 로직(`ReservationService.confirm`)은 직접 코드를 줄게. DTO/Controller는 반복 패턴이니 그 부분만 직접 시도해봐.

- [ ] **Step 1: ReservationService 작성 (제공 코드)**

```java
package com.dw.movie.reservation;

import com.dw.movie.auth.Member;
import com.dw.movie.auth.MemberRepository;
import com.dw.movie.common.exception.MemberNotFoundException;
import com.dw.movie.common.exception.ShowtimeNotFoundException;
import com.dw.movie.screen.Seat;
import com.dw.movie.screen.SeatRepository;
import com.dw.movie.showtime.Showtime;
import com.dw.movie.showtime.ShowtimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final MemberRepository memberRepository;
    private final SeatHoldService seatHoldService;

    public ReservationService(ReservationRepository reservationRepository, ShowtimeRepository showtimeRepository,
                               SeatRepository seatRepository, MemberRepository memberRepository, SeatHoldService seatHoldService) {
        this.reservationRepository = reservationRepository;
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
        this.memberRepository = memberRepository;
        this.seatHoldService = seatHoldService;
    }

    @Transactional
    public Reservation confirm(Long memberId, Long showtimeId, List<Long> seatIds) {
        seatHoldService.validateHolder(showtimeId, seatIds, memberId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException("해당 회원을 찾을 수 없습니다."));
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ShowtimeNotFoundException("해당 상영시간표를 찾을 수 없습니다."));
        List<Seat> seats = seatRepository.findAllById(seatIds);

        long totalPrice = 0L;
        Reservation reservation = new Reservation(member, showtime, 0L);
        for (Seat seat : seats) {
            long price = SeatPriceCalculator.calculate(showtime.getBasePrice(), seat.getGrade(), showtime.getFormat());
            totalPrice += price;
            reservation.addSeat(seat, price);
        }
        reservation.applyTotalPrice(totalPrice);

        Reservation saved = reservationRepository.save(reservation);
        seatHoldService.release(showtimeId, seatIds, memberId);

        return saved;
    }

    public List<Reservation> getMyReservations(Long memberId) {
        return reservationRepository.findByMemberId(memberId);
    }
}
```

이 코드가 쓰는 `reservation.addSeat(...)`, `reservation.applyTotalPrice(...)`, `MemberNotFoundException`은 아직 없다 (아래 Step에서 만든다). `cancel()` 메서드는 이번 태스크에서는 만들지 않는다 — Task 11(예약 취소)에서 이 파일에 이어서 추가할 것.

- [ ] **Step 2: Reservation 엔티티에 헬퍼 메서드 추가**

`src/main/java/com/dw/movie/reservation/Reservation.java`에 아래 필드/메서드 추가:

```java
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL)
    private List<ReservationSeat> seats = new java.util.ArrayList<>();

    public void addSeat(com.dw.movie.screen.Seat seat, long price) {
        this.seats.add(new ReservationSeat(this, seat, price));
    }

    public void applyTotalPrice(long totalPrice) {
        this.totalPrice = totalPrice;
    }
```

`Reservation.java`는 원래 `import java.util.List;`가 없으니(Task 3에서는 `LocalDateTime`만 import했음) 파일 상단 import 목록에 `import java.util.List;`를 추가해야 한다. 생성자에서 `totalPrice`를 받던 부분은 그대로 둬도 된다(초기 0으로 넣고 나중에 `applyTotalPrice`로 갱신하는 흐름).

- [ ] **Step 3: MemberNotFoundException 확인/생성**

`find src/main/java -iname "MemberNotFoundException.java"`로 확인. 없으면:

```java
package com.dw.movie.common.exception;

public class MemberNotFoundException extends RuntimeException {
    public MemberNotFoundException(String message) {
        super(message);
    }
}
```

`GlobalExceptionHandler`에도 404 매핑 추가 (이미 있다면 스킵).

- [ ] **Step 4: 나머지 DTO/Controller는 직접 시도**

1. `ReservationConfirmRequest` — 필드 `showtimeId(Long)`, `seatIds(List<Long>)`, `@Getter`만
2. `ReservationResponse` — 필드 `id(Long), movieTitle(String), screenName(String), startTime(LocalDateTime), totalPrice(Long), status(ReservationStatus), createdAt(LocalDateTime)`, `@Getter @AllArgsConstructor`
3. `ReservationController` — `@RestController @RequestMapping("/api/reservations")`, `POST` 엔드포인트: `@AuthenticationPrincipal Long memberId`, `@RequestBody ReservationConfirmRequest request` 받아서 `reservationService.confirm(memberId, request.getShowtimeId(), request.getSeatIds())` 호출하고 결과를 `ReservationResponse`로 변환해서 `201 Created`로 응답 (변환 시 `reservation.getShowtime().getMovie().getTitle()`, `.getScreen().getName()` 사용)

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Postman으로 테스트**

계정 A로 좌석 선점(Task 7) → `POST /api/reservations` body `{"showtimeId": ..., "seatIds": [...]}` → 201 Created, `totalPrice`가 좌석 등급/포맷대로 계산됐는지 확인 → `GET .../seats`로 해당 좌석이 `CONFIRMED`로 보이는지 확인

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: 예약 확정 API 추가"
```

---

### Task 10: 내 예약 목록 조회 API

**Files:**
- Modify: `src/main/java/com/dw/movie/reservation/ReservationController.java`

**Interfaces:**
- Consumes: `ReservationService.getMyReservations`
- Produces: `GET /api/reservations/me` → `List<ReservationResponse>`

직접 시도: `ReservationController`에 `GET /me` 추가. `@AuthenticationPrincipal Long memberId` 받아서 `reservationService.getMyReservations(memberId)` 호출, Task 9에서 만든 것과 같은 방식으로 `List<ReservationResponse>`로 변환해서 응답.

- [ ] **Step 1: 작성 (직접 시도)**
- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Postman으로 테스트**

계정 A로 예약 1~2건 만든 뒤 `GET /api/reservations/me` → 200 OK, 본인이 만든 예약만 보이는지 확인 (계정 B로 조회하면 안 보여야 함)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/dw/movie/reservation/ReservationController.java
git commit -m "feat: 내 예약 목록 조회 API 추가"
```

---

### Task 11: 예약 취소 API

**Files:**
- Create: `src/main/java/com/dw/movie/common/exception/ReservationNotFoundException.java`
- Create: `src/main/java/com/dw/movie/common/exception/NotReservationOwnerException.java`
- Create: `src/main/java/com/dw/movie/common/exception/ReservationAlreadyCancelledException.java`
- Modify: `src/main/java/com/dw/movie/reservation/ReservationService.java`
- Modify: `src/main/java/com/dw/movie/reservation/ReservationController.java`
- Modify: `src/main/java/com/dw/movie/common/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Produces: `DELETE /api/reservations/{id}` → 200 OK

- [ ] **Step 1: 예외 클래스 3개 작성 (다른 예외들과 동일한 패턴)**

```java
package com.dw.movie.common.exception;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(String message) {
        super(message);
    }
}
```

```java
package com.dw.movie.common.exception;

public class NotReservationOwnerException extends RuntimeException {
    public NotReservationOwnerException(String message) {
        super(message);
    }
}
```

```java
package com.dw.movie.common.exception;

public class ReservationAlreadyCancelledException extends RuntimeException {
    public ReservationAlreadyCancelledException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: GlobalExceptionHandler에 매핑 추가**

```java
    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<String> handleReservationNotFound(ReservationNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
    @ExceptionHandler(NotReservationOwnerException.class)
    public ResponseEntity<String> handleNotReservationOwner(NotReservationOwnerException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
    @ExceptionHandler(ReservationAlreadyCancelledException.class)
    public ResponseEntity<String> handleReservationAlreadyCancelled(ReservationAlreadyCancelledException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
```

- [ ] **Step 3: ReservationService에 cancel() 추가**

Task 9에서 미뤄둔 부분을 마저 채운다. `ReservationService`에 아래 메서드 추가:

```java
    @Transactional
    public void cancel(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("해당 예약을 찾을 수 없습니다."));

        if (!reservation.getMember().getId().equals(memberId)) {
            throw new NotReservationOwnerException("본인의 예약이 아닙니다.");
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new ReservationAlreadyCancelledException("이미 취소된 예약입니다.");
        }

        reservation.cancel();
    }
```

파일 상단 import에 아래 3개 추가:

```java
import com.dw.movie.common.exception.ReservationNotFoundException;
import com.dw.movie.common.exception.NotReservationOwnerException;
import com.dw.movie.common.exception.ReservationAlreadyCancelledException;
```

- [ ] **Step 4: ReservationController에 DELETE 엔드포인트 추가 (직접 시도)**

`DELETE /{id}` — `@AuthenticationPrincipal Long memberId`, `@PathVariable Long id` 받아서 `reservationService.cancel(memberId, id)` 호출 후 `ResponseEntity.noContent().build()` (기존 `MemberController.logout`과 같은 응답 패턴).

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Postman으로 테스트**

계정 A로 예약 확정 → `DELETE /api/reservations/{id}` → 204 No Content → `GET /api/reservations/me`로 상태가 `CANCELLED`인지 확인 → `GET .../seats`로 해당 좌석이 다시 `AVAILABLE`인지 확인 → 같은 예약 다시 취소 시도 → 409 Conflict 확인 → 계정 B로 A의 예약 취소 시도 → 403 Forbidden 확인

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: 예약 취소 API 추가"
```

---

### Task 12: 동시성 통합 테스트

**Files:**
- Test: `src/test/java/com/dw/movie/reservation/SeatHoldConcurrencyTest.java`

**Interfaces:**
- Consumes: `SeatHoldService.hold` (실제 Redis 연결 필요, `@SpringBootTest`)

이번 서브프로젝트의 핵심 목표를 직접 검증하는 테스트다. 같은 좌석에 10개 스레드가 동시에 선점을 시도했을 때 딱 1개만 성공하는지 확인한다.

- [ ] **Step 1: 테스트 작성 (제공 코드)**

```java
package com.dw.movie.reservation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class SeatHoldConcurrencyTest {

    @Autowired
    private SeatHoldService seatHoldService;

    @Test
    void 같은_좌석에_동시에_10명이_선점을_시도하면_한명만_성공한다() throws InterruptedException {
        Long showtimeId = 999L; // 테스트 전용 임의 showtimeId
        Long seatId = 999L;     // 테스트 전용 임의 seatId
        int threadCount = 10;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            long memberId = i;
            executorService.submit(() -> {
                try {
                    seatHoldService.hold(showtimeId, List.of(seatId), memberId);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                    // 선점 실패는 정상 동작 — 카운트하지 않음
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        assertEquals(1, successCount.get());
    }
}
```

**왜 이렇게 짰는지:** `ExecutorService`로 10개 스레드를 동시에 띄우고, `CountDownLatch`로 10개가 다 끝날 때까지 기다린 다음, `AtomicInteger`(여러 스레드가 동시에 값을 바꿔도 안전한 카운터)로 성공한 개수를 센다. Redis의 `SETNX` 원자성이 제대로 동작한다면 결과는 항상 정확히 1이어야 한다.

- [ ] **Step 2: 테스트 전 로컬 Redis/DB가 떠 있는지 확인**

Run: `docker ps` — Redis 컨테이너와 PostgreSQL이 모두 떠 있어야 `@SpringBootTest`가 뜬다 (테스트가 스프링 컨텍스트 전체를 띄우기 때문).

- [ ] **Step 3: 테스트 실행**

Run: `./gradlew test --tests "com.dw.movie.reservation.SeatHoldConcurrencyTest"`
Expected: PASS — `successCount`가 정확히 1

- [ ] **Step 4: (선택) 테스트를 일부러 깨뜨려서 검증 방식 확인**

`SeatHoldService.hold()`의 `setIfAbsent` 부분을 일부러 `redisTemplate.opsForValue().set(key, ...)` (무조건 덮어쓰기)로 잠깐 바꾼 뒤 테스트를 다시 돌려보면 `successCount`가 1보다 크게 나오는 걸 확인할 수 있다 — 이게 바로 SETNX 없이는 동시성 제어가 안 된다는 증거. 확인했으면 원래 코드로 되돌려놓는다.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/dw/movie/reservation/SeatHoldConcurrencyTest.java
git commit -m "test: 좌석 선점 동시성 통합 테스트 추가"
```

---

### Task 13: README 업데이트

**Files:**
- Modify: `README.md`

**Interfaces:** 없음 (문서 작업)

- [ ] **Step 1: 진행 상황 체크박스 갱신**

`README.md`의 `## 진행 상황`에서:
```
- [ ] 좌석 예약 + Redis 동시성 제어
```
→
```
- [x] 좌석 예약 + Redis 동시성 제어
```

- [ ] **Step 2: 예약 API 섹션 추가**

`## 관리자 API` 섹션 뒤에 아래 추가:

```markdown
## 예약 API

로그인 필요.

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/showtimes/{showtimeId}/seats` | 좌석 현황 조회 |
| POST | `/api/showtimes/{showtimeId}/seats/hold` | 좌석 선점 |
| DELETE | `/api/showtimes/{showtimeId}/seats/hold` | 선점 취소 |
| POST | `/api/reservations` | 예약 확정 |
| GET | `/api/reservations/me` | 내 예약 목록 조회 |
| DELETE | `/api/reservations/{id}` | 예약 취소 |
```

- [ ] **Step 3: 기술 스택에 Redis 추가**

`## 기술 스택` 섹션의 `- **DB**: PostgreSQL` 아래 줄에 추가:

```markdown
- **캐시/동시성 제어**: Redis (좌석 선점 TTL + SETNX)
```

- [ ] **Step 4: 실행 섹션에 Redis 안내 추가**

`## 실행` 섹션에 한 줄 추가:

```markdown
로컬 Redis가 필요하다: `docker run -d -p 6379:6379 --name cineo-redis redis`
```

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: README에 좌석 예약 서브프로젝트 반영"
```

---

## 완료 후

전체 태스크가 끝나면 서브프로젝트 3번이 완료된다. 다음은 4번(결제 연동) 서브프로젝트 브레인스토밍으로 넘어가면 된다.
