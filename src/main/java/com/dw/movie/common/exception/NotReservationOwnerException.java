package com.dw.movie.common.exception;

public class NotReservationOwnerException extends RuntimeException{
    public NotReservationOwnerException(String message){
        super(message);
    }
}
