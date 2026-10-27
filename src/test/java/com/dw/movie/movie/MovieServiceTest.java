package com.dw.movie.movie;

import com.dw.movie.movie.client.TmdbClient;
import com.dw.movie.movie.dto.TmdbSearchResponse;
import com.dw.movie.movie.entity.Movie;
import com.dw.movie.movie.repository.MovieRepository;
import com.dw.movie.movie.service.MovieService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @Mock MovieRepository movieRepository;
    @Mock TmdbClient tmdbClient;

    @InjectMocks MovieService movieService;

    @Test
    void TMDB_검색_결과에_이미_등록한_영화를_표시한다() {
        given(tmdbClient.searchMovies("인셉션")).willReturn(List.of(
                new TmdbClient.TmdbSearchItem(27205L, "인셉션", "2010-07-15", null),
                new TmdbClient.TmdbSearchItem(99999L, "인셉션 메이킹", "", null)
        ));
        given(movieRepository.findByTmdbIdIn(List.of(27205L, 99999L))).willReturn(List.of(
                new Movie(27205L, "인셉션", null, null, null, 148, "12")
        ));

        List<TmdbSearchResponse> results = movieService.searchTmdb("인셉션");

        assertTrue(results.get(0).isRegistered());
        assertFalse(results.get(1).isRegistered());
    }
}
