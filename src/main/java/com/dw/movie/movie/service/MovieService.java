package com.dw.movie.movie.service;

import com.dw.movie.common.exception.DuplicateTmdbIdException;
import com.dw.movie.common.exception.MovieNotFoundException;
import com.dw.movie.movie.client.TmdbClient;
import com.dw.movie.movie.dto.TmdbSearchResponse;
import com.dw.movie.movie.entity.Movie;
import com.dw.movie.movie.repository.MovieRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MovieService {

    private final MovieRepository movieRepository;
    private final TmdbClient tmdbClient;

    public MovieService(MovieRepository movieRepository, TmdbClient tmdbClient) {
        this.movieRepository = movieRepository;
        this.tmdbClient = tmdbClient;
    }

    @Transactional
    public Movie registerMovie(Long tmdbId){
        if(movieRepository.existsByTmdbId(tmdbId)) {
            throw new DuplicateTmdbIdException("이미 등록된 영화입니다.");
        }
        TmdbClient.TmdbMovieInfo info = tmdbClient.fetchMovieInfo(tmdbId);
        Movie movie = new Movie(
                tmdbId,
                info.title(),
                info.posterUrl(),
                info.overview(),
                info.releaseDate(),
                info.runningTime(),
                info.ageRating()
        );
        return movieRepository.save(movie);
    }

    public List<TmdbSearchResponse> searchTmdb(String query) {
        List<TmdbClient.TmdbSearchItem> items = tmdbClient.searchMovies(query);

        // 결과마다 조회하지 않고 IN 조회 한 번으로 이미 등록한 영화를 찾는다
        List<Long> tmdbIds = items.stream().map(TmdbClient.TmdbSearchItem::tmdbId).toList();
        Set<Long> registeredIds = movieRepository.findByTmdbIdIn(tmdbIds).stream()
                .map(Movie::getTmdbId)
                .collect(Collectors.toSet());

        return items.stream()
                .map(item -> TmdbSearchResponse.of(item, registeredIds.contains(item.tmdbId())))
                .toList();
    }

    public List<Movie> getAllMovies() {
        return movieRepository.findAll();
    }

    public Movie getMovie(Long id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> new MovieNotFoundException("해당 영화를 찾을 수 없습니다."));
    }
}
