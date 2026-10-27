package com.dw.movie.movie.dto;

import com.dw.movie.movie.client.TmdbClient;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TmdbSearchResponse {
    private Long tmdbId;
    private String title;
    private String releaseDate;
    private String posterUrl;
    private boolean registered;

    public static TmdbSearchResponse of(TmdbClient.TmdbSearchItem item, boolean registered) {
        return new TmdbSearchResponse(
                item.tmdbId(),
                item.title(),
                item.releaseDate(),
                item.posterUrl(),
                registered
        );
    }
}
