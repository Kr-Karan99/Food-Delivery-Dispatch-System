package com.delivery.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class DeliverySystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliverySystemApplication.class, args);
    }

}
