package com.customer.order.service.database.repositories;

import com.customer.order.service.database.entities.ProductOfferings;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductOfferingRepository extends JpaRepository<ProductOfferings, String> {

    // Using a projection to only fetch the IDs, not the whole entity
    @Query("SELECT p.id FROM ProductOfferings p WHERE p.id IN :ids")
    List<String> findExistingIds(@Param("ids") List<String> ids);
}
