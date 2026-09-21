package com.dw.movie.common.exception;

public class RefundFailedException extends RuntimeException{
    public RefundFailedException(String message){
        super(message);
    }
}
