package com.customer.order.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(scanBasePackages = "${load_packages}")//,exclude = {SecurityAutoConfiguration.class})
@ComponentScan(basePackages = {"com.customer.order.service.*"})
public class CustomerOrderServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerOrderServerApplication.class, args);
    }

}
