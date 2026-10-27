package com.dw.movie.movie.dto;

import com.dw.movie.movie.entity.Movie;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class MovieResponse {
    private long id;
    private long tmdbId;
    private String title;
    private String posterUrl;
    private String overview;
    private LocalDate releaseDate;
    private Integer runningTime;
    private String ageRating;

    public static MovieResponse from(Movie movie) {
        return new MovieResponse(
                movie.getId(),
                movie.getTmdbId(),
                movie.getTitle(),
                movie.getPosterUrl(),
                movie.getOverview(),
                movie.getReleaseDate(),
                movie.getRunningTime(),
                movie.getAgeRating()
        );
    }
}

