package com.dw.movie.common.exception;

public class SeatAlreadyHeldException extends RuntimeException{
    public SeatAlreadyHeldException(String message){
        super(message);
    }
}
