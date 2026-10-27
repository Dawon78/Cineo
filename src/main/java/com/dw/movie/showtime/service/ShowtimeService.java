package com.dw.movie.showtime.service;

import com.dw.movie.common.exception.InvalidShowtimeException;
import com.dw.movie.common.exception.MovieNotFoundException;
import com.dw.movie.common.exception.ScreenNotFoundException;
import com.dw.movie.common.exception.ShowtimeNotDeletableException;
import com.dw.movie.common.exception.ShowtimeNotFoundException;
import com.dw.movie.movie.entity.Movie;
import com.dw.movie.movie.repository.MovieRepository;
import com.dw.movie.reservation.repository.ReservationRepository;
import com.dw.movie.reservation.service.SeatHoldService;
import com.dw.movie.screen.entity.Screen;
import com.dw.movie.screen.entity.Seat;
import com.dw.movie.screen.repository.ScreenRepository;
import com.dw.movie.screen.repository.SeatRepository;
import com.dw.movie.showtime.entity.Showtime;
import com.dw.movie.showtime.entity.ShowtimeFormat;
import com.dw.movie.showtime.repository.ShowtimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShowtimeService {

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final ScreenRepository screenRepository;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final SeatHoldService seatHoldService;

    public ShowtimeService(ShowtimeRepository showtimeRepository, MovieRepository movieRepository, ScreenRepository screenRepository,
                           SeatRepository seatRepository, ReservationRepository reservationRepository, SeatHoldService seatHoldService) {
        this.showtimeRepository = showtimeRepository;
        this.movieRepository = movieRepository;
        this.screenRepository = screenRepository;
        this.seatRepository = seatRepository;
        this.reservationRepository = reservationRepository;
        this.seatHoldService = seatHoldService;
    }

    @Transactional
    public Showtime createShowtime(Long movieId, Long screenId, ShowtimeFormat format, LocalDateTime startTime, Long basePrice) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new MovieNotFoundException("해당 영화를 찾을 수 없습니다."));

        Screen screen = screenRepository.findById(screenId)
                .orElseThrow(() -> new ScreenNotFoundException("해당 상영관을 찾을 수 없습니다."));

        // 개봉 전 영화는 TMDB에 러닝타임이 비어 있을 수 있다. 종료 시간을 계산할 수 없으니 등록을 막는다
        Integer runningTime = movie.getRunningTime();
        if (runningTime == null || runningTime <= 0) {
            throw new InvalidShowtimeException("러닝타임 정보가 없는 영화는 상영시간을 등록할 수 없습니다.");
        }
        LocalDateTime endTime = startTime.plusMinutes(runningTime);

        Showtime showtime = new Showtime(movie, screen, format, startTime, endTime, basePrice);

        return showtimeRepository.save(showtime);
    }

    @Transactional
    public void deleteShowtime(Long id) {
        // 예매 확정도 같은 행에 같은 락을 건다. 확정과 삭제가 동시에 와도 하나씩 처리된다
        Showtime showtime = showtimeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ShowtimeNotFoundException("해당 상영시간표를 찾을 수 없습니다."));

        // 취소된 예매도 이 상영을 참조하고, 결제·환불 이력도 남아야 한다
        if (reservationRepository.existsByShowtimeId(id)) {
            throw new ShowtimeNotDeletableException("예매 기록이 있는 상영시간은 삭제할 수 없습니다.");
        }

        // 좌석을 잡고 결제 중인 사람이 있으면, 지운 뒤 확정에서 실패해도 자동 환불이 되지 않는다
        List<Long> seatIds = seatRepository.findByScreen_Id(showtime.getScreen().getId()).stream()
                .map(Seat::getId)
                .toList();
        if (seatHoldService.hasAnyHold(id, seatIds)) {
            throw new ShowtimeNotDeletableException("좌석을 선점 중인 사용자가 있어 삭제할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }

        showtimeRepository.delete(showtime);
    }

    public List<Showtime> getAllShowtimes() {
        return showtimeRepository.findAll();
    }

    public List<Showtime> getUpcomingShowtimes(Long movieId) {
        return showtimeRepository.findByMovieIdAndStartTimeAfterOrderByStartTimeAsc(movieId, LocalDateTime.now());
    }
}
