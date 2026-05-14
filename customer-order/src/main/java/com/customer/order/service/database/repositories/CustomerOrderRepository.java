package com.customer.order.service.database.repositories;

import com.customer.order.service.database.entities.CustomerOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {
    Page<CustomerOrder> findByCategory(String category, Pageable pageable);

}
