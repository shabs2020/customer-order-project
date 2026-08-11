package com.customer.order.service.service;

import com.customer.order.service.database.entities.CustomerOrder;
import com.customer.order.service.database.entities.IdempotencyKey;
import com.customer.order.service.database.repositories.CustomerOrderRepository;
import com.customer.order.service.database.repositories.IdempotencyRepository;
import com.customer.order.service.dto.OrderResponseDTO;
import com.customer.order.service.dto.OrderResponseIdempotencyDTO;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service responsible for handling idempotency key validation, hashing, and replay detection.
 * Ensures that duplicate requests with the same idempotency key and payload are not processed twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final OrderMapper orderMapper;

    /**
     * Checks if the idempotency key exists and handles the request accordingly.
     */
    @Transactional
    public OrderResponseIdempotencyDTO checkAndLockIdempotency(
            String key, 
            String requestHash,
            OrderResponseDTO orderDto) {
        Optional<IdempotencyKey> existing = idempotencyRepository.findById(key);
        if (existing.isPresent()) {
            return handleExistingKey(existing.get(), requestHash);
        }
        
        try {
            IdempotencyKey newRecord = IdempotencyKey.builder()
                    .key(key)
                    .requestHash(requestHash)
                    .expiryDate(LocalDateTime.now().plusMinutes(5))
                    .build();
            idempotencyRepository.saveAndFlush(newRecord);
            return new OrderResponseIdempotencyDTO(orderDto, false);
        } catch (DataIntegrityViolationException e) {
            log.warn("Race condition detected for idempotency key: {}", key);
            Optional<IdempotencyKey> retried = idempotencyRepository.findById(key);
            if (retried.isPresent()) {
                return handleExistingKey(retried.get(), requestHash);
            }
            throw new RuntimeException("Failed to lock idempotency key: " + key, e);
        }
    }

    /**
     * Handles the case where an idempotency key already exists.
     */
    public OrderResponseIdempotencyDTO handleExistingKey(
            IdempotencyKey existingKey, 
            String currentHash) {
        if (existingKey.getRequestHash().equals(currentHash)) {
            if (existingKey.getOrderId() != null) {
                log.info("Replay detected for idempotency key: {}", existingKey.getKey());
                CustomerOrder order = customerOrderRepository.findById(existingKey.getOrderId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                "Order referenced by idempotency key not found"));
                return new OrderResponseIdempotencyDTO(orderMapper.toOrderResponseDTO(order), true);
            } else {
                log.warn("Request still processing for idempotency key: {}", existingKey.getKey());
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, 
                    "Request with idempotency key " + existingKey.getKey() + " is still processing");
            }
        } else {
            log.warn("Idempotency conflict: key {} reused with different payload", existingKey.getKey());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Idempotency key " + existingKey.getKey() + " already used with a different request");
        }
    }

    /**
     * Generates a SHA-256 hash of the request payload.
     */
    public String generateHash(Object request) {
        try {
            String data = request.toString();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }

    /**
     * Updates the idempotency key record with the order ID after successful creation.
     */
    @Transactional
    public void updateIdempotencyKeyWithOrderId(String key, UUID orderId) {
        idempotencyRepository.findById(key).ifPresent(idempotencyKey -> {
            idempotencyKey.setOrderId(orderId);
            idempotencyRepository.save(idempotencyKey);
        });
    }
}
