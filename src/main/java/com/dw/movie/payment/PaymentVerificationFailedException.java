package com.dw.movie.common.exception;

public class PaymentVerificationFailedException extends RuntimeException {
    public PaymentVerificationFailedException(String message){
        super(message);
    }
}
