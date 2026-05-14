package com.customer.order.service.database.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "idempotency_keys", indexes = {@Index(columnList = "idempotencyKey")})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyKey{
    @Id
    @Column(name = "idempotency_key", length = 255, unique = true)
    private String key;

    @Column(nullable = false)
    private String requestHash;

    private UUID orderId;

    @Column(nullable = false)
    private LocalDateTime expiryDate;
}
