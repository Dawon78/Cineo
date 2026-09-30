package com.dw.movie.payment;

public class RefundFailedException extends RuntimeException{
    public RefundFailedException(String message){
        super(message);
    }
}
