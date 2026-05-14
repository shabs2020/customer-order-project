package com.customer.order.service.dto;

import com.customer.order.service.database.enums.OrderState;
import java.util.List;

public record OrderPatchDTO(
                            String category,
                            String state,
                            CustomerDTO customer,
                            SiteDTO site,
                            PaymentTypeDTO paymentType,
                            List<OrderItemDTO> items) {

}
