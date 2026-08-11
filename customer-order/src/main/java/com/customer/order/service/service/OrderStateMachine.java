package com.customer.order.service.service;

import com.customer.order.service.database.embeddables.Customer;
import com.customer.order.service.database.embeddables.PaymentMethod;
import com.customer.order.service.database.embeddables.Site;
import com.customer.order.service.database.entities.CustomerOrder;
import com.customer.order.service.database.entities.OrderItems;
import com.customer.order.service.database.enums.OrderCategory;
import com.customer.order.service.database.enums.OrderState;
import com.customer.order.service.database.enums.PaymentType;
import com.customer.order.service.dto.OrderItemDTO;
import com.customer.order.service.dto.OrderPatchDTO;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service responsible for managing order state transitions and validation.
 * Enforces business rules for order state changes and data modifications.
 */
@Slf4j
@Service
public class OrderStateMachine {

    /**
     * Validates if a state transition is allowed.
     */
    public void validateStateTransition(OrderState currentState, OrderState newState) {
        if (currentState == newState) {
            return;
        }

        boolean isValidTransition = switch (currentState) {
            case DRAFT -> newState == OrderState.PREVIEW;
            case PREVIEW -> newState == OrderState.DRAFT || newState == OrderState.SUBMITTED;
            case SUBMITTED -> newState == OrderState.CONFIRMED;
            case CONFIRMED -> false;
        };

        if (!isValidTransition) {
            log.warn("Invalid state transition: {} -> {}", currentState, newState);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid state transition from " + currentState + " to " + newState);
        }
    }

    /**
     * Checks if the order data is being modified in a way that is not allowed for its current state.
     */
    public boolean isDataBeingModified(CustomerOrder existingOrder, OrderPatchDTO patch) {

        if (patch.category() != null
                && !OrderCategory.valueOf(patch.category().toUpperCase()).equals(existingOrder.getCategory())) {
            return true;
        }

        if (patch.customer() != null && !patch.customer().id().equals(existingOrder.getCustomer().getId())) {
            return true;
        }

        if (patch.site() != null && !patch.site().id().equals(existingOrder.getSite().getId())) {
            return true;
        }

        if (patch.orderItems() != null) {
            if (patch.orderItems().size() != existingOrder.getOrderItems().size()) {
                return true;
            }
            List<OrderItems> mappedItems = patch.orderItems().stream()
                    .map(this::mapItemToEntity)
                    .toList();
            return !mappedItems.equals(existingOrder.getOrderItems());
        }

        if (patch.paymentType() != null) {
            PaymentMethod patchPayment = PaymentMethod.builder()
                    .paymentType(PaymentType.valueOf(patch.paymentType().type().toUpperCase()))
                    .iban(patch.paymentType().iban())
                    .build();
            return !patchPayment.equals(existingOrder.getPaymentMethod());
        }

        return false;
    }

    /**
     * Applies the patch updates to the existing order.
     */
    public void applyDataUpdates(CustomerOrder existingOrder, OrderPatchDTO patch) {
        if (patch.category() != null) {
            existingOrder.setCategory(OrderCategory.valueOf(patch.category().toUpperCase()));
        }

        if (patch.customer() != null) {
            existingOrder.setCustomer(Customer.builder().id(patch.customer().id()).build());
        }

        if (patch.site() != null) {
            existingOrder.setSite(Site.builder().id(patch.site().id()).build());
        }

        if (patch.orderItems() != null) {
            existingOrder.getOrderItems().clear();
            existingOrder.getOrderItems().addAll(
                    patch.orderItems().stream()
                            .map(this::mapItemToEntity)
                            .toList());
        }

        if (patch.paymentType() != null) {
            existingOrder.setPaymentMethod(PaymentMethod.builder()
                    .paymentType(PaymentType.valueOf(patch.paymentType().type().toUpperCase()))
                    .iban(patch.paymentType().iban())
                    .build());
        }

        if (patch.state() != null) {
            existingOrder.setState(OrderState.valueOf(patch.state().toUpperCase()));
        }
    }

    /**
     * Maps an OrderItemDTO to an OrderItems entity.
     */
    private OrderItems mapItemToEntity(OrderItemDTO itemDto) {
        return OrderItems.builder()
                .productOfferingId(itemDto.productOfferingId())
                .quantity(itemDto.quantity())
                .build();
    }
}
