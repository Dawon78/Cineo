package com.dw.movie.reservation.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class SeatHoldRequest {
    private List<Long> seatIds;
}