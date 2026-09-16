package com.dw.movie.showtime.dto;

import com.dw.movie.showtime.ShowtimeFormat;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ShowtimeRegisterRequest {
    private Long movieId;
    private Long screenId;
    private ShowtimeFormat format;
    private LocalDateTime startTime;
    private Long basePrice;
}
