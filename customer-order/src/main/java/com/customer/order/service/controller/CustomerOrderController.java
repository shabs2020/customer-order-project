package com.customer.order.service.controller;

import com.customer.order.service.dto.OrderCreateDTO;
import com.customer.order.service.dto.OrderListResponseDTO;
import com.customer.order.service.dto.OrderPatchDTO;
import com.customer.order.service.dto.OrderResponseDTO;
import com.customer.order.service.dto.OrderResponseIdempotencyDTO;
import com.customer.order.service.service.OrderService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customer-orders")
@AllArgsConstructor
public class CustomerOrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponseDTO> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid OrderCreateDTO request) {

        OrderResponseIdempotencyDTO createOrderResponse = orderService.createOrder(request, idempotencyKey);
        if (createOrderResponse.isReplay()) {
            return ResponseEntity.ok()
                    .header("X-Is-Replay", "true")
                    .body(createOrderResponse.dto());
        }
        return new ResponseEntity<>(createOrderResponse.dto(), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> getOrder(@PathVariable UUID id) throws BadRequestException {
        try{
            return ResponseEntity.ok(orderService.getOrderById(id));
        }
        catch (IllegalArgumentException e){
            throw new BadRequestException("Bad input: invalid UUID");
        }
    }

    @GetMapping
    public ResponseEntity<OrderListResponseDTO> listOrders(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(orderService.listOrders(category, limit, offset));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> updateOrder(
            @PathVariable UUID id,
            @RequestBody OrderPatchDTO patchOrder) {
        return ResponseEntity.ok(orderService.patchOrder(id, patchOrder));
    }
}
