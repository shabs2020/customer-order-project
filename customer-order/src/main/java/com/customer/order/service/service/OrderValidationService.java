package com.customer.order.service.service;

import com.customer.order.service.component.CatalogClient;
import com.customer.order.service.dto.OrderCreateDTO;
import com.customer.order.service.dto.OrderItemDTO;
import com.customer.order.service.dto.PaymentTypeDTO;
import com.customer.order.service.database.enums.PaymentType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service responsible for validating business rules for orders.
 * Handles payment method validation and product offering validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderValidationService {

    private final CatalogClient catalogClient;

    /**
     * Validates the payment method in the order create DTO.
     * Ensures that IBAN is provided for DIRECT_DEBIT payments.
     *
     * @param paymentTypeDTO The payment type DTO to validate.
     * @throws ResponseStatusException if validation fails.
     */
    public void validatePaymentMethod(PaymentTypeDTO paymentTypeDTO) {

        PaymentType paymentType = PaymentType.valueOf(paymentTypeDTO.type().toUpperCase());
        if (paymentType == PaymentType.DIRECT_DEBIT) {
            if (paymentTypeDTO.iban() == null || paymentTypeDTO.iban().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "IBAN is required for DIRECT_DEBIT payment type");
            }
        }
    }

    /**
     * Validates that all product offerings in the order exist in the catalog.
     *
     * @param orderCreateDTO The order create DTO containing the product offerings to validate.
     * @throws ResponseStatusException if any product offering does not exist.
     */
    public void validateProductOfferings(OrderCreateDTO orderCreateDTO) {
        if (orderCreateDTO.orderItems() == null || orderCreateDTO.orderItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order items are required");
        }

        List<String> productIds = orderCreateDTO.orderItems().stream()
                .map(OrderItemDTO::productOfferingId)
                .toList();

        catalogClient.verifyOfferingExists(productIds);
    }

    /**
     * Validates that all product offerings in the patch request exist in the catalog.
     *
     * @param orderItems The list of order items to validate.
     * @throws ResponseStatusException if any product offering does not exist.
     */
    public void validateProductOfferingsForPatch(List<OrderItemDTO> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            return;
        }

        List<String> productIds = orderItems.stream()
                .map(OrderItemDTO::productOfferingId)
                .toList();

        catalogClient.verifyOfferingExists(productIds);
    }
}
