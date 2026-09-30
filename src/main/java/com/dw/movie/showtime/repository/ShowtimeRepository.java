package com.dw.movie.showtime.repository;

import com.dw.movie.showtime.entity.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {
}
