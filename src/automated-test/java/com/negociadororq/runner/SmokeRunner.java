package com.negociadororq.runner;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Smoke: auth + validaciones básicas de contrato HTTP.
 * No requiere datos en BD. Apto para producción (combinado con ProdRunner).
 * Ejecución: ./gradlew smokeTest
 */
class SmokeRunner {

    @Test
    void runSmoke() {
        Results results = Runner
                .path("classpath:com/negociadororq")
                .tags("@smoke")
                .parallel(2);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }
}