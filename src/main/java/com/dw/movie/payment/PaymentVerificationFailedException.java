package com.dw.movie.payment;

public class PaymentVerificationFailedException extends RuntimeException {
    public PaymentVerificationFailedException(String message){
        super(message);
    }
}
