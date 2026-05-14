package com.customer.order.service.exceptions;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String offeringId) {
        super(String.format("unable to find product with id %s", offeringId));
    }
}
