package com.customer.order.service.service;

import com.customer.order.service.database.embeddables.Customer;
import com.customer.order.service.database.embeddables.PaymentMethod;
import com.customer.order.service.database.embeddables.Site;
import com.customer.order.service.database.entities.CustomerOrder;
import com.customer.order.service.database.entities.OrderItems;
import com.customer.order.service.database.enums.OrderCategory;
import com.customer.order.service.database.enums.OrderState;
import com.customer.order.service.database.enums.PaymentType;
import com.customer.order.service.dto.*;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

/**
 * Service responsible for mapping between DTOs and entities.
 */
@Service
public class OrderMapper {

    public OrderResponseDTO toOrderResponseDTO(CustomerOrder customerOrder) {
        List<OrderItemDTO> orderItems = customerOrder.getOrderItems() != null
                ? customerOrder.getOrderItems().stream()
                        .map(this::toOrderItemDTO)
                        .toList()
                : null;

        return new OrderResponseDTO(
                customerOrder.getId(),
                customerOrder.getCategory().name(),
                customerOrder.getState(),
                toCustomerDTO(customerOrder.getCustomer()),
                toSiteDTO(customerOrder.getSite()),
                toPaymentTypeDTO(customerOrder.getPaymentMethod()),
                orderItems,
                customerOrder.getUpdatedAt(),
                customerOrder.getCreatedAt()
        );
    }

    public OrderListResponseDTO toOrderListResponseDTO(Page<CustomerOrder> orders) {
        List<OrderResponseDTO> orderResponseDTOs = orders.getContent().stream()
                .map(this::toOrderResponseDTO)
                .toList();

        return new OrderListResponseDTO(
                orderResponseDTOs,
                orders.getTotalElements(),
                orders.getPageable().getPageSize(),
                (int) orders.getPageable().getOffset()
        );
    }

    public CustomerOrder toCustomerOrder(OrderCreateDTO dto) {
        return CustomerOrder.builder()
                .category(OrderCategory.valueOf(dto.category().toUpperCase()))
                .customer(toCustomerEntity(dto.customer()))
                .site(toSiteEntity(dto.site()))
                .paymentMethod(toPaymentMethodEntity(dto.paymentType()))
                .orderItems(toOrderItemsEntities(dto.orderItems()))
                .state(OrderState.DRAFT)
                .build();
    }


    private OrderItemDTO toOrderItemDTO(OrderItems item) {
        return new OrderItemDTO(item.getProductOfferingId(), item.getQuantity());
    }

    private CustomerDTO toCustomerDTO(Customer customer) {
        return customer != null ? new CustomerDTO(customer.getId()) : null;
    }

    private SiteDTO toSiteDTO(Site site) {
        return site != null ? new SiteDTO(site.getId()) : null;
    }

    private PaymentTypeDTO toPaymentTypeDTO(PaymentMethod paymentMethod) {
        if (paymentMethod == null) return null;
        return new PaymentTypeDTO(paymentMethod.getPaymentType().toString(), paymentMethod.getIban());
    }

    private Customer toCustomerEntity(CustomerDTO dto) {
        return Customer.builder().id(dto.id()).build();
    }

    private Site toSiteEntity(SiteDTO dto) {
        return Site.builder().id(dto.id()).build();
    }

    private PaymentMethod toPaymentMethodEntity(PaymentTypeDTO dto) {
        return PaymentMethod.builder()
                .paymentType(PaymentType.valueOf(dto.type().toUpperCase()))
                .iban(dto.iban())
                .build();
    }

    private List<OrderItems> toOrderItemsEntities(List<OrderItemDTO> dtos) {
        // Aggregate items with the same productOfferingId
        List<OrderItemDTO> aggregatedItems = aggregateOrderItemsByProductOfferingId(dtos);

        return aggregatedItems.stream()
                .map(dto -> OrderItems.builder()
                        .productOfferingId(dto.productOfferingId())
                        .quantity(dto.quantity())
                        .build())
                .toList();
    }

    /**
     * Aggregates order items by productOfferingId, summing quantities for items with the same ID.
     *
     * @param orderItems List of order items to aggregate
     * @return List of aggregated order items
     */
    private List<OrderItemDTO> aggregateOrderItemsByProductOfferingId(List<OrderItemDTO> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            return orderItems;
        }

        // Group by productOfferingId and sum quantities
        return orderItems.stream()
                .collect(Collectors.groupingBy(
                        OrderItemDTO::productOfferingId,
                        Collectors.summingInt(OrderItemDTO::quantity)
                ))
                .entrySet()
                .stream()
                .map(entry -> new OrderItemDTO(entry.getKey(), entry.getValue()))
                .toList();
    }
}
