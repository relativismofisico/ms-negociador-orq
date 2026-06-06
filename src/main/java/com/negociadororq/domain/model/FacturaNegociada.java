package com.negociadororq.domain.model;

import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.enums.EstadoNegociacion;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacturaNegociada {

    private String numeroFactura;
    private BigDecimal valorFactura;


    private EstadoNegociacion estadoNegociacion;
    private EstadoFirma estadoFirma;

    private String rutPagador;
    private String rutProveedor;
    // Cada factura puede tener uno o varios fondeadores asociados
    private List<FondeadorAsignacion> fondeadores;

    // Historial de aprobaciones (puede haber varias, según los roles que participen)
    private List<AprobacionActorDto> aprobaciones;
    //private EstadoFacturaNegociada estado;
    private EstadoEndoso estadoEndoso;
    private Boolean estadoCreacionEndoso;

    private String otpEndoso;

    private LocalDateTime fechaOtp;

}

