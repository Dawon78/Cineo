package com.dw.movie.showtime;

import com.dw.movie.common.exception.InvalidShowtimeException;
import com.dw.movie.movie.entity.Movie;
import com.dw.movie.movie.repository.MovieRepository;
import com.dw.movie.screen.entity.Screen;
import com.dw.movie.screen.repository.ScreenRepository;
import com.dw.movie.showtime.entity.ShowtimeFormat;
import com.dw.movie.showtime.repository.ShowtimeRepository;
import com.dw.movie.showtime.service.ShowtimeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShowtimeServiceTest {

    @Mock ShowtimeRepository showtimeRepository;
    @Mock MovieRepository movieRepository;
    @Mock ScreenRepository screenRepository;

    @InjectMocks ShowtimeService showtimeService;

    @Test
    void 러닝타임이_없는_영화는_상영시간을_등록할_수_없다() {
        Movie movie = new Movie(1L, "개봉 예정작", null, null, null, null, "정보없음");
        given(movieRepository.findById(1L)).willReturn(Optional.of(movie));
        given(screenRepository.findById(1L)).willReturn(Optional.of(new Screen("1관")));

        assertThrows(InvalidShowtimeException.class,
                () -> showtimeService.createShowtime(1L, 1L, ShowtimeFormat.TWO_D, LocalDateTime.now().plusDays(1), 12000L));

        verify(showtimeRepository, never()).save(any());
    }
}
