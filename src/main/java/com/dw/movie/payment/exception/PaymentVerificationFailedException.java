package com.dw.movie.payment.exception;

public class PaymentVerificationFailedException extends RuntimeException {
    public PaymentVerificationFailedException(String message){
        super(message);
    }
}
