package com.dw.movie.reservation;

import com.dw.movie.reservation.dto.ReservationConfirmRequest;
import com.dw.movie.reservation.dto.ReservationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService){
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> confirm(@RequestBody ReservationConfirmRequest request,
                                                       @AuthenticationPrincipal Long memberId)
    {
        Reservation reservation = reservationService.confirm(memberId, request.getShowtimeId(),
                request.getSeatIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(reservation));
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getShowtime().getMovie().getTitle(),
                reservation.getShowtime().getScreen().getName(),
                reservation.getShowtime().getStartTime(),
                reservation.getTotalPrice(),
                reservation.getStatus(),
                reservation.getCreatedAt()
        );
    }

    @GetMapping("/me")
    public ResponseEntity<List<ReservationResponse>> getMyReservations(@AuthenticationPrincipal Long memberId){
        List<Reservation> reservations = reservationService.getMyReservations(memberId);
                return ResponseEntity.ok(reservations.stream().map(this::toResponse).toList());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReservation(@PathVariable Long id, @AuthenticationPrincipal Long memberId){
        reservationService.cancel(memberId, id);
        return ResponseEntity.noContent().build();
    }
}
