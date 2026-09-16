package com.dw.movie.movie.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class MovieResponse {
    private long id;
    private long tmdbid;
    private String title;
    private String posterUrl;
    private String overView;
    private LocalDate releaseDate;
    private Integer runningTime;
    private String ageRating;
}
