package com.dw.movie.reservation.dto;

import com.dw.movie.reservation.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
public class ReservationResponse {
    private Long id;
    private String movieTitle;
    private String screenName;
    private LocalDateTime startTime;
    private Long totalPrice;
    private ReservationStatus status;
    private LocalDateTime createdAt;
}
