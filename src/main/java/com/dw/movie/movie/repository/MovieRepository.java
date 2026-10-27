package com.dw.movie.movie.repository;

import com.dw.movie.movie.entity.Movie;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    boolean existsByTmdbId(Long tmdbId);

    Optional<Movie> findByTmdbId(Long tmdbId);

    List<Movie> findByTmdbIdIn(Collection<Long> tmdbIds);
}
