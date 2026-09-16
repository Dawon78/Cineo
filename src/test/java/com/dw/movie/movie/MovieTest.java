package com.dw.movie.movie;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MovieTest {

    @Test
    void 영화가_생성자로_잘_만들어지는지() {
        Movie movie = new Movie(27205L, "인셉션", "poster.jpg", "꿈 이야기", LocalDate.of(2010, 7, 21), 148, "12세이상관람가");

        assertEquals("인셉션", movie.getTitle());
        assertEquals(27205L, movie.getTmdbId());
    }
}