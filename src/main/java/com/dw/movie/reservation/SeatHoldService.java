package com.dw.movie.reservation;

import com.dw.movie.common.exception.NotSeatHolderException;
import com.dw.movie.common.exception.SeatAlreadyHeldException;
import com.dw.movie.common.exception.SeatHoldExpiredException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class SeatHoldService {

    private static final Duration HOLD_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public SeatHoldService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isHeld(Long showtimeId, Long seatId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(showtimeId, seatId)));
    }

    public void hold(Long showtimeId, List<Long> seatIds, Long memberId) {
        List<String> succeededKeys = new ArrayList<>();
        for (Long seatId : seatIds) {
            String key = key(showtimeId, seatId);
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(key, String.valueOf(memberId), HOLD_TTL);
            if (Boolean.TRUE.equals(success)) {
                succeededKeys.add(key);
            } else {
                succeededKeys.forEach(redisTemplate::delete);
                throw new SeatAlreadyHeldException("이미 선점된 좌석입니다: " + seatId);
            }
        }
    }

    public void release(Long showtimeId, List<Long> seatIds, Long memberId) {
        for (Long seatId : seatIds) {
            String key = key(showtimeId, seatId);
            String holderId = redisTemplate.opsForValue().get(key);
            if (holderId == null) {
                continue;
            }
            if (!holderId.equals(String.valueOf(memberId))) {
                throw new NotSeatHolderException("본인이 선점한 좌석이 아닙니다: " + seatId);
            }
            redisTemplate.delete(key);
        }
    }

    public void validateHolder(Long showtimeId, List<Long> seatIds, Long memberId) {
        for (Long seatId : seatIds) {
            String holderId = redisTemplate.opsForValue().get(key(showtimeId, seatId));
            if (holderId == null || !holderId.equals(String.valueOf(memberId))) {
                throw new SeatHoldExpiredException("선점이 만료되었거나 본인이 선점하지 않은 좌석입니다: " + seatId);
            }
        }
    }

    private String key(Long showtimeId, Long seatId) {
        return "seat-hold:" + showtimeId + ":" + seatId;
    }
}