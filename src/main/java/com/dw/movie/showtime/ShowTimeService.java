package com.dw.movie.showtime;

import com.dw.movie.common.exception.MovieNotFoundException;
import com.dw.movie.common.exception.ScreenNotFoundException;
import com.dw.movie.movie.Movie;
import com.dw.movie.movie.MovieRepository;
import com.dw.movie.screen.Screen;
import com.dw.movie.screen.ScreenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShowTimeService {

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final ScreenRepository screenRepository;

    public ShowTimeService(ShowtimeRepository showtimeRepository, MovieRepository movieRepository, ScreenRepository screenRepository) {
        this.showtimeRepository = showtimeRepository;
        this.movieRepository = movieRepository;
        this.screenRepository = screenRepository;
    }

    @Transactional
    public Showtime createShowtime(Long movieId, Long screenId, ShowtimeFormat format, LocalDateTime startTime, Long basePrice) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException("해당 영화를 찾을 수 없습니다."));

        Screen screen = screenRepository.findById(screenId)
                .orElseThrow(() -> new ScreenNotFoundException("해당 상영관을 찾을 수 없습니다."));

        LocalDateTime endTime = startTime.plusMinutes(movie.getRunningTime());

        Showtime showtime = new Showtime(movie, screen, format, startTime, endTime, basePrice);

        return showtimeRepository.save(showtime);
    }

    public List<Showtime> getAllShowtimes() {
        return showtimeRepository.findAll();
    }
}