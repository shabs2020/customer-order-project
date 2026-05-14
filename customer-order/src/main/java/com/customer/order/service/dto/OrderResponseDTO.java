package com.customer.order.service.dto;

import com.customer.order.service.database.enums.OrderState;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponseDTO(
        UUID id,
        String category,
        OrderState state,
        CustomerDTO customer,
        SiteDTO site,
        PaymentTypeDTO paymentMethod,
        List<OrderItemDTO> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}