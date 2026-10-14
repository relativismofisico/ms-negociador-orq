package com.negociadororq.domain.event;

import java.time.LocalDateTime;

import com.negociadororq.domain.model.Operacion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * Evento publicado a Kafka para solicitar la liquidación de una factura ya negociada.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LiquidacionSolicitadaEvent {
    private String negociacionId;
    private String rutOperador;
    private String numeroFactura;
    private Operacion tipoOperacion;
    private LocalDateTime fecha;

    /**
     * Marca de trazabilidad: la solicitud de OTP que derivó en esta liquidación
     * pasó el guard de ownership de origen (CompanyOwnershipGuard). El consumidor
     * (ms-liquidador-neg) no revalida, pero puede alertar si llega en false.
     */
    private boolean validadoEnOrigen;

    /**
     * requestId de la solicitud del OTP que derivó en esta liquidación: ms-liquidador-neg lo pone en su MDC para
     * correlacionar sus logs y su auditoría con los de la aprobación.
     */
    private String requestId;
}

