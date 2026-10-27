package com.dw.movie.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SeatHoldResponse {
    private long expiresInSeconds;
}
