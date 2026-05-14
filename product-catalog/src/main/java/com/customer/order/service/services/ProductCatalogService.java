package com.customer.order.service.services;

import com.customer.order.service.database.entities.ProductOfferings;
import com.customer.order.service.database.repositories.ProductOfferingRepository;
import com.customer.order.service.exceptions.ProductNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductCatalogService {
    private final ProductOfferingRepository repository;

    public List<ProductOfferings> getAllProducts() {
        return repository.findAll();
    }

    public Optional<ProductOfferings> getProductById(String id) {
        return repository.findById(id);
    }

    public void verifyProductsExist(List<String> productIds) {

        Set<String> existingIdsSet = new HashSet<>(repository.findExistingIds(productIds));
        for(String offeringId : productIds){
            if(!existingIdsSet.contains(offeringId)){
                throw new ProductNotFoundException(offeringId);
            }
        }
    }
}

