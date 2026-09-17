package com.dw.movie.reservation;

import com.dw.movie.screen.SeatGrade;
import com.dw.movie.showtime.ShowtimeFormat;

public class SeatPriceCalculator {

    public static Long calculate(Long basePrice, SeatGrade grade, ShowtimeFormat format) {
        return basePrice + gradeSurcharge(grade) + formatSurcharge(format);
    }

    private static long gradeSurcharge(SeatGrade grade) {
        return switch (grade) {
            case PREMIUM -> 3000L;
            case COUPLE -> 5000L;
            case STANDARD -> 0L;
        };
    }

    private static long formatSurcharge(ShowtimeFormat format) {
        return switch (format) {
            case THREE_D -> 2000L;
            case IMAX -> 6000L;
            case FOUR_DX -> 8000L;
            case TWO_D -> 0L;
        };
    }
}