package com.dw.movie.common.exception;

public class SeatHoldExpiredException extends RuntimeException{
    public SeatHoldExpiredException(String message){
        super(message);
    }
}
