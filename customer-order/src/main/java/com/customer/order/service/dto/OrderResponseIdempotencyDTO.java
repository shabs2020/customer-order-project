package com.customer.order.service.dto;

public record OrderResponseIdempotencyDTO(
        OrderResponseDTO dto,
        boolean isReplay
) {

}
