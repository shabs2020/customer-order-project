package com.customer.order.service.service;

import com.customer.order.service.client.CatalogClient;
import com.customer.order.service.database.embeddables.Customer;
import com.customer.order.service.database.embeddables.PaymentMethod;
import com.customer.order.service.database.embeddables.Site;
import com.customer.order.service.database.entities.CustomerOrder;
import com.customer.order.service.database.entities.IdempotencyKey;
import com.customer.order.service.database.entities.OrderItems;
import com.customer.order.service.database.enums.OrderCategory;
import com.customer.order.service.database.enums.OrderState;
import com.customer.order.service.database.enums.PaymentType;
import com.customer.order.service.database.repositories.CustomerOrderRepository;
import com.customer.order.service.database.repositories.IdempotencyRepository;
import com.customer.order.service.dto.OrderCreateDTO;
import com.customer.order.service.dto.OrderItemDTO;
import com.customer.order.service.dto.OrderListResponseDTO;
import com.customer.order.service.dto.OrderPatchDTO;
import com.customer.order.service.dto.OrderResponseDTO;
import com.customer.order.service.dto.OrderResponseIdempotencyDTO;
import com.customer.order.service.dto.PaymentTypeDTO;
import com.customer.order.service.mappers.EntityToDtoMapping;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final CustomerOrderRepository orderRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final CatalogClient catalogClient;
    private final EntityToDtoMapping mapper;

    @Transactional
    public OrderResponseIdempotencyDTO createOrder(OrderCreateDTO dto, String key) {
        //Check iban for direct debit
        validatePaymentMethod(dto.paymentType());
        //Check if productOfferingId exists in product catalog service
        List<String> productIds = dto.orderItems().stream()
                .map(OrderItemDTO::productOfferingId)
                .toList();
        catalogClient.verifyOfferingExists(productIds);
        // Idempotency check: In a real app, check a Redis/DB cache for the key
        // Here the database entity IdempotencyKey stores the key and other details
        if (key == null) {
            CustomerOrder createdOrder = orderRepository.save(buildCustomerOrder(dto));
            return new OrderResponseIdempotencyDTO(mapper.mapToOrderResponseDTO(createdOrder),false);
        }
        String currentHash = generateHash(dto);
        Optional<IdempotencyKey> existing = idempotencyRepository.findById(key);
        if (existing.isPresent()) {
            // If it exists, use your existing logic to handle the replay or conflict
            return handleExistingKey(existing.get(), currentHash);
        }
        try{
            IdempotencyKey newRecord = IdempotencyKey.builder()
                    .key(key)
                    .requestHash(currentHash)
                    .expiryDate(LocalDateTime.now().plusHours(24))
                    .build();
            idempotencyRepository.saveAndFlush(newRecord);
            log.info(dto.customer().id());
            CustomerOrder newOrder = orderRepository.save(buildCustomerOrder(dto));
            newRecord.setOrderId(newOrder.getId());
            idempotencyRepository.save(newRecord);
            return new OrderResponseIdempotencyDTO(mapper.mapToOrderResponseDTO(newOrder), false);
        } catch (DataIntegrityViolationException e) {
            IdempotencyKey raceConditionRecord = idempotencyRepository.findById(key)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR));
            return handleExistingKey(raceConditionRecord, currentHash);
        }
    }



    @Transactional
    public OrderResponseDTO patchOrder(UUID id, OrderPatchDTO patchOrderDTO) {
        //Validate if payment method in request is correct for direct_debit
        if(patchOrderDTO.paymentType() != null){
            validatePaymentMethod(patchOrderDTO.paymentType());
        }
        //Check if productOfferingId exists in product catalog service
        if(patchOrderDTO.orderItems() != null){
            List<String> productIds = patchOrderDTO.orderItems().stream()
                    .map(OrderItemDTO::productOfferingId)
                    .toList();
            catalogClient.verifyOfferingExists(productIds);
            log.info("All product offerings valid");
        }


        var getOrder = orderRepository.findById(id);
        if(getOrder.isEmpty()){
            throw new NoSuchElementException("Order not found");
        }
        CustomerOrder existingOrder = getOrder.get();

        //Rule: Confirmed orders are immutable
        if (existingOrder.getState() == OrderState.CONFIRMED) {
            throw new IllegalStateException("Confirmed orders cannot be modified.");
        }
        OrderState currentState = existingOrder.getState();
        log.info("OrderState {}", currentState.getValue());
        // If SUBMITTED, check if data in patch differs from current entity
        if(currentState.equals(OrderState.SUBMITTED)){
            log.info("State is submitted");
            if(isDataBeingModified(existingOrder, patchOrderDTO)){
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Only state can be modified after an order is submitted");
            }
        }

       //Transition Validation
        if(patchOrderDTO.state() != null && !patchOrderDTO.state().toLowerCase().equals(currentState.getValue())){
            OrderState nextState = OrderState.fromValue(patchOrderDTO.state().toLowerCase());
            log.info("checkTransition {}", currentState.checkTransition(nextState));
            if(!currentState.checkTransition(nextState)){
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid state transition: " + currentState.getValue() + " -> " + patchOrderDTO.state());
            }
            existingOrder.setState(nextState);
            }

        applyDataUpdates(existingOrder,patchOrderDTO);
        var savedOrder =  orderRepository.save(existingOrder);
        return mapper.mapToOrderResponseDTO(savedOrder);
    }



    public OrderResponseDTO getOrderById(UUID id) {
        Optional<CustomerOrder> customerOrder = orderRepository.findById(id);
        if(customerOrder.isEmpty()){
            throw new NoSuchElementException("Unable to find Order");
        }
        return mapper.mapToOrderResponseDTO(customerOrder.get());
    }
    public OrderListResponseDTO listOrders(String category, int limit, int offset) {
        // Convert offset-based API to page-based Spring Data
        // pageNumber = offset / limit
        int pageNumber = offset / limit;
        Pageable pageable = PageRequest.of(pageNumber, limit);
        Page<CustomerOrder> resultPage;
        if(category == null){
            resultPage = orderRepository.findAll(pageable);
        }
        else{
            resultPage = orderRepository.findByCategory(OrderCategory.valueOf(category.toUpperCase()), pageable);
        }
        List<OrderResponseDTO> orderList = resultPage.getContent().stream().map(mapper::mapToOrderResponseDTO).toList();
        return new OrderListResponseDTO(
                orderList,        // The List<Order>
                (int) resultPage.getTotalElements(), // The 'total' count for the frontend
                limit,
                offset
        );
    }
    private OrderResponseIdempotencyDTO handleExistingKey(IdempotencyKey existingKeyRecord, String currentHash) {
               // RULE: Same key + Different payload = 409 Conflict
        if (!existingKeyRecord.getRequestHash().equals(currentHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The Idempotency-Key is already associated with a different request payload.");
        }

        // RULE: Same key + Identical payload = Replay Result
        if (existingKeyRecord.getOrderId() != null) {
            CustomerOrder order = orderRepository.findById(existingKeyRecord.getOrderId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Original order not found."));
            return new OrderResponseIdempotencyDTO(mapper.mapToOrderResponseDTO(order),true);
        }

        // If orderId is still null, it means the first request is still running in another thread.
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "A request with this key is already being processed. Please wait.");

    }
    private void validatePaymentMethod(PaymentTypeDTO paymentMethod) {
        // 1. Check if the type is DIRECT_DEBIT (ignoring case for flexibility)
        log.info(paymentMethod.type());
        if (paymentMethod.type().toUpperCase().equals(PaymentType.DIRECT_DEBIT.toString())) {

            // 2. If it is direct debit, the IBAN must not be null or blank
            if (paymentMethod.iban() == null || paymentMethod.iban().isBlank()) {
                throw new IllegalArgumentException("IBAN is required for direct debit payment method"
                );
            }
        }
    }

    private CustomerOrder buildCustomerOrder(OrderCreateDTO dto){
        return CustomerOrder.builder()
                .category(OrderCategory.valueOf(dto.category().toUpperCase()))
                .state(OrderState.DRAFT) // Force initial state
                .customer(Customer.builder().id(dto.customer().id()).build())
                .site(Site.builder().id(dto.site().id()).build())
                .paymentMethod(PaymentMethod.builder().paymentType(PaymentType.valueOf(dto.paymentType().type().toUpperCase())).iban(dto.paymentType().iban()).build())
                .orderItems(dto.orderItems().stream().map(this::mapItemToEntity).toList())
                .build();
    }

    private String generateHash(OrderCreateDTO request) {
        try {
            // Simple approach: Hash the toString() or serialize to JSON string
            String data = request.toString();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }

    private OrderItems mapItemToEntity(OrderItemDTO itemDto) {
        return OrderItems.builder()
                .productOfferingId(itemDto.productOfferingId())
                .quantity(itemDto.quantity())
                .build();
    }

    private boolean isDataBeingModified(CustomerOrder existingOrder, OrderPatchDTO patch) {
        // Check Category
        if (patch.category() != null && !OrderCategory.valueOf(patch.category().toUpperCase()).equals(existingOrder.getCategory())) {
            return true;
        }

        // Check Customer
        if (patch.customer() != null && !patch.customer().id().equals(existingOrder.getCustomer().getId())) {
            return true;
        }

        // Check Site
        if (patch.site() != null && !patch.site().id().equals(existingOrder.getSite().getId())) {
            return true;
        }

        // Check Order Items (Comparing lists)
        if (patch.orderItems() != null) {
            // Map DTO orders to a comparable format or compare directly
            if(patch.orderItems().size()!=existingOrder.getOrderItems().size()){
                return true;
            }
            List<OrderItems> mappedItems = patch.orderItems().stream().map(this::mapItemToEntity).toList();
            if (!mappedItems.equals(existingOrder.getOrderItems())) {
                return true;
            }
        }

        // Check Payment Method
        if (patch.paymentType() != null) {
            PaymentMethod patchPayment = PaymentMethod.builder()
                    .paymentType(PaymentType.valueOf(patch.paymentType().type().toUpperCase()))
                    .iban(patch.paymentType().iban())
                    .build();
            if (!patchPayment.equals(existingOrder.getPaymentMethod())) {
                return true;
            }
        }

        return false;
    }

    private void applyDataUpdates(CustomerOrder existingOrder, OrderPatchDTO patchOrder) {

        if (patchOrder.category() != null) existingOrder.setCategory(OrderCategory.valueOf(patchOrder.category().toUpperCase()));

        if (patchOrder.customer() != null) {
            existingOrder.setCustomer(Customer.builder().id(patchOrder.customer().id()).build());
        }

        if (patchOrder.site() != null) {
            existingOrder.setSite(Site.builder().id(patchOrder.site().id()).build());
        }

        if (patchOrder.orderItems() != null) {
            existingOrder.getOrderItems().clear();
            existingOrder.getOrderItems().addAll(
                    patchOrder.orderItems().stream()
                            .map(this::mapItemToEntity)
                            .toList()
            );        }

        if (patchOrder.paymentType() != null) {
            existingOrder.setPaymentMethod(PaymentMethod.builder().paymentType(PaymentType.valueOf(patchOrder.paymentType().type().toUpperCase())).iban(patchOrder.paymentType().iban()).build());
        }
    }

}