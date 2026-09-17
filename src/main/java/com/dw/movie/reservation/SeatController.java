package com.dw.movie.reservation;

import com.dw.movie.common.exception.ShowtimeAlreadyStartedException;
import com.dw.movie.common.exception.ShowtimeNotFoundException;
import com.dw.movie.reservation.dto.SeatHoldRequest;
import com.dw.movie.reservation.dto.SeatStatusResponse;
import com.dw.movie.showtime.Showtime;
import com.dw.movie.showtime.ShowtimeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/showtimes")
public class SeatController {

    private final SeatQueryService seatQueryService;
    private final SeatHoldService seatHoldService;
    private final ShowtimeRepository showtimeRepository;

    public SeatController(SeatQueryService seatQueryService, SeatHoldService seatHoldService, ShowtimeRepository showtimeRepository) {
        this.seatQueryService = seatQueryService;
        this.seatHoldService = seatHoldService;
        this.showtimeRepository = showtimeRepository;
    }

    @GetMapping("/{showtimeId}/seats")
    public ResponseEntity<List<SeatStatusResponse>> getSeats(@PathVariable Long showtimeId) {
        return ResponseEntity.ok(seatQueryService.getSeatStatuses(showtimeId));
    }

    @PostMapping("/{showtimeId}/seats/hold")
    public ResponseEntity<Void> holdSeats(@PathVariable Long showtimeId, @RequestBody SeatHoldRequest request,
                                          @AuthenticationPrincipal Long memberId) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ShowtimeNotFoundException("해당 상영시간표를 찾을 수 없습니다."));
        if (showtime.getStartTime().isBefore(LocalDateTime.now())) {
            throw new ShowtimeAlreadyStartedException("이미 시작된 상영시간표입니다.");
        }

        seatHoldService.hold(showtimeId, request.getSeatIds(), memberId);
        return ResponseEntity.ok().build();
    }
}