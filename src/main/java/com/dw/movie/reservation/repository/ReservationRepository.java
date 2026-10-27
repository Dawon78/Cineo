package com.dw.movie.reservation.repository;

import com.dw.movie.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByMemberId(Long memberId);

    boolean existsByImpUid(String impUid);

    boolean existsByShowtimeId(Long showtimeId);
}
