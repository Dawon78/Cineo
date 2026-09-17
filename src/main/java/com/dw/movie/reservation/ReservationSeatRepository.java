package com.dw.movie.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {
    List<ReservationSeat> findByReservation_Showtime_IdAndReservation_Status(Long showtimeId, ReservationStatus status);
}
