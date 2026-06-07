package com.negociadororq.runner;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression: flujos de negocio completos (smoke incluido al usar @regression).
 * Requiere servidor activo con datos mínimos de prueba.
 * Ejecución: ./gradlew regressionTest
 */
class RegressionRunner {

    @Test
    void runRegression() {
        Results results = Runner
                .path("classpath:com/negociadororq")
                .tags("@regression")
                .parallel(2);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }
}