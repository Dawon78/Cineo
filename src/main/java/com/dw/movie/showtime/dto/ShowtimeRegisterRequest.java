package com.dw.movie.showtime.dto;

import com.dw.movie.showtime.entity.ShowtimeFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ShowtimeRegisterRequest {
    @NotNull
    private Long movieId;

    @NotNull
    private Long screenId;

    @NotNull
    private ShowtimeFormat format;

    @NotNull
    private LocalDateTime startTime;

    @NotNull
    @Positive
    private Long basePrice;
}
