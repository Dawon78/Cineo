package com.dw.movie.reservation;

import com.dw.movie.auth.Member;
import com.dw.movie.screen.Screen;
import com.dw.movie.screen.Seat;
import com.dw.movie.screen.SeatGrade;
import com.dw.movie.showtime.Showtime;
import org.junit.jupiter.api.Test;

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

    @Test
    void applyPayment_호출하면_impUid와_결제금액이_저장된다() {
        Reservation reservation = new Reservation(new Member(), new Showtime(), 15000L);

        reservation.applyPayment("imp_123456789", 15000L);

        assertEquals("imp_123456789", reservation.getImpUid());
        assertEquals(15000L, reservation.getPaidAmount());
    }
}