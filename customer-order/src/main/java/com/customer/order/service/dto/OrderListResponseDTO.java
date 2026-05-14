package com.customer.order.service.dto;

import java.util.List;

public record OrderListResponseDTO(
        List<OrderResponseDTO> items,
        long total,
        int limit,
        int offset
) {

}
