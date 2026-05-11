package com.negociadororq.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
}
