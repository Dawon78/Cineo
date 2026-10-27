package com.dw.movie.common.exception;

public class InvalidShowtimeException extends RuntimeException {
    public InvalidShowtimeException(String message){
        super(message);
    }
}
