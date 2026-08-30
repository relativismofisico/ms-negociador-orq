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
}

