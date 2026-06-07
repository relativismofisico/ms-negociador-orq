package com.negociadororq.runner;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Full: suite completa incluyendo escenarios de OTP y flujos críticos.
 * Requiere ambiente de integración completo (BD + Kafka + dependencias).
 * Ejecución: ./gradlew automatedTest  (task principal)
 */
class FullRunner {

    @Test
    void runFull() {
        Results results = Runner
                .path("classpath:com/negociadororq")
                .tags("@full")
                .parallel(4);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }
}