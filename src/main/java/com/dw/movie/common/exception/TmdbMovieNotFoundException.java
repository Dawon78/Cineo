package com.dw.movie.common.exception;

public class TmdbMovieNotFoundException extends RuntimeException {
    public TmdbMovieNotFoundException(String message) {
        super(message);
    }
}