package com.dw.movie.reservation.controller;

import com.dw.movie.reservation.service.SeatHoldService;
import com.dw.movie.reservation.service.SeatQueryService;
import com.dw.movie.reservation.dto.SeatHoldRequest;
import com.dw.movie.reservation.dto.SeatPriceResponse;
import com.dw.movie.reservation.dto.SeatStatusResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/showtimes")
public class SeatController {

    private final SeatQueryService seatQueryService;
    private final SeatHoldService seatHoldService;

    public SeatController(SeatQueryService seatQueryService, SeatHoldService seatHoldService) {
        this.seatQueryService = seatQueryService;
        this.seatHoldService = seatHoldService;
    }

    @GetMapping("/{showtimeId}/seats")
    public ResponseEntity<List<SeatStatusResponse>> getSeats(@PathVariable Long showtimeId) {
        return ResponseEntity.ok(seatQueryService.getSeatStatuses(showtimeId));
    }

    @PostMapping("/{showtimeId}/seats/hold")
    public ResponseEntity<Void> holdSeats(@PathVariable Long showtimeId, @Valid @RequestBody SeatHoldRequest request,
                                          @AuthenticationPrincipal Long memberId) {
        seatQueryService.validateHoldable(showtimeId, request.getSeatIds());
        seatHoldService.hold(showtimeId, request.getSeatIds(), memberId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{showtimeId}/seats/hold")
    public ResponseEntity<Void> deleteSeats(@PathVariable Long showtimeId, @Valid @RequestBody SeatHoldRequest request,
                                            @AuthenticationPrincipal Long memberId) {
        seatHoldService.release(showtimeId, request.getSeatIds(), memberId);
        return ResponseEntity.ok().build();

    }

    @GetMapping("/{showtimeId}/seats/price")
    public ResponseEntity<SeatPriceResponse> getPrice(@PathVariable Long showtimeId,
                                                      @RequestParam @NotEmpty @Size(max = 10) List<Long> seatIds) {
        Long totalPrice = seatQueryService.calculateTotalPrice(showtimeId, seatIds);
        return ResponseEntity.ok(new SeatPriceResponse(totalPrice));
    }
}
