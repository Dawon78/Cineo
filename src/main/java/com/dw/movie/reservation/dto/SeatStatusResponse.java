package com.dw.movie.reservation.dto;

import com.dw.movie.reservation.entity.SeatStatus;
import com.dw.movie.screen.entity.SeatGrade;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class SeatStatusResponse {
    private Long seatId;
    private String rowLabel;
    private Integer seatNumber;
    private SeatGrade grade;
    private SeatStatus status;
}
