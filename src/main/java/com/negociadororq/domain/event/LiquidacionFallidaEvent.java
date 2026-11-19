package com.negociadororq.domain.event;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento publicado a Kafka cuando falla la liquidación de una factura, para que
 * el consumer correspondiente revierta el cupo consumido y notifique al operador.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiquidacionFallidaEvent {
    private String numeroFactura;
    private String motivo;
    private String tipoOperacion;
    private String rutOperador;
    private LocalDateTime fecha;
    /** Fase 28d (C): negociación de la factura (ms-liquidador-neg lo toma de liquidacion.solicitada). */
    private String negociacionId;
    /** Fase 28d (C): CUFE de la factura; sin él (o sin negociacionId) la reversión no se hace y el evento falla. */
    private String cufe;
}

