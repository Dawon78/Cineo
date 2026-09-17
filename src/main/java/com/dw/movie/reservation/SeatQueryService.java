package com.dw.movie.reservation;

import com.dw.movie.common.exception.ShowtimeNotFoundException;
import com.dw.movie.reservation.dto.SeatStatusResponse;
import com.dw.movie.screen.Seat;
import com.dw.movie.screen.SeatRepository;
import com.dw.movie.showtime.Showtime;
import com.dw.movie.showtime.ShowtimeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SeatQueryService {

    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private final SeatHoldService seatHoldService;

    public SeatQueryService(ShowtimeRepository showtimeRepository, SeatRepository seatRepository,
                            ReservationSeatRepository reservationSeatRepository, SeatHoldService seatHoldService) {
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
        this.reservationSeatRepository = reservationSeatRepository;
        this.seatHoldService = seatHoldService;
    }

    public List<SeatStatusResponse> getSeatStatuses(Long showtimeId) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ShowtimeNotFoundException("해당 상영시간표를 찾을 수 없습니다."));

        List<Seat> screenSeats = seatRepository.findAll().stream()
                .filter(seat -> seat.getScreen().getId().equals(showtime.getScreen().getId()))
                .toList();

        Set<Long> confirmedSeatIds = reservationSeatRepository
                .findByReservation_Showtime_IdAndReservation_Status(showtimeId, ReservationStatus.CONFIRMED)
                .stream()
                .map(reservationSeat -> reservationSeat.getSeat().getId())
                .collect(Collectors.toSet());

        return screenSeats.stream()
                .map(seat -> new SeatStatusResponse(
                        seat.getId(),
                        seat.getRowLabel(),
                        seat.getSeatNumber(),
                        seat.getGrade(),
                        resolveStatus(showtimeId, seat, confirmedSeatIds)
                ))
                .toList();
    }

    private SeatStatus resolveStatus(Long showtimeId, Seat seat, Set<Long> confirmedSeatIds) {
        if (confirmedSeatIds.contains(seat.getId())) {
            return SeatStatus.CONFIRMED;
        }
        if (seatHoldService.isHeld(showtimeId, seat.getId())) {
            return SeatStatus.HELD;
        }
        return SeatStatus.AVAILABLE;
    }
}