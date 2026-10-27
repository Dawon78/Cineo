package com.dw.movie.showtime.controller;

import com.dw.movie.showtime.service.ShowtimeService;
import com.dw.movie.showtime.dto.ShowtimeRegisterRequest;
import com.dw.movie.showtime.dto.ShowtimeResponse;
import com.dw.movie.showtime.entity.Showtime;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/showtimes")
public class ShowtimeController {

    private final ShowtimeService showtimeService;

    public ShowtimeController(ShowtimeService showtimeService){
        this.showtimeService = showtimeService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ShowtimeResponse> register(@Valid @RequestBody ShowtimeRegisterRequest request) {
        Showtime showtime = showtimeService.createShowtime(
                request.getMovieId(),
                request.getScreenId(),
                request.getFormat(),
                request.getStartTime(),
                request.getBasePrice()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ShowtimeResponse.from(showtime));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<ShowtimeResponse>> getAll() {
        List<ShowtimeResponse> responses = showtimeService.getAllShowtimes().stream()
                .map(ShowtimeResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        showtimeService.deleteShowtime(id);
        return ResponseEntity.noContent().build();
    }
}