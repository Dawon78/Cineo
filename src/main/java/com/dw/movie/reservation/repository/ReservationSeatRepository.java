package com.dw.movie.reservation.repository;

import com.dw.movie.reservation.entity.ReservationStatus;
import com.dw.movie.reservation.entity.ReservationSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {
    List<ReservationSeat> findByReservation_Showtime_IdAndReservation_Status(Long showtimeId, ReservationStatus status);

    boolean existsByReservation_Showtime_IdAndReservation_StatusAndSeat_IdIn(Long showtimeId, ReservationStatus status, Collection<Long> seatIds);
}
