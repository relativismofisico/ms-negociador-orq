package com.negociadororq.application.port.output;

/** Alerta operativa de firmas por OTP que terminaron en {@code FALLO_FIRMA} (Fase 28c). */
public interface AlertaFirmaOutputPort {

    /** Registra una firma fallida (log {@code [ALERTA][FIRMA-FALLIDA]}, para el filtro de métrica de CloudWatch). */
    void firmaFallida();
}
