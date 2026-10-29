package com.transactionledger.exception;

public class DuplicateTransactionException extends RuntimeException {

    public DuplicateTransactionException(String referenceId) {
        super("A transaction with reference id '" + referenceId + "' already exists for this account");
    }
}
