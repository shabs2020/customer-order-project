package com.customer.order.service.dto;

import jakarta.validation.Valid;
import java.util.List;

public record OrderPatchDTO(
        String category,
        String state,
        CustomerDTO customer,
        SiteDTO site,
        @Valid
        List<OrderItemDTO> orderItems,
        PaymentTypeDTO paymentType
                            ) {

}
