package com.dw.movie.showtime;

import com.dw.movie.showtime.dto.ShowtimeRegisterRequest;
import com.dw.movie.showtime.dto.ShowtimeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/showtimes")
public class ShowtimeController {

    private final ShowTimeService showTimeService;

    public ShowtimeController(ShowTimeService showTimeService){
        this.showTimeService = showTimeService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ShowtimeResponse> register(@RequestBody ShowtimeRegisterRequest request) {
        Showtime showtime = showTimeService.createShowtime(
                request.getMovieId(),
                request.getScreenId(),
                request.getFormat(),
                request.getStartTime(),
                request.getBasePrice()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(showtime));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<ShowtimeResponse>> getAll() {
        List<ShowtimeResponse> responses = showTimeService.getAllShowtimes().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    private ShowtimeResponse toResponse(Showtime showtime) {
        return new ShowtimeResponse(
                showtime.getId(),
                showtime.getMovie().getTitle(),
                showtime.getScreen().getName(),
                showtime.getFormat(),
                showtime.getStartTime(),
                showtime.getEndTime(),
                showtime.getBasePrice()
        );
    }
}