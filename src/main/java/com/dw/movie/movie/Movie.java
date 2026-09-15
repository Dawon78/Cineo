package com.dw.movie.movie;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long tmdbId;

    private String title;
    private String posterUrl;

    @Column(columnDefinition = "TEXT")
    private String overview;

    private LocalDate releaseDate;
    private Integer runningTime;
    private String ageRating;

    private LocalDateTime createdAt;

    public Movie(Long tmdbId, String title, String posterUrl, String overview, LocalDate releaseDate, Integer runningTime, String ageRating) {
        this.tmdbId = tmdbId;
        this.title = title;
        this.posterUrl = posterUrl;
        this.overview = overview;
        this.releaseDate = releaseDate;
        this.runningTime = runningTime;
        this.ageRating = ageRating;
        this.createdAt = LocalDateTime.now();
    }
}