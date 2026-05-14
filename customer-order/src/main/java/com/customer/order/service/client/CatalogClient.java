package com.customer.order.service.client;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class CatalogClient {
    private final RestClient restClient;
    public CatalogClient(RestClient.Builder builder, @Value("${catalog.service.url.base}") String baseUrl) {
        log.info(baseUrl);
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public void verifyOfferingExists(List<String> offeringIds) {
        try{
            restClient.post()
                    .uri("/product-offerings/validate")
                    .body(offeringIds)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            throw new NoSuchElementException(e.getResponseBodyAsString());
        }
        catch (HttpServerErrorException | ResourceAccessException e){
            throw new RuntimeException("Product catalog service is unreachable: " + e.getMessage());
        }
    }

}
