package com.customer.order.service.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponseDTO(
        UUID id,
        String productOfferingId,
        Integer quantity,
        BigDecimal priceAtPurchase
) {

}
