package com.customer.order.service.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice
public class RestExceptionHandler {
    // Handles: Illegal State Transitions & Validation Logic
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        return  ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,ex.getMessage());
    }

    // Handles: Invalid arguments (e.g., from the Product Catalog check)
    @ExceptionHandler(IllegalArgumentException.class)
    public  ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return  ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public  ProblemDetail handleStatusException(ResponseStatusException ex, HttpStatus status) {
        return  ProblemDetail.forStatusAndDetail(status,ex.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public  ProblemDetail handleException(ProductNotFoundException e) {
        return  ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,e.getMessage());
    }
}


