package com.negociadororq.application.port.output;

import java.time.LocalDateTime;

/**
 * Puerto de tiempo: sustituye llamadas directas a {@code LocalDate.now()}/
 * {@code LocalDateTime.now()} desde application/, para que la fecha/hora usada por
 * la lógica de negocio sea inyectable y determinística en los tests (sin
 * {@code Thread.sleep} ni dependencia del reloj real de la máquina).
 */
public interface ClockPort {

    /** Fecha y hora actual. */
    LocalDateTime now();
}
