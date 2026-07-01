package org.neobank.transactionservice.exception;

public class KycNotApprovedException extends RuntimeException {
    public KycNotApprovedException(String message) {
        super(message);
    }
}
