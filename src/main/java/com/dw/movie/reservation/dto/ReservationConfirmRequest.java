package com.dw.movie.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

@Getter
public class ReservationConfirmRequest {
    @NotNull
    private Long showtimeId;

    @NotEmpty
    @Size(max = 10)
    private List<@NotNull Long> seatIds;

    @NotBlank
    @Size(max = 100)
    private String impUid;
}
