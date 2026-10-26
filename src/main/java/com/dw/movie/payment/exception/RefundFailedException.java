package com.dw.movie.payment.exception;

public class RefundFailedException extends RuntimeException{
    public RefundFailedException(String message){
        super(message);
    }
}
