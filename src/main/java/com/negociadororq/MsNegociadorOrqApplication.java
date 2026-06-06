package com.negociadororq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MsNegociadorOrqApplication {
    public static void main(String[] args) {
        SpringApplication.run(MsNegociadorOrqApplication.class, args);
    }
}

