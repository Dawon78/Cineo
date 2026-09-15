package com.dw.movie.movie;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    boolean existsByTmdbId(Long tmdbId);

    Optional<Movie> findByTmdbId(Long tmdbId);
}
