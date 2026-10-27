package com.dw.movie.reservation.service;

import com.dw.movie.member.entity.Member;
import com.dw.movie.member.repository.MemberRepository;
import com.dw.movie.common.exception.*;
import com.dw.movie.payment.exception.PaymentVerificationFailedException;
import com.dw.movie.payment.exception.PortOneApiException;
import com.dw.movie.payment.exception.RefundFailedException;
import com.dw.movie.payment.client.PortOneClient;
import com.dw.movie.reservation.entity.ReservationStatus;
import com.dw.movie.reservation.dto.SeatPriceCalculator;
import com.dw.movie.reservation.entity.Reservation;
import com.dw.movie.reservation.repository.ReservationRepository;
import com.dw.movie.screen.entity.Seat;
import com.dw.movie.screen.repository.SeatRepository;
import com.dw.movie.showtime.entity.Showtime;
import com.dw.movie.showtime.repository.ShowtimeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final String SEAT_FAILURE_REFUND_REASON = "좌석 확보 실패로 인한 자동 환불";
    private static final String AMOUNT_MISMATCH_REFUND_REASON = "결제 금액 불일치로 인한 자동 환불";
    private static final String LOOKUP_FAILURE_REFUND_REASON = "결제 확인 실패로 인한 자동 환불";

    private final ReservationRepository reservationRepository;
    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final MemberRepository memberRepository;
    private final SeatHoldService seatHoldService;
    private final PortOneClient portOneClient;
    private final SeatQueryService seatQueryService;

    public ReservationService(ReservationRepository reservationRepository, ShowtimeRepository showtimeRepository,
                              SeatRepository seatRepository, MemberRepository memberRepository, SeatHoldService seatHoldService,
                              PortOneClient portOneClient, SeatQueryService seatQueryService) {
        this.reservationRepository = reservationRepository;
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
        this.memberRepository = memberRepository;
        this.seatHoldService = seatHoldService;
        this.portOneClient = portOneClient;
        this.seatQueryService = seatQueryService;
    }

    @Transactional
    public Reservation confirm(Long memberId, Long showtimeId, List<Long> seatIds, String impUid) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new MemberNotFoundException("해당 회원을 찾을 수 없습니다."));
        Showtime showtime = showtimeRepository.findByIdForUpdate(showtimeId)
                .orElseThrow(() -> new ShowtimeNotFoundException("해당 상영시간표를 찾을 수 없습니다."));
        if (!showtime.getStartTime().isAfter(LocalDateTime.now())) {
            throw new ShowtimeAlreadyStartedException("이미 시작된 상영시간표입니다.");
        }
        if (reservationRepository.existsByImpUid(impUid)) {
            throw new PaymentVerificationFailedException("이미 사용된 결제입니다: " + impUid);
        }

        List<Seat> seats = seatQueryService.validateSeatShape(showtime, seatIds);

        long totalPrice = 0L;
        for (Seat seat : seats) {
            totalPrice += SeatPriceCalculator.calculate(showtime.getBasePrice(), seat.getGrade(), showtime.getFormat());
        }

        PortOneClient.PaymentInfo paymentInfo;
        try {
            paymentInfo = portOneClient.getPaymentInfo(impUid);
        } catch (PortOneApiException e) {
            refundQuietly(impUid);
            throw new PortOneApiException(e.getMessage() + " 결제는 자동 환불을 요청했습니다.");
        }
        if (!"paid".equals(paymentInfo.status())) {
            throw new PaymentVerificationFailedException("결제가 완료되지 않았습니다: " + impUid);
        }
        if (!Long.valueOf(totalPrice).equals(paymentInfo.amount())) {
            refundAfterFailure(impUid, AMOUNT_MISMATCH_REFUND_REASON);
            throw new PaymentVerificationFailedException("결제 금액이 일치하지 않습니다. 결제는 자동으로 환불되었습니다.");
        }

        try {
            seatQueryService.validateNotReserved(showtime, seatIds);
            seatHoldService.validateNotHeldByOthers(showtimeId, seatIds, memberId);
        } catch (SeatAlreadyReservedException e) {
            refundAfterFailure(impUid, SEAT_FAILURE_REFUND_REASON);
            throw new SeatAlreadyReservedException(e.getMessage() + " 결제는 자동으로 환불되었습니다.");
        } catch (SeatAlreadyHeldException e) {
            refundAfterFailure(impUid, SEAT_FAILURE_REFUND_REASON);
            throw new SeatAlreadyHeldException(e.getMessage() + " 결제는 자동으로 환불되었습니다.");
        }

        Reservation reservation = new Reservation(member, showtime, 0L);
        for (Seat seat : seats) {
            long price = SeatPriceCalculator.calculate(showtime.getBasePrice(), seat.getGrade(), showtime.getFormat());
            reservation.addSeat(seat, price);
        }
        reservation.applyTotalPrice(totalPrice);
        reservation.applyPayment(impUid, paymentInfo.amount());
        reservation.applyEarnedPoints(member.recordPayment(totalPrice));

        Reservation saved = reservationRepository.save(reservation);
        seatHoldService.releaseOwned(showtimeId, seatIds, memberId);

        return saved;
    }

    private void refundAfterFailure(String impUid, String reason) {
        portOneClient.cancelPayment(impUid, reason);
    }

    // 결제 확인 자체가 실패한 경우라 환불도 실패할 수 있다. 원래 오류를 가리지 않도록 환불 실패는 로그만 남긴다
    private void refundQuietly(String impUid) {
        try {
            portOneClient.cancelPayment(impUid, LOOKUP_FAILURE_REFUND_REASON);
        } catch (RefundFailedException | PortOneApiException e) {
            log.warn("결제 확인 실패 후 자동 환불도 실패 (impUid={}): {}", impUid, e.getMessage());
        }
    }

    public List<Reservation> getMyReservations(Long memberId) {
        return reservationRepository.findByMemberId(memberId);
    }

    @Transactional
    public void cancel(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("해당 예약을 찾을 수 없습니다."));

        if (!reservation.getMember().getId().equals(memberId)) {
            throw new NotReservationOwnerException("본인의 예약이 아닙니다.");
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new ReservationAlreadyCancelledException("이미 취소된 예약입니다.");
        }

        if (!reservation.getShowtime().getStartTime().isAfter(LocalDateTime.now())) {
            throw new ShowtimeAlreadyStartedException("이미 시작된 상영은 취소할 수 없습니다.");
        }

        portOneClient.cancelPayment(reservation.getImpUid(), "고객 예약 취소");
        reservation.cancel();
        long earnedPoints = reservation.getEarnedPoints() == null ? 0L : reservation.getEarnedPoints();
        reservation.getMember().refundPayment(reservation.getTotalPrice(), earnedPoints);
    }
}