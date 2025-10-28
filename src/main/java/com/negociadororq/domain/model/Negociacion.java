package com.negociadororq.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import org.springframework.data.annotation.Id;
import java.util.ArrayList;
import java.util.Map;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "negociacion")
public class Negociacion {

    @Id
    private String id;

    private Operacion tipoOperacion; // FACTORING o CONFIRMING

    private String rutEmpresa;   // empresa que negocia
    private String rutPagador;
    private String rutProveedor;

    // Podría ser una lista si hay múltiples fondeadores
    private List<String> rutFondeadores;

    private BigDecimal totalNegociado;

    private List<FacturaNegociada> facturas;

    private String estado; // PENDIENTE, ACEPTADA, RECHAZADA

    private List<String> errores; // errores de facturas rechazadas

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaUltimaActualizacion;

    private Map<String, Boolean> aprobaciones;
    // Key = rutEmpresa, Value = true (aprobado) / false (rechazado)

}
