package com.dw.movie.showtime.controller;

import com.dw.movie.showtime.dto.ShowtimeResponse;
import com.dw.movie.showtime.service.ShowTimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/movies/{movieId}/showtimes")
public class ShowtimePublicController {

    private final ShowTimeService showTimeService;

    public ShowtimePublicController(ShowTimeService showTimeService) {
        this.showTimeService = showTimeService;
    }

    @GetMapping
    public ResponseEntity<List<ShowtimeResponse>> getUpcoming(@PathVariable Long movieId) {
        List<ShowtimeResponse> responses = showTimeService.getUpcomingShowtimes(movieId).stream()
                .map(ShowtimeResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }
}