package com.dw.movie.showtime;

import com.dw.movie.common.exception.InvalidShowtimeException;
import com.dw.movie.common.exception.ShowtimeNotDeletableException;
import com.dw.movie.movie.entity.Movie;
import com.dw.movie.movie.repository.MovieRepository;
import com.dw.movie.reservation.repository.ReservationRepository;
import com.dw.movie.reservation.service.SeatHoldService;
import com.dw.movie.screen.entity.Screen;
import com.dw.movie.screen.entity.Seat;
import com.dw.movie.screen.entity.SeatGrade;
import com.dw.movie.screen.repository.ScreenRepository;
import com.dw.movie.screen.repository.SeatRepository;
import com.dw.movie.showtime.entity.Showtime;
import com.dw.movie.showtime.entity.ShowtimeFormat;
import com.dw.movie.showtime.repository.ShowtimeRepository;
import com.dw.movie.showtime.service.ShowtimeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShowtimeServiceTest {

    @Mock ShowtimeRepository showtimeRepository;
    @Mock MovieRepository movieRepository;
    @Mock ScreenRepository screenRepository;
    @Mock SeatRepository seatRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock SeatHoldService seatHoldService;

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

    @Test
    void 예매가_있는_상영시간은_삭제할_수_없다() {
        Showtime showtime = showtime();
        given(showtimeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(showtime));
        given(reservationRepository.existsByShowtimeId(1L)).willReturn(true);

        assertThrows(ShowtimeNotDeletableException.class, () -> showtimeService.deleteShowtime(1L));

        verify(showtimeRepository, never()).delete(any());
    }

    @Test
    void 좌석을_선점_중인_상영시간은_삭제할_수_없다() {
        Showtime showtime = showtime();
        given(showtimeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(showtime));
        given(reservationRepository.existsByShowtimeId(1L)).willReturn(false);
        given(seatRepository.findByScreen_Id(any())).willReturn(List.of(new Seat(showtime.getScreen(), "A", 1, SeatGrade.STANDARD)));
        given(seatHoldService.hasAnyHold(eq(1L), anyList())).willReturn(true);

        assertThrows(ShowtimeNotDeletableException.class, () -> showtimeService.deleteShowtime(1L));

        verify(showtimeRepository, never()).delete(any());
    }

    @Test
    void 예매도_선점도_없으면_상영시간이_삭제된다() {
        Showtime showtime = showtime();
        given(showtimeRepository.findByIdForUpdate(1L)).willReturn(Optional.of(showtime));
        given(reservationRepository.existsByShowtimeId(1L)).willReturn(false);
        given(seatRepository.findByScreen_Id(any())).willReturn(List.of(new Seat(showtime.getScreen(), "A", 1, SeatGrade.STANDARD)));
        given(seatHoldService.hasAnyHold(eq(1L), anyList())).willReturn(false);

        showtimeService.deleteShowtime(1L);

        verify(showtimeRepository).delete(showtime);
    }

    private Showtime showtime() {
        Movie movie = new Movie(1L, "테스트 영화", null, null, null, 120, "12");
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        return new Showtime(movie, new Screen("1관"), ShowtimeFormat.TWO_D, start, start.plusMinutes(120), 12000L);
    }
}
