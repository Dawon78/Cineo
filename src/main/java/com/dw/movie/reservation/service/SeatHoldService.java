package com.dw.movie.reservation.service;

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

    // 좌석 키를 한 번에 조회(multiGet)한다. 하나라도 값이 있으면 누군가 선점 중이다
    public boolean hasAnyHold(Long showtimeId, List<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return false;
        }
        List<String> keys = seatIds.stream().map(seatId -> key(showtimeId, seatId)).toList();
        List<String> holderIds = redisTemplate.opsForValue().multiGet(keys);
        return holderIds != null && holderIds.stream().anyMatch(holderId -> holderId != null);
    }

    // 모두 잡으면 선점 유지 시간(초)을 돌려준다. 화면 타이머가 이 값을 기준으로 센다
    public long hold(Long showtimeId, List<Long> seatIds, Long memberId) {
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
        return HOLD_TTL.toSeconds();
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

    public void validateNotHeldByOthers(Long showtimeId, List<Long> seatIds, Long memberId) {
        for (Long seatId : seatIds) {
            String holderId = redisTemplate.opsForValue().get(key(showtimeId, seatId));
            if (holderId != null && !holderId.equals(String.valueOf(memberId))) {
                throw new SeatAlreadyHeldException("다른 사용자가 선점 중인 좌석입니다: " + seatId);
            }
        }
    }

    public void releaseOwned(Long showtimeId, List<Long> seatIds, Long memberId) {
        for (Long seatId : seatIds) {
            String key = key(showtimeId, seatId);
            if (String.valueOf(memberId).equals(redisTemplate.opsForValue().get(key))) {
                redisTemplate.delete(key);
            }
        }
    }

    private String key(Long showtimeId, Long seatId) {
        return "seat-hold:" + showtimeId + ":" + seatId;
    }
}