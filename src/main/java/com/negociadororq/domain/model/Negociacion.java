package com.negociadororq.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Negociacion {

    private String id;
    private Operacion tipoOperacion;
    private String rutEmpresa;
    private String rutPagador;
    private String rutProveedor;
    private List<String> rutFondeadores;
    private List<String> rutPagadores;
    private BigDecimal totalNegociado;
    private List<FacturaNegociada> facturas;
    private String estado;
    private List<String> errores;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaUltimaActualizacion;
    private Map<String, Boolean> aprobaciones;
}
