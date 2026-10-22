package com.dw.movie.reservation;

import com.dw.movie.auth.entity.Member;
import com.dw.movie.auth.repository.MemberRepository;
import com.dw.movie.common.exception.ShowtimeAlreadyStartedException;
import com.dw.movie.payment.PaymentVerificationFailedException;
import com.dw.movie.payment.PortOneClient;
import com.dw.movie.reservation.entity.Reservation;
import com.dw.movie.reservation.entity.ReservationStatus;
import com.dw.movie.reservation.repository.ReservationRepository;
import com.dw.movie.reservation.service.ReservationService;
import com.dw.movie.reservation.service.SeatHoldService;
import com.dw.movie.reservation.service.SeatQueryService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

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
    void 정상_취소하면_환불이_호출되고_누적결제금액이_복구된다() {
        Member member = memberWithId(1L);
        member.addSpent(10000L);
        Reservation reservation = new Reservation(member, showtimeStartingAt(LocalDateTime.now().plusDays(1)), 10000L);
        reservation.applyPayment("imp_1", 10000L);
        given(reservationRepository.findById(10L)).willReturn(Optional.of(reservation));

        reservationService.cancel(1L, 10L);

        verify(portOneClient).cancelPayment("imp_1", "고객 예약 취소");
        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertEquals(0L, member.getTotalSpent());
    }

    @Test
    void 이미_사용된_impUid로는_예약을_확정할_수_없고_포트원도_조회하지_않는다() {
        Member member = memberWithId(1L);
        Showtime showtime = showtimeStartingAt(LocalDateTime.now().plusDays(1));
        given(showtimeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(showtime));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(seatQueryService.validateSelectable(any(), any())).willReturn(List.of());
        given(reservationRepository.existsByImpUid("imp_dup")).willReturn(true);

        assertThrows(PaymentVerificationFailedException.class,
                () -> reservationService.confirm(1L, 1L, List.of(5L), "imp_dup"));

        verify(portOneClient, never()).getPaymentInfo(any());
    }
}
