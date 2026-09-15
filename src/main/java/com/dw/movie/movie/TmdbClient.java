package com.dw.movie.movie;

import com.dw.movie.common.exception.TmdbMovieNotFoundException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

@Component
public class TmdbClient {

    private final RestClient restClient;
    private final String apiKey;

    public TmdbClient(@Value("${tmdb.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.create("https://api.themoviedb.org/3");
    }

    public TmdbMovieInfo fetchMovieInfo(Long tmdbId) {
        MovieDetailResponse detail;
        try {
            detail = restClient.get()
                    .uri("/movie/{id}?api_key={key}&language=ko-KR", tmdbId, apiKey)
                    .retrieve()
                    .body(MovieDetailResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new TmdbMovieNotFoundException("TMDB에서 해당 영화를 찾을 수 없습니다: " + tmdbId);
        }

        String posterUrl = (detail != null && detail.posterPath() != null)
                ? "https://image.tmdb.org/t/p/w500" + detail.posterPath()
                : null;

        String ageRating = fetchAgeRating(tmdbId);

        return new TmdbMovieInfo(
                detail.title(),
                posterUrl,
                detail.overview(),
                detail.releaseDate(),
                detail.runtime(),
                ageRating
        );
    }

    private String fetchAgeRating(Long tmdbId) {
        ReleaseDatesResponse response = restClient.get()
                .uri("/movie/{id}/release_dates?api_key={key}", tmdbId, apiKey)
                .retrieve()
                .body(ReleaseDatesResponse.class);

        if (response == null || response.results() == null) {
            return "정보없음";
        }

        return response.results().stream()
                .filter(country -> "KR".equals(country.iso31661()))
                .flatMap(country -> country.releaseDates().stream())
                .map(ReleaseDateItem::certification)
                .filter(c -> c != null && !c.isBlank())
                .findFirst()
                .orElse("정보없음");
    }

    public record TmdbMovieInfo(
            String title,
            String posterUrl,
            String overview,
            LocalDate releaseDate,
            Integer runningTime,
            String ageRating
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MovieDetailResponse(
            String title,
            String overview,
            @JsonProperty("poster_path") String posterPath,
            @JsonProperty("release_date") LocalDate releaseDate,
            Integer runtime
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReleaseDatesResponse(List<CountryReleaseDates> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CountryReleaseDates(
            @JsonProperty("iso_3166_1") String iso31661,
            @JsonProperty("release_dates") List<ReleaseDateItem> releaseDates
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReleaseDateItem(String certification) {}
}