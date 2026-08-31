package com.negociadororq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada del microservicio orquestador de negociación de Factoring/Confirming.
 */
@EnableScheduling
@SpringBootApplication
public class MsNegociadorOrqApplication {

    /**
     * @param args argumentos de línea de comandos, delegados a Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(MsNegociadorOrqApplication.class, args);
    }
}

