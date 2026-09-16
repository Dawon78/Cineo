package com.dw.movie.movie;

import com.dw.movie.common.exception.DuplicateTmdbIdException;
import com.dw.movie.common.exception.MovieNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public List<Movie> getAllMovies() {
        return movieRepository.findAll();
    }

    public Movie getMovie(Long id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> new MovieNotFoundException("해당 영화를 찾을 수 없습니다."));
    }
}
