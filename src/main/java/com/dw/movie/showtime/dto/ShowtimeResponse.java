package com.dw.movie.showtime.dto;

import com.dw.movie.showtime.entity.Showtime;
import com.dw.movie.showtime.entity.ShowtimeFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ShowtimeResponse {
    private Long id;
    private String movieTitle;
    private String screenName;
    private ShowtimeFormat format;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long basePrice;

    public static ShowtimeResponse from(Showtime showtime) {
        return new ShowtimeResponse(
                showtime.getId(),
                showtime.getMovie().getTitle(),
                showtime.getScreen().getName(),
                showtime.getFormat(),
                showtime.getStartTime(),
                showtime.getEndTime(),
                showtime.getBasePrice()
        );
    }
}
