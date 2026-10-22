package com.dw.movie.reservation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

@Getter
public class SeatHoldRequest {
    @NotEmpty
    @Size(max = 10)
    private List<@NotNull Long> seatIds;
}
