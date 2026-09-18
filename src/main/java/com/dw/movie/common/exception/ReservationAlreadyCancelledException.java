package com.dw.movie.common.exception;

public class ReservationAlreadyCancelledException extends RuntimeException{
    public ReservationAlreadyCancelledException(String message){
        super(message);
    }
}
