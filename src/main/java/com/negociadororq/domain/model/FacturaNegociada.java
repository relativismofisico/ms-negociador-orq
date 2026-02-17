package com.negociadororq.domain.model;

import com.negociadororq.enums.EstadoFirma;
import com.negociadororq.enums.EstadoNegociacion;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.ArrayList;


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

}
