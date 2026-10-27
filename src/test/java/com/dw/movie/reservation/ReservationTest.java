package com.dw.movie.reservation;

import com.dw.movie.member.entity.Member;
import com.dw.movie.reservation.entity.Reservation;
import com.dw.movie.reservation.entity.ReservationSeat;
import com.dw.movie.reservation.entity.ReservationStatus;
import com.dw.movie.screen.entity.Screen;
import com.dw.movie.screen.entity.Seat;
import com.dw.movie.screen.entity.SeatGrade;
import com.dw.movie.showtime.entity.Showtime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReservationTest {

    @Test
    void Reservation은_생성되면_CONFIRMED_상태이고_총액은_0이다() {
        Reservation reservation = new Reservation(new Member(), new Showtime());

        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        assertEquals(0L, reservation.getTotalPrice());
        assertNotNull(reservation.getCreatedAt());
    }

    @Test
    void addSeat_하면_좌석이_담기고_총액이_좌석_가격만큼_올라간다() {
        Reservation reservation = new Reservation(new Member(), new Showtime());
        Screen screen = new Screen("1관");

        reservation.addSeat(new Seat(screen, "A", 1, SeatGrade.STANDARD), 12000L);
        reservation.addSeat(new Seat(screen, "A", 2, SeatGrade.PREMIUM), 15000L);

        assertEquals(2, reservation.getSeats().size());
        assertEquals(27000L, reservation.getTotalPrice());
    }

    @Test
    void cancel_호출하면_CANCELLED로_바뀐다() {
        Reservation reservation = new Reservation(new Member(), new Showtime());

        reservation.cancel();

        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertNotNull(reservation.getCanceledAt());
    }

    @Test
    void ReservationSeat은_가격_스냅샷을_들고있다() {
        Reservation reservation = new Reservation(new Member(), new Showtime());
        Screen screen = new Screen("1관");
        Seat seat = new Seat(screen, "A", 1, SeatGrade.PREMIUM);

        ReservationSeat reservationSeat = new ReservationSeat(reservation, seat, 13000L);

        assertEquals(13000L, reservationSeat.getPrice());
        assertEquals(seat, reservationSeat.getSeat());
    }

    @Test
    void applyPayment_호출하면_impUid와_결제금액이_저장된다() {
        Reservation reservation = new Reservation(new Member(), new Showtime());

        reservation.applyPayment("imp_123456789", 15000L);

        assertEquals("imp_123456789", reservation.getImpUid());
        assertEquals(15000L, reservation.getPaidAmount());
    }
}
