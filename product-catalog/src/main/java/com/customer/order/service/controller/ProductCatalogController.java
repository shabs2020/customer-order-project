package com.customer.order.service.controller;

import com.customer.order.service.database.entities.ProductOfferings;
import com.customer.order.service.services.ProductCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/product-offerings")
@RequiredArgsConstructor
public class ProductCatalogController {
    private final ProductCatalogService catalogService;

    @GetMapping
    public List<ProductOfferings> getAll() {
        return catalogService.getAllProducts();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductOfferings> getById(@PathVariable String id) {
        return catalogService.getProductById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @PostMapping("/validate")
    @ResponseStatus(HttpStatus.NO_CONTENT) // Returns 204 if no exception is thrown
    public void validateProducts(@RequestBody List<String> productIds) {
        catalogService.verifyProductsExist(productIds);
    }
}
