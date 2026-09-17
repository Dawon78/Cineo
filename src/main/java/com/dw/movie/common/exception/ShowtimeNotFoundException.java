package com.dw.movie.common.exception;

public class ShowtimeNotFoundException extends RuntimeException{
    public ShowtimeNotFoundException(String message){
        super(message);
    }
}
