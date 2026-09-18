package com.dw.movie.common.exception;

public class ReservationNotFoundException extends RuntimeException{
    public ReservationNotFoundException(String message){
        super(message);
    }
}
