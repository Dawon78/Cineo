package com.dw.movie.movie.client;

import com.dw.movie.common.exception.TmdbMovieNotFoundException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Component
public class TmdbClient {

    private final RestClient restClient;
    private final String apiKey;

    public TmdbClient(@Value("${tmdb.api-key}") String apiKey) {
        this.apiKey = apiKey;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl("https://api.themoviedb.org/3")
                .requestFactory(requestFactory)
                .build();
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
        if (detail == null) {
            throw new TmdbMovieNotFoundException("TMDB에서 해당 영화를 찾을 수 없습니다: " + tmdbId);
        }

        String posterUrl = posterUrl(detail.posterPath());

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

    // 첫 페이지(최대 20개)만 쓴다
    public List<TmdbSearchItem> searchMovies(String query) {
        SearchResponse response = restClient.get()
                .uri("/search/movie?api_key={key}&language=ko-KR&include_adult=false&query={query}", apiKey, query)
                .retrieve()
                .body(SearchResponse.class);

        if (response == null || response.results() == null) {
            return List.of();
        }

        return response.results().stream()
                .map(result -> new TmdbSearchItem(
                        result.id(),
                        result.title(),
                        result.releaseDate(),
                        posterUrl(result.posterPath())
                ))
                .toList();
    }

    private String posterUrl(String posterPath) {
        return posterPath != null ? "https://image.tmdb.org/t/p/w500" + posterPath : null;
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

    public record TmdbSearchItem(
            Long tmdbId,
            String title,
            String releaseDate,
            String posterUrl
    ) {}

    // 검색 결과는 개봉일이 없으면 null이 아니라 ""를 준다. LocalDate로 받으면 실패하니 문자열로 받는다
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResponse(List<SearchResult> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResult(
            Long id,
            String title,
            @JsonProperty("poster_path") String posterPath,
            @JsonProperty("release_date") String releaseDate
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