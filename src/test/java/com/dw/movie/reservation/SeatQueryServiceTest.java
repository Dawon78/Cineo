package com.dw.movie.reservation;

import com.dw.movie.common.exception.InvalidSeatSelectionException;
import com.dw.movie.common.exception.SeatAlreadyReservedException;
import com.dw.movie.reservation.entity.ReservationStatus;
import com.dw.movie.reservation.repository.ReservationSeatRepository;
import com.dw.movie.reservation.service.SeatHoldService;
import com.dw.movie.reservation.service.SeatQueryService;
import com.dw.movie.screen.entity.Screen;
import com.dw.movie.screen.entity.Seat;
import com.dw.movie.screen.entity.SeatGrade;
import com.dw.movie.screen.repository.SeatRepository;
import com.dw.movie.showtime.entity.Showtime;
import com.dw.movie.showtime.entity.ShowtimeFormat;
import com.dw.movie.showtime.repository.ShowtimeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SeatQueryServiceTest {

    @Mock ShowtimeRepository showtimeRepository;
    @Mock SeatRepository seatRepository;
    @Mock ReservationSeatRepository reservationSeatRepository;
    @Mock SeatHoldService seatHoldService;

    @InjectMocks SeatQueryService seatQueryService;

    private Screen screen1;
    private Screen screen2;
    private Showtime showtime;

    @BeforeEach
    void setUp() {
        screen1 = screenWithId(1L);
        screen2 = screenWithId(2L);
        showtime = new Showtime(null, screen1, ShowtimeFormat.TWO_D, LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusMinutes(120), 10000L);
        ReflectionTestUtils.setField(showtime, "id", 1L);
    }

    private Screen screenWithId(Long id) {
        Screen screen = new Screen("관");
        ReflectionTestUtils.setField(screen, "id", id);
        return screen;
    }

    private Seat seatWithId(Long id, Screen screen) {
        Seat seat = new Seat(screen, "A", 1, SeatGrade.STANDARD);
        ReflectionTestUtils.setField(seat, "id", id);
        return seat;
    }

    @Test
    void 정상_좌석이면_좌석목록을_돌려준다() {
        Seat seat = seatWithId(5L, screen1);
        given(seatRepository.findAllById(List.of(5L))).willReturn(List.of(seat));
        given(reservationSeatRepository.existsByReservation_Showtime_IdAndReservation_StatusAndSeat_IdIn(
                1L, ReservationStatus.CONFIRMED, List.of(5L))).willReturn(false);

        List<Seat> result = seatQueryService.validateSelectable(showtime, List.of(5L));

        assertEquals(List.of(seat), result);
    }

    @Test
    void 이미_확정된_좌석은_선택할_수_없다() {
        Seat seat = seatWithId(5L, screen1);
        given(seatRepository.findAllById(List.of(5L))).willReturn(List.of(seat));
        given(reservationSeatRepository.existsByReservation_Showtime_IdAndReservation_StatusAndSeat_IdIn(
                1L, ReservationStatus.CONFIRMED, List.of(5L))).willReturn(true);

        assertThrows(SeatAlreadyReservedException.class,
                () -> seatQueryService.validateSelectable(showtime, List.of(5L)));
    }

    @Test
    void 다른_상영관의_좌석은_선택할_수_없다() {
        Seat seat = seatWithId(5L, screen2);
        given(seatRepository.findAllById(List.of(5L))).willReturn(List.of(seat));

        assertThrows(InvalidSeatSelectionException.class,
                () -> seatQueryService.validateSelectable(showtime, List.of(5L)));
    }

    @Test
    void 존재하지_않는_좌석이_섞여있으면_선택할_수_없다() {
        given(seatRepository.findAllById(List.of(5L, 6L))).willReturn(List.of(seatWithId(5L, screen1)));

        assertThrows(InvalidSeatSelectionException.class,
                () -> seatQueryService.validateSelectable(showtime, List.of(5L, 6L)));
    }

    @Test
    void 같은_좌석을_중복으로_보내면_선택할_수_없다() {
        assertThrows(InvalidSeatSelectionException.class,
                () -> seatQueryService.validateSelectable(showtime, List.of(5L, 5L)));
    }
}
