package com.dw.movie.common.exception;

public class DuplicateTmdbIdException extends RuntimeException {
    public DuplicateTmdbIdException(String message) {
        super(message);
    }
}