package com.dw.movie.common.exception;

public class ShowtimeAlreadyStartedException extends RuntimeException {
    public ShowtimeAlreadyStartedException(String message){
        super(message);
    }
}
