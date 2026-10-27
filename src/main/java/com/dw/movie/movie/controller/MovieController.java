package com.dw.movie.movie.controller;

import com.dw.movie.movie.dto.MovieRegisterRequest;
import com.dw.movie.movie.dto.MovieResponse;
import com.dw.movie.movie.dto.TmdbSearchResponse;
import com.dw.movie.movie.entity.Movie;
import com.dw.movie.movie.service.MovieService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
    public ResponseEntity<MovieResponse> register(@Valid @RequestBody MovieRegisterRequest request) {
        Movie movie = movieService.registerMovie(request.getTmdbId());
        return ResponseEntity.status(HttpStatus.CREATED).body(MovieResponse.from(movie));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<MovieResponse>> getAll() {
        List<MovieResponse> responses = movieService.getAllMovies().stream()
                .map(MovieResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/search")
    public ResponseEntity<List<TmdbSearchResponse>> search(@RequestParam @NotBlank String query) {
        return ResponseEntity.ok(movieService.searchTmdb(query.trim()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<MovieResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(MovieResponse.from(movieService.getMovie(id)));
    }
}