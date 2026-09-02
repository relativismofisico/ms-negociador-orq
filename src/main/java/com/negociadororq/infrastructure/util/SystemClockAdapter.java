package com.negociadororq.infrastructure.util;

import java.time.LocalDateTime;

import com.negociadororq.application.port.output.ClockPort;
import org.springframework.stereotype.Component;

/** Implementación real de {@link ClockPort}: delega en el reloj del sistema. */
@Component
public class SystemClockAdapter implements ClockPort {

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}
