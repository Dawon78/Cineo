package com.dw.movie.reservation;

import com.dw.movie.screen.SeatGrade;
import com.dw.movie.showtime.ShowtimeFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeatPriceCalculatorTest {

    @Test
    void STANDARD_좌석() {
        Long price = SeatPriceCalculator.calculate(10000L, SeatGrade.STANDARD, ShowtimeFormat.TWO_D);
        assertEquals(10000L, price);
    }

    @Test
    void PREMIUM_좌석_IMAX() {
        Long price = SeatPriceCalculator.calculate(10000L, SeatGrade.PREMIUM, ShowtimeFormat.IMAX);
        assertEquals(19000L, price); // 10000 + 3000(PREMIUM) + 6000(IMAX)
    }

    @Test
    void COUPLE_좌석_FOUR_DX() {
        Long price = SeatPriceCalculator.calculate(10000L, SeatGrade.COUPLE, ShowtimeFormat.FOUR_DX);
        assertEquals(23000L, price); // 10000 + 5000(COUPLE) + 8000(FOUR_DX)
    }
}

