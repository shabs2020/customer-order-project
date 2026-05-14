package com.customer.order.service.mappers;

import com.customer.order.service.database.embeddables.Customer;
import com.customer.order.service.database.embeddables.PaymentMethod;
import com.customer.order.service.database.embeddables.Site;
import com.customer.order.service.database.entities.CustomerOrder;
import com.customer.order.service.database.entities.OrderItems;
import com.customer.order.service.dto.CustomerDTO;
import com.customer.order.service.dto.OrderItemDTO;
import com.customer.order.service.dto.OrderResponseDTO;
import com.customer.order.service.dto.PaymentTypeDTO;
import com.customer.order.service.dto.SiteDTO;
import org.springframework.stereotype.Component;

@Component
public class EntityToDtoMapping {

    public OrderResponseDTO mapToOrderResponseDTO(CustomerOrder customerOrder) {
        var orderItems = customerOrder.getOrderItems()!=null?customerOrder.getOrderItems().stream().map(this::mapToOrderItemCreateDTO).toList():null;
        return new OrderResponseDTO(
                customerOrder.getId(),
                customerOrder.getCategory().name(),
                customerOrder.getState(),
                mapToCustomerDTO(customerOrder.getCustomer()),
                mapToSiteDTO(customerOrder.getSite()),
                mapToPaymentMethodDTO(customerOrder.getPaymentMethod()),
                orderItems,
                customerOrder.getUpdatedAt(),
                customerOrder.getCreatedAt()

        );
    }

    private OrderItemDTO mapToOrderItemCreateDTO(OrderItems item) {
        return new OrderItemDTO(
                item.getProductOfferingId(),
                item.getQuantity());

    }

    private PaymentTypeDTO mapToPaymentMethodDTO(PaymentMethod paymentMethod) {
        if(paymentMethod == null){
            return null;
        }
        return new PaymentTypeDTO(
                paymentMethod.getPaymentType().toString(),
                paymentMethod.getIban());
    }
    private CustomerDTO mapToCustomerDTO(Customer customer){
        return new CustomerDTO(
                customer.getId());
    }
    private SiteDTO mapToSiteDTO(Site site){
        return new SiteDTO(site.getId());
    }

}
