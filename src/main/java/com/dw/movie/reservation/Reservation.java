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

    public Reservation(Member member, Showtime showtime, Long totalPrice){
        this.member = member;
        this.showtime = showtime;
        this.status = ReservationStatus.CONFIRMED;
        this.totalPrice = totalPrice;
        this.createdAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
        this.canceledAt = LocalDateTime.now();
    }
}
