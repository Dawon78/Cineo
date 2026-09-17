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