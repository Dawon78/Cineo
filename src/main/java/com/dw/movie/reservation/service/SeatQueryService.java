package com.dw.movie.reservation.service;

import com.dw.movie.common.exception.InvalidSeatSelectionException;
import com.dw.movie.common.exception.SeatAlreadyReservedException;
import com.dw.movie.common.exception.ShowtimeNotFoundException;
import com.dw.movie.reservation.entity.SeatStatus;
import com.dw.movie.reservation.dto.SeatPriceCalculator;
import com.dw.movie.reservation.dto.SeatStatusResponse;
import com.dw.movie.reservation.entity.ReservationStatus;
import com.dw.movie.reservation.repository.ReservationSeatRepository;
import com.dw.movie.screen.entity.Seat;
import com.dw.movie.screen.repository.SeatRepository;
import com.dw.movie.showtime.entity.Showtime;
import com.dw.movie.showtime.repository.ShowtimeRepository;
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

        List<Seat> screenSeats = seatRepository.findByScreen_Id(showtime.getScreen().getId());

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

    public List<Seat> validateSelectable(Showtime showtime, List<Long> seatIds) {
        List<Long> distinctSeatIds = seatIds.stream().distinct().toList();
        if (distinctSeatIds.size() != seatIds.size()) {
            throw new InvalidSeatSelectionException("중복된 좌석이 포함되어 있습니다.");
        }

        List<Seat> seats = seatRepository.findAllById(distinctSeatIds);
        if (seats.size() != distinctSeatIds.size()) {
            throw new InvalidSeatSelectionException("존재하지 않는 좌석이 포함되어 있습니다.");
        }

        Long screenId = showtime.getScreen().getId();
        boolean fromOtherScreen = seats.stream().anyMatch(seat -> !seat.getScreen().getId().equals(screenId));
        if (fromOtherScreen) {
            throw new InvalidSeatSelectionException("해당 상영관의 좌석이 아닙니다.");
        }

        boolean alreadyReserved = reservationSeatRepository
                .existsByReservation_Showtime_IdAndReservation_StatusAndSeat_IdIn(showtime.getId(), ReservationStatus.CONFIRMED, distinctSeatIds);
        if (alreadyReserved) {
            throw new SeatAlreadyReservedException("이미 예약된 좌석이 포함되어 있습니다.");
        }

        return seats;
    }

    public Long calculateTotalPrice(Long showtimeId, List<Long> seatIds){
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(()-> new ShowtimeNotFoundException("해당 상영시간표를 찾을 수 없습니다."));
        List<Seat> seats = seatRepository.findAllById(seatIds);

        long totalPrice = 0L;
        for (Seat seat : seats) {
            totalPrice += SeatPriceCalculator.calculate(showtime.getBasePrice(), seat.getGrade(), showtime.getFormat());
        }
            return totalPrice;
        }
    }
