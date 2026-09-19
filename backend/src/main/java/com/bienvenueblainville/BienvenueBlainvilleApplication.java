package com.bienvenueblainville;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BienvenueBlainvilleApplication {
    public static void main(String[] args) {
        SpringApplication.run(BienvenueBlainvilleApplication.class, args);
    }
}

