package com.customer.order.service.dto;

import java.util.List;

public record OrderPatchDTO(
        String category,
        String state,
        CustomerDTO customer,
        SiteDTO site,
        List<OrderItemDTO> orderItems,
        PaymentTypeDTO paymentType
                            ) {

}
