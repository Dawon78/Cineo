package com.dw.movie.common.exception;

public class ScreenNotFoundException extends RuntimeException {
    public ScreenNotFoundException(String message){
        super(message);
    }
}
