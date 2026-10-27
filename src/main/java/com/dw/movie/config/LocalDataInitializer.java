package com.dw.movie.config;

import com.dw.movie.movie.entity.Movie;
import com.dw.movie.movie.repository.MovieRepository;
import com.dw.movie.movie.service.MovieService;
import com.dw.movie.screen.entity.Screen;
import com.dw.movie.screen.repository.ScreenRepository;
import com.dw.movie.screen.service.ScreenService;
import com.dw.movie.showtime.entity.ShowtimeFormat;
import com.dw.movie.showtime.repository.ShowtimeRepository;
import com.dw.movie.showtime.service.ShowtimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
@Profile("local")
public class LocalDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDataInitializer.class);

    // 인셉션, 인터스텔라, 듄, 기생충, 다크 나이트, 어벤져스: 엔드게임, 듄: 파트 2, 오펜하이머,
    // 센과 치히로의 행방불명, 너의 이름은., 코코, 스파이더맨: 어크로스 더 유니버스
    private static final List<Long> TMDB_IDS = List.of(
            27205L, 157336L, 438631L, 496243L, 155L, 299534L,
            693134L, 872585L, 129L, 372058L, 354912L, 569094L);
    private static final List<LocalTime> START_TIMES = List.of(
            LocalTime.of(10, 0), LocalTime.of(14, 0), LocalTime.of(19, 0));
    private static final int DAYS = 3;

    private final MovieService movieService;
    private final MovieRepository movieRepository;
    private final ScreenService screenService;
    private final ScreenRepository screenRepository;
    private final ShowtimeService showtimeService;
    private final ShowtimeRepository showtimeRepository;

    public LocalDataInitializer(MovieService movieService, MovieRepository movieRepository,
                                ScreenService screenService, ScreenRepository screenRepository,
                                ShowtimeService showtimeService, ShowtimeRepository showtimeRepository) {
        this.movieService = movieService;
        this.movieRepository = movieRepository;
        this.screenService = screenService;
        this.screenRepository = screenRepository;
        this.showtimeService = showtimeService;
        this.showtimeRepository = showtimeRepository;
    }

    @Override
    public void run(String... args) {
        for (Long tmdbId : TMDB_IDS) {
            if (!movieRepository.existsByTmdbId(tmdbId)) {
                try {
                    movieService.registerMovie(tmdbId);
                } catch (RuntimeException e) {
                    log.warn("TMDB 영화 등록 실패, 건너뜀 (tmdbId={}): {}", tmdbId, e.getMessage());
                }
            }
        }
        List<Movie> movies = movieRepository.findAll(Sort.by("id"));

        List<Screen> screens = screenRepository.findAll(Sort.by("id"));
        for (int i = screens.size(); i < movies.size(); i++) {
            screenService.createScreen((i + 1) + "관", 8, 10);
        }
        screens = screenRepository.findAll(Sort.by("id"));

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        for (int i = 0; i < movies.size(); i++) {
            Movie movie = movies.get(i);
            // 영화별로 판단한다. 이미 내일 이후 상영이 있는 영화는 건너뛰고, 새로 추가된 영화만 상영을 만든다
            if (showtimeRepository.existsByMovieIdAndStartTimeAfter(movie.getId(), tomorrow.atStartOfDay())) {
                continue;
            }
            Screen screen = screens.get(i);
            try {
                for (int day = 0; day < DAYS; day++) {
                    for (LocalTime time : START_TIMES) {
                        LocalDateTime startTime = LocalDateTime.of(tomorrow.plusDays(day), time);
                        showtimeService.createShowtime(movie.getId(), screen.getId(),
                                ShowtimeFormat.TWO_D, startTime, 12000L);
                    }
                }
            } catch (RuntimeException e) {
                // 러닝타임이 없는 영화 등은 상영을 만들 수 없다. 그 영화만 건너뛴다
                log.warn("상영시간 생성 실패, 건너뜀 (movieId={}): {}", movie.getId(), e.getMessage());
            }
        }
    }
}