package com.dw.movie.reservation.entity;

import com.dw.movie.member.entity.Member;
import com.dw.movie.screen.entity.Seat;
import com.dw.movie.showtime.entity.Showtime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    @Column(unique = true)
    private String impUid;
    private Long paidAmount;
    private Long earnedPoints;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL)
    private List<ReservationSeat> seats = new ArrayList<>();

    public Reservation(Member member, Showtime showtime){
        this.member = member;
        this.showtime = showtime;
        this.status = ReservationStatus.CONFIRMED;
        this.totalPrice = 0L;
        this.createdAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
        this.canceledAt = LocalDateTime.now();
    }

    // 좌석을 담을 때 총액도 같이 올려서, 좌석 가격 합계와 총액이 항상 같게 유지한다
    public void addSeat(Seat seat, long price) {
        this.seats.add(new ReservationSeat(this, seat, price));
        this.totalPrice += price;
    }

    public void applyEarnedPoints(long earnedPoints) {
        this.earnedPoints = earnedPoints;
    }

    public void applyPayment(String impUid, Long paidAmount) {
        this.impUid = impUid;
        this.paidAmount = paidAmount;
    }
}
