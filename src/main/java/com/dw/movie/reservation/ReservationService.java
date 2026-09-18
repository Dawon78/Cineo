package com.dw.movie.reservation;

import com.dw.movie.auth.Member;
import com.dw.movie.auth.MemberRepository;
import com.dw.movie.common.exception.MemberNotFoundException;
import com.dw.movie.common.exception.ShowtimeNotFoundException;
import com.dw.movie.screen.Seat;
import com.dw.movie.screen.SeatRepository;
import com.dw.movie.showtime.Showtime;
import com.dw.movie.showtime.ShowtimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final MemberRepository memberRepository;
    private final SeatHoldService seatHoldService;

    public ReservationService(ReservationRepository reservationRepository, ShowtimeRepository showtimeRepository,
                              SeatRepository seatRepository, MemberRepository memberRepository, SeatHoldService seatHoldService) {
        this.reservationRepository = reservationRepository;
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
        this.memberRepository = memberRepository;
        this.seatHoldService = seatHoldService;
    }

    @Transactional
    public Reservation confirm(Long memberId, Long showtimeId, List<Long> seatIds) {
        seatHoldService.validateHolder(showtimeId, seatIds, memberId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException("해당 회원을 찾을 수 없습니다."));
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ShowtimeNotFoundException("해당 상영시간표를 찾을 수 없습니다."));
        List<Seat> seats = seatRepository.findAllById(seatIds);

        long totalPrice = 0L;
        Reservation reservation = new Reservation(member, showtime, 0L);
        for (Seat seat : seats) {
            long price = SeatPriceCalculator.calculate(showtime.getBasePrice(), seat.getGrade(), showtime.getFormat());
            totalPrice += price;
            reservation.addSeat(seat, price);
        }
        reservation.applyTotalPrice(totalPrice);

        Reservation saved = reservationRepository.save(reservation);
        seatHoldService.release(showtimeId, seatIds, memberId);

        return saved;
    }

    public List<Reservation> getMyReservations(Long memberId) {
        return reservationRepository.findByMemberId(memberId);
    }
}