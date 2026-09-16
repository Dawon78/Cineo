package com.dw.movie.movie;

import com.dw.movie.movie.dto.MovieRegisterRequest;
import com.dw.movie.movie.dto.MovieResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequestMapping("/api/admin/movies")
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<MovieResponse> register(@RequestBody MovieRegisterRequest request) {
        Movie movie = movieService.registerMovie(request.getTmdbId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(movie));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<MovieResponse>> getAll() {
        List<MovieResponse> responses = movieService.getAllMovies().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<MovieResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(movieService.getMovie(id)));
    }

    private MovieResponse toResponse(Movie movie) {
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