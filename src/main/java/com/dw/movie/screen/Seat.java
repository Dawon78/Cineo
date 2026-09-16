package com.dw.movie.screen;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Screen screen;

    private  String rowLabel;
    private  Integer seatNumber;

    @Enumerated(EnumType.STRING)
    private SeatGrade grade;
    private LocalDateTime createdAt;

    public Seat(Screen screen, String rowLabel, Integer seatNumber, SeatGrade grade){
        this.screen = screen;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.grade = grade;
        this.createdAt = LocalDateTime.now();
    }


}

