package com.dw.movie.movie.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

@Getter
public class MovieRegisterRequest {
    @NotNull
    @Positive
    private Long tmdbId;
}
