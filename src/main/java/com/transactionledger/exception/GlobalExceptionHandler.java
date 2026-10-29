package com.transactionledger.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.transactionledger.dto.ErrorResponse;
import com.transactionledger.dto.FieldErrorItem;
import com.transactionledger.entity.TransactionType;
import com.transactionledger.logging.AppLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = AppLogger.get(GlobalExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateTransactionException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidPaginationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPagination(InvalidPaginationException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldErrorItem> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorItem(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldErrorItem> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldErrorItem(leafProperty(violation.getPropertyPath().toString()), violation.getMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message;
        if (ex.getRequiredType() == TransactionType.class) {
            message = "Transaction type must be CREDIT or DEBIT";
        } else if ("page".equals(ex.getName()) || "limit".equals(ex.getName())) {
            message = "Pagination parameters must be numbers";
        } else if (ex.getRequiredType() == Long.class) {
            message = "Account id must be a number";
        } else {
            message = "Invalid value for parameter '" + ex.getName() + "'";
        }
        return respond(HttpStatus.BAD_REQUEST, message, request, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String message = "Invalid request body";
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof InvalidFormatException invalidFormat
                && invalidFormat.getTargetType() != null
                && invalidFormat.getTargetType().isEnum()) {
            message = "Transaction type must be CREDIT or DEBIT";
        }
        return respond(HttpStatus.BAD_REQUEST, message, request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String text = ex.getMostSpecificCause().getMessage() == null
                ? ""
                : ex.getMostSpecificCause().getMessage().toLowerCase();
        if (text.contains("uk_transactions_account_reference") || text.contains("duplicate")) {
            return respond(HttpStatus.CONFLICT, "A transaction with this reference id already exists for the account", request, null);
        }
        return respond(HttpStatus.BAD_REQUEST, "Request violates a database constraint", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {}", request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", request, null);
    }

    private ResponseEntity<ErrorResponse> respond(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            List<FieldErrorItem> fieldErrors
    ) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message, request.getRequestURI(), fieldErrors));
    }

    private String leafProperty(String path) {
        int index = path.lastIndexOf('.');
        return index >= 0 ? path.substring(index + 1) : path;
    }
}
