package com.negociadororq.domain.event;

import com.negociadororq.domain.model.Operacion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


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

