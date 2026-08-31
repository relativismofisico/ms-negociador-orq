package com.negociadororq.runner;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Production: SOLO pruebas de lectura sin efectos secundarios en producción.
 * Requiere: BASE_URL, TEST_TOKEN_ADMIN, TEST_TOKEN_EMPRESA,
 *           TEST_TOKEN_FONDEADOR, TEST_TOKEN_OPERARIO como env vars.
 * Ejecución: AMBIENTE_PIPE=prod BASE_URL=https://... ./gradlew productionTest
 */
class ProductionRunner {

    @Test
    void runProduction() {
        Results results = Runner
                .path("classpath:com/negociadororq")
                .tags("@production-safe")
                .parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }
}