package com.dw.movie.showtime;

import com.dw.movie.movie.Movie;
import com.dw.movie.screen.Screen;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
public class Showtime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Movie movie;

    @ManyToOne
    private Screen screen;

    @Enumerated(EnumType.STRING)
    private ShowtimeFormat format;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long basePrice;
    private LocalDateTime createAt;

    public Showtime(Movie movie, Screen screen, ShowtimeFormat format, LocalDateTime startTime, LocalDateTime endTime, Long basePrice){
        this.movie = movie;
        this.screen = screen;
        this.format = format;
        this.startTime = startTime;
        this.endTime = endTime;
        this.basePrice = basePrice;
        this.createAt = LocalDateTime.now();
    }

}
