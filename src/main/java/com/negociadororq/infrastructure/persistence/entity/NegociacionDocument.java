package com.negociadororq.infrastructure.persistence.entity;

import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Operacion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "negociacion")
public class NegociacionDocument {

    @Id
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
