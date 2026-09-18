package com.dw.movie.reservation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class SeatHoldConcurrencyTest {

    @Autowired
    private SeatHoldService seatHoldService;

    @Test
    void 같은_좌석에_동시에_10명이_선점을_시도하면_한명만_성공한다() throws InterruptedException {
        Long showtimeId = 999L;
        Long seatId = 999L;
        int threadCount = 10;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            long memberId = i;
            executorService.submit(() -> {
                try {
                    seatHoldService.hold(showtimeId, List.of(seatId), memberId);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                    // 선점 실패는 정상 동작 — 카운트하지 않음
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        assertEquals(1, successCount.get());
    }
}