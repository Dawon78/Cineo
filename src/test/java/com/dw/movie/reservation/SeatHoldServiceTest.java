package com.dw.movie.reservation;

import com.dw.movie.common.exception.SeatAlreadyHeldException;
import com.dw.movie.reservation.service.SeatHoldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SeatHoldServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    @InjectMocks SeatHoldService seatHoldService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void 다른_사용자가_선점한_좌석이_있으면_예외를_던진다() {
        given(valueOperations.get("seat-hold:1:5")).willReturn("2");

        assertThrows(SeatAlreadyHeldException.class,
                () -> seatHoldService.validateNotHeldByOthers(1L, List.of(5L), 1L));
    }

    @Test
    void 내가_선점했거나_선점이_없는_좌석은_통과한다() {
        given(valueOperations.get("seat-hold:1:5")).willReturn("1");
        given(valueOperations.get("seat-hold:1:6")).willReturn(null);

        assertDoesNotThrow(() -> seatHoldService.validateNotHeldByOthers(1L, List.of(5L, 6L), 1L));
    }

    @Test
    void releaseOwned는_내_선점만_지우고_남의_선점이나_빈_좌석에도_예외를_던지지_않는다() {
        given(valueOperations.get("seat-hold:1:5")).willReturn("1");
        given(valueOperations.get("seat-hold:1:6")).willReturn("2");
        given(valueOperations.get("seat-hold:1:7")).willReturn(null);

        assertDoesNotThrow(() -> seatHoldService.releaseOwned(1L, List.of(5L, 6L, 7L), 1L));

        verify(redisTemplate).delete("seat-hold:1:5");
        verify(redisTemplate, never()).delete("seat-hold:1:6");
        verify(redisTemplate, never()).delete("seat-hold:1:7");
    }
}
