package com.dw.movie.showtime.controller;

import com.dw.movie.showtime.dto.ShowtimeResponse;
import com.dw.movie.showtime.service.ShowtimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/movies/{movieId}/showtimes")
public class ShowtimePublicController {

    private final ShowtimeService showtimeService;

    public ShowtimePublicController(ShowtimeService showtimeService) {
        this.showtimeService = showtimeService;
    }

    @GetMapping
    public ResponseEntity<List<ShowtimeResponse>> getUpcoming(@PathVariable Long movieId) {
        List<ShowtimeResponse> responses = showtimeService.getUpcomingShowtimes(movieId).stream()
                .map(ShowtimeResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }
}