package com.dw.movie.reservation.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class ReservationConfirmRequest {
    private Long showtimeId;
    private List<Long> seatIds;
}
