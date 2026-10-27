package com.dw.movie.reservation;

import com.dw.movie.member.entity.Member;
import com.dw.movie.member.repository.MemberRepository;
import com.dw.movie.common.exception.SeatAlreadyHeldException;
import com.dw.movie.common.exception.SeatAlreadyReservedException;
import com.dw.movie.common.exception.ShowtimeAlreadyStartedException;
import com.dw.movie.payment.exception.PaymentVerificationFailedException;
import com.dw.movie.payment.exception.PortOneApiException;
import com.dw.movie.payment.exception.RefundFailedException;
import com.dw.movie.payment.client.PortOneClient;
import com.dw.movie.reservation.entity.Reservation;
import com.dw.movie.reservation.entity.ReservationStatus;
import com.dw.movie.reservation.repository.ReservationRepository;
import com.dw.movie.reservation.service.ReservationService;
import com.dw.movie.reservation.service.SeatHoldService;
import com.dw.movie.reservation.service.SeatQueryService;
import com.dw.movie.screen.entity.Screen;
import com.dw.movie.screen.entity.Seat;
import com.dw.movie.screen.entity.SeatGrade;
import com.dw.movie.screen.repository.SeatRepository;
import com.dw.movie.showtime.entity.Showtime;
import com.dw.movie.showtime.entity.ShowtimeFormat;
import com.dw.movie.showtime.repository.ShowtimeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final String REFUND_REASON = "좌석 확보 실패로 인한 자동 환불";

    @Mock ReservationRepository reservationRepository;
    @Mock ShowtimeRepository showtimeRepository;
    @Mock SeatRepository seatRepository;
    @Mock MemberRepository memberRepository;
    @Mock SeatHoldService seatHoldService;
    @Mock PortOneClient portOneClient;
    @Mock SeatQueryService seatQueryService;

    @InjectMocks ReservationService reservationService;

    private Member memberWithId(Long id) {
        Member member = new Member("a@a.com", "pw", "name", "010");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Showtime showtimeStartingAt(LocalDateTime startTime) {
        return new Showtime(null, null, ShowtimeFormat.TWO_D, startTime, startTime.plusMinutes(120), 10000L);
    }

    private Seat standardSeat() {
        return new Seat(new Screen("관"), "A", 1, SeatGrade.STANDARD);
    }

    @Test
    void 상영이_시작된_예약은_취소할_수_없고_환불도_호출되지_않는다() {
        Member member = memberWithId(1L);
        Reservation reservation = new Reservation(member, showtimeStartingAt(LocalDateTime.now().minusMinutes(1)), 10000L);
        given(reservationRepository.findById(10L)).willReturn(Optional.of(reservation));

        assertThrows(ShowtimeAlreadyStartedException.class, () -> reservationService.cancel(1L, 10L));

        verify(portOneClient, never()).cancelPayment(any(), any());
        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
    }

    @Test
    void 정상_취소하면_환불이_호출되고_누적결제금액과_포인트가_복구된다() {
        Member member = memberWithId(1L);
        long earned = member.recordPayment(10000L);
        Reservation reservation = new Reservation(member, showtimeStartingAt(LocalDateTime.now().plusDays(1)), 10000L);
        reservation.applyPayment("imp_1", 10000L);
        reservation.applyEarnedPoints(earned);
        given(reservationRepository.findById(10L)).willReturn(Optional.of(reservation));

        reservationService.cancel(1L, 10L);

        verify(portOneClient).cancelPayment("imp_1", "고객 예약 취소");
        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertEquals(0L, member.getTotalSpent());
        assertEquals(0L, member.getPoints());
    }

    @Test
    void 이미_사용된_impUid로는_예약을_확정할_수_없고_포트원도_조회하지_않는다() {
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(memberWithId(1L)));
        given(showtimeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(showtimeStartingAt(LocalDateTime.now().plusDays(1))));
        given(reservationRepository.existsByImpUid("imp_dup")).willReturn(true);

        assertThrows(PaymentVerificationFailedException.class,
                () -> reservationService.confirm(1L, 1L, List.of(5L), "imp_dup"));

        verify(portOneClient, never()).getPaymentInfo(any());
        verify(portOneClient, never()).cancelPayment(any(), any());
    }

    @Test
    void 결제금액이_다르면_자동환불하고_예외를_던진다() {
        Seat seat = standardSeat();
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(memberWithId(1L)));
        given(showtimeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(showtimeStartingAt(LocalDateTime.now().plusDays(1))));
        given(reservationRepository.existsByImpUid("imp_1")).willReturn(false);
        given(seatQueryService.validateSeatShape(any(), any())).willReturn(List.of(seat));
        given(portOneClient.getPaymentInfo("imp_1")).willReturn(new PortOneClient.PaymentInfo("imp_1", 5000L, "paid"));

        assertThrows(PaymentVerificationFailedException.class,
                () -> reservationService.confirm(1L, 1L, List.of(5L), "imp_1"));

        verify(portOneClient).cancelPayment("imp_1", "결제 금액 불일치로 인한 자동 환불");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 결제조회가_실패하면_자동환불을_시도하고_예외를_던진다() {
        Seat seat = standardSeat();
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(memberWithId(1L)));
        given(showtimeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(showtimeStartingAt(LocalDateTime.now().plusDays(1))));
        given(reservationRepository.existsByImpUid("imp_1")).willReturn(false);
        given(seatQueryService.validateSeatShape(any(), any())).willReturn(List.of(seat));
        given(portOneClient.getPaymentInfo("imp_1")).willThrow(new PortOneApiException("포트원 결제 조회에 실패했습니다."));

        assertThrows(PortOneApiException.class,
                () -> reservationService.confirm(1L, 1L, List.of(5L), "imp_1"));

        verify(portOneClient).cancelPayment("imp_1", "결제 확인 실패로 인한 자동 환불");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 결제조회_실패_후_환불도_실패하면_원래_조회_실패_예외를_던진다() {
        Seat seat = standardSeat();
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(memberWithId(1L)));
        given(showtimeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(showtimeStartingAt(LocalDateTime.now().plusDays(1))));
        given(reservationRepository.existsByImpUid("imp_1")).willReturn(false);
        given(seatQueryService.validateSeatShape(any(), any())).willReturn(List.of(seat));
        given(portOneClient.getPaymentInfo("imp_1")).willThrow(new PortOneApiException("포트원 결제 조회에 실패했습니다."));
        doThrow(new RefundFailedException("포트원 환불 요청에 실패했습니다."))
                .when(portOneClient).cancelPayment(any(), any());

        assertThrows(PortOneApiException.class,
                () -> reservationService.confirm(1L, 1L, List.of(5L), "imp_1"));

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 결제검증_후_좌석이_이미_예약돼있으면_자동환불하고_예외를_던진다() {
        Seat seat = standardSeat();
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(memberWithId(1L)));
        given(showtimeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(showtimeStartingAt(LocalDateTime.now().plusDays(1))));
        given(reservationRepository.existsByImpUid("imp_1")).willReturn(false);
        given(seatQueryService.validateSeatShape(any(), any())).willReturn(List.of(seat));
        given(portOneClient.getPaymentInfo("imp_1")).willReturn(new PortOneClient.PaymentInfo("imp_1", 10000L, "paid"));
        doThrow(new SeatAlreadyReservedException("이미 예약된 좌석이 포함되어 있습니다."))
                .when(seatQueryService).validateNotReserved(any(), any());

        assertThrows(SeatAlreadyReservedException.class,
                () -> reservationService.confirm(1L, 1L, List.of(5L), "imp_1"));

        verify(portOneClient).cancelPayment("imp_1", REFUND_REASON);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 결제검증_후_다른_사용자가_선점_중이면_자동환불하고_예외를_던진다() {
        Seat seat = standardSeat();
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(memberWithId(1L)));
        given(showtimeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(showtimeStartingAt(LocalDateTime.now().plusDays(1))));
        given(reservationRepository.existsByImpUid("imp_1")).willReturn(false);
        given(seatQueryService.validateSeatShape(any(), any())).willReturn(List.of(seat));
        given(portOneClient.getPaymentInfo("imp_1")).willReturn(new PortOneClient.PaymentInfo("imp_1", 10000L, "paid"));
        doThrow(new SeatAlreadyHeldException("다른 사용자가 선점 중인 좌석입니다: 5"))
                .when(seatHoldService).validateNotHeldByOthers(any(), any(), any());

        assertThrows(SeatAlreadyHeldException.class,
                () -> reservationService.confirm(1L, 1L, List.of(5L), "imp_1"));

        verify(portOneClient).cancelPayment("imp_1", REFUND_REASON);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 선점이_만료됐어도_좌석이_비어있으면_예약이_확정되고_환불되지_않는다() {
        Member member = memberWithId(1L);
        Seat seat = standardSeat();
        given(memberRepository.findByIdForUpdate(1L)).willReturn(Optional.of(member));
        given(showtimeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(showtimeStartingAt(LocalDateTime.now().plusDays(1))));
        given(reservationRepository.existsByImpUid("imp_1")).willReturn(false);
        given(seatQueryService.validateSeatShape(any(), any())).willReturn(List.of(seat));
        given(portOneClient.getPaymentInfo("imp_1")).willReturn(new PortOneClient.PaymentInfo("imp_1", 10000L, "paid"));
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        Reservation result = reservationService.confirm(1L, 1L, List.of(5L), "imp_1");

        assertEquals(ReservationStatus.CONFIRMED, result.getStatus());
        assertEquals("imp_1", result.getImpUid());
        assertEquals(10000L, result.getTotalPrice());
        assertEquals(10000L, member.getTotalSpent());
        assertEquals(100L, member.getPoints());
        assertEquals(100L, result.getEarnedPoints());
        verify(seatHoldService).releaseOwned(1L, List.of(5L), 1L);
        verify(portOneClient, never()).cancelPayment(any(), any());
    }
}
