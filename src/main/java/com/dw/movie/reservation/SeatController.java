package com.dw.movie.reservation;

import com.dw.movie.reservation.dto.SeatStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/showtimes")
public class SeatController {

    private final SeatQueryService seatQueryService;

    public SeatController(SeatQueryService seatQueryService) {
        this.seatQueryService = seatQueryService;
    }

    @GetMapping("/{showtimeId}/seats")
    public ResponseEntity<List<SeatStatusResponse>> getSeats(@PathVariable Long showtimeId) {
        return ResponseEntity.ok(seatQueryService.getSeatStatuses(showtimeId));
    }


}

