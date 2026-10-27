package com.dw.movie.common.exception;

public class ShowtimeNotDeletableException extends RuntimeException {
    public ShowtimeNotDeletableException(String message){
        super(message);
    }
}
