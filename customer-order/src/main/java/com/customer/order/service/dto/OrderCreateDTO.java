package com.customer.order.service.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
public record OrderCreateDTO(
        @NotBlank(message = "Category is required")
        String category,

        @NotNull(message = "Customer is required")
        @Valid
        CustomerDTO customer,

        @NotNull(message = "Site is required")
        @Valid
        SiteDTO site,

        @NotEmpty(message = "Order orders cannot be empty")
        List<OrderItemDTO> orderItems,

        @NotNull(message = "Payment method type is required")
        PaymentTypeDTO paymentType
) {}