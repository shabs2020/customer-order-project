package com.customer.order.service.service;

import com.customer.order.service.database.entities.CustomerOrder;
import com.customer.order.service.database.enums.OrderCategory;
import com.customer.order.service.database.enums.OrderState;
import com.customer.order.service.database.repositories.CustomerOrderRepository;
import com.customer.order.service.dto.OrderCreateDTO;
import com.customer.order.service.dto.OrderListResponseDTO;
import com.customer.order.service.dto.OrderPatchDTO;
import com.customer.order.service.dto.OrderResponseDTO;
import com.customer.order.service.dto.OrderResponseIdempotencyDTO;
import jakarta.transaction.Transactional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service responsible for orchestrating order creation, retrieval, and updating.
 * Delegates specific responsibilities to specialized services.
 */
@Slf4j
@Service
@AllArgsConstructor
public class OrderService {

    private final CustomerOrderRepository orderRepository;
    private final IdempotencyService idempotencyService;
    private final OrderValidationService orderValidationService;
    private final OrderStateMachine orderStateMachine;
    private final OrderMapper orderMapper;

    @Transactional
    public OrderResponseIdempotencyDTO createOrder(OrderCreateDTO dto, String key) {
        orderValidationService.validatePaymentMethod(dto.paymentType());
        orderValidationService.validateProductOfferings(dto);
        
        if (key == null) {
            CustomerOrder createdOrder = orderRepository.save(orderMapper.toCustomerOrder(dto));
            return new OrderResponseIdempotencyDTO(orderMapper.toOrderResponseDTO(createdOrder), false);
        }
        
        String currentHash = idempotencyService.generateHash(dto);
        OrderResponseIdempotencyDTO idempotencyCheck = idempotencyService.checkAndLockIdempotency(
                key, currentHash, null);
        
        if (idempotencyCheck.isReplay()) {
            return idempotencyCheck;
        }
        
        CustomerOrder createdOrder = orderRepository.save(orderMapper.toCustomerOrder(dto));
        idempotencyService.updateIdempotencyKeyWithOrderId(key, createdOrder.getId());
        
        return new OrderResponseIdempotencyDTO(orderMapper.toOrderResponseDTO(createdOrder), false);
    }

    @Transactional
    public OrderResponseDTO getOrderById(UUID id) {
        try {
            return orderRepository.findById(id)
                    .map(orderMapper::toOrderResponseDTO)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID format");
        }
    }

    @Transactional
    public OrderListResponseDTO listOrders(String category, int limit, int offset) {
        Pageable pageable = PageRequest.of(offset, limit);
        Page<CustomerOrder> orders;

        if (category != null) {
            OrderCategory orderCategory = OrderCategory.valueOf(category.toUpperCase());
            orders = orderRepository.findByCategory(orderCategory, pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }

        return orderMapper.toOrderListResponseDTO(orders);
    }

    @Transactional
    public OrderResponseDTO patchOrder(UUID id, OrderPatchDTO patchOrder) {
        CustomerOrder existingOrder = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (existingOrder.getState() == OrderState.CONFIRMED) {
            throw new IllegalStateException("Cannot modify order in CONFIRMED state");
        }

        if(existingOrder.getState() == OrderState.SUBMITTED){
            if(orderStateMachine.isDataBeingModified(existingOrder, patchOrder)){
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot modify order data in SUBMITTED state. Only state changes are allowed.");
            }
        }
        if(patchOrder.paymentType() != null){
            orderValidationService.validatePaymentMethod(patchOrder.paymentType());
        }

        if (patchOrder.state() != null) {
            OrderState newState = OrderState.valueOf(patchOrder.state().toUpperCase());
            orderStateMachine.validateStateTransition(existingOrder.getState(), newState);
        }
        if (patchOrder.orderItems() != null) {
            orderValidationService.validateProductOfferingsForPatch(patchOrder.orderItems());
        }
        orderStateMachine.applyDataUpdates(existingOrder, patchOrder);
        CustomerOrder updatedOrder = orderRepository.save(existingOrder);

        return orderMapper.toOrderResponseDTO(updatedOrder);
    }
}
