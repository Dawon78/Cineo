package com.dw.movie.config;

import com.dw.movie.movie.entity.Movie;
import com.dw.movie.movie.repository.MovieRepository;
import com.dw.movie.movie.service.MovieService;
import com.dw.movie.screen.entity.Screen;
import com.dw.movie.screen.repository.ScreenRepository;
import com.dw.movie.screen.service.ScreenService;
import com.dw.movie.showtime.entity.ShowtimeFormat;
import com.dw.movie.showtime.repository.ShowtimeRepository;
import com.dw.movie.showtime.service.ShowTimeService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
@Profile("local")
public class LocalDataInitializer implements CommandLineRunner {

    private static final List<Long> TMDB_IDS = List.of(27205L, 157336L, 438631L);
    private static final List<LocalTime> START_TIMES = List.of(
            LocalTime.of(10, 0), LocalTime.of(14, 0), LocalTime.of(19, 0));
    private static final int DAYS = 3;

    private final MovieService movieService;
    private final MovieRepository movieRepository;
    private final ScreenService screenService;
    private final ScreenRepository screenRepository;
    private final ShowTimeService showTimeService;
    private final ShowtimeRepository showtimeRepository;

    public LocalDataInitializer(MovieService movieService, MovieRepository movieRepository,
                                ScreenService screenService, ScreenRepository screenRepository,
                                ShowTimeService showTimeService, ShowtimeRepository showtimeRepository) {
        this.movieService = movieService;
        this.movieRepository = movieRepository;
        this.screenService = screenService;
        this.screenRepository = screenRepository;
        this.showTimeService = showTimeService;
        this.showtimeRepository = showtimeRepository;
    }

    @Override
    public void run(String... args) {
        if (showtimeRepository.existsByStartTimeAfter(LocalDateTime.now())) {
            return;
        }

        for (Long tmdbId : TMDB_IDS) {
            if (!movieRepository.existsByTmdbId(tmdbId)) {
                movieService.registerMovie(tmdbId);
            }
        }
        List<Movie> movies = movieRepository.findAll();

        List<Screen> screens = screenRepository.findAll();
        for (int i = screens.size(); i < movies.size(); i++) {
            screenService.createScreen((i + 1) + "관", 8, 10);
        }
        screens = screenRepository.findAll();

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        for (int i = 0; i < movies.size(); i++) {
            Movie movie = movies.get(i);
            Screen screen = screens.get(i);
            for (int day = 0; day < DAYS; day++) {
                for (LocalTime time : START_TIMES) {
                    LocalDateTime startTime = LocalDateTime.of(tomorrow.plusDays(day), time);
                    showTimeService.createShowtime(movie.getId(), screen.getId(),
                            ShowtimeFormat.TWO_D, startTime, 12000L);
                }
            }
        }
    }
}