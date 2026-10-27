package com.dw.movie.reservation.dto;

import com.dw.movie.reservation.entity.Reservation;
import com.dw.movie.reservation.entity.ReservationSeat;
import com.dw.movie.reservation.entity.ReservationStatus;
import com.dw.movie.screen.entity.SeatGrade;
import com.dw.movie.showtime.entity.Showtime;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Getter
@AllArgsConstructor
public class ReservationDetailResponse {
    private Long id;
    private ReservationStatus status;
    private String movieTitle;
    private String posterUrl;
    private String screenName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<SeatItem> seats;
    private Long totalPrice;
    private Long paidAmount;
    private Long earnedPoints;
    private String impUid;
    private LocalDateTime createdAt;
    private LocalDateTime canceledAt;

    public static ReservationDetailResponse from(Reservation reservation) {
        Showtime showtime = reservation.getShowtime();
        // 좌석은 A1, A2, B1 순서로 보여준다
        List<SeatItem> seats = reservation.getSeats().stream()
                .sorted(Comparator.comparing((ReservationSeat rs) -> rs.getSeat().getRowLabel())
                        .thenComparing(rs -> rs.getSeat().getSeatNumber()))
                .map(rs -> new SeatItem(rs.getSeat().getRowLabel() + rs.getSeat().getSeatNumber(),
                        rs.getSeat().getGrade(), rs.getPrice()))
                .toList();

        return new ReservationDetailResponse(
                reservation.getId(),
                reservation.getStatus(),
                showtime.getMovie().getTitle(),
                showtime.getMovie().getPosterUrl(),
                showtime.getScreen().getName(),
                showtime.getStartTime(),
                showtime.getEndTime(),
                seats,
                reservation.getTotalPrice(),
                reservation.getPaidAmount(),
                reservation.getEarnedPoints(),
                reservation.getImpUid(),
                reservation.getCreatedAt(),
                reservation.getCanceledAt()
        );
    }

    @Getter
    @AllArgsConstructor
    public static class SeatItem {
        private String label;
        private SeatGrade grade;
        private Long price;
    }
}
