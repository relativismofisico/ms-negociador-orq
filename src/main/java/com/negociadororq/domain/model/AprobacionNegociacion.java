package com.negociadororq.domain.model;

import java.time.LocalDateTime;
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
@Document(collection = "aprobaciones_negociacion")
public class AprobacionNegociacion {

    @Id
    private String id;

    private String negociacionId;
    private String rutEmpresa;
    private String rol; // PAGADOR, PROVEEDOR, FONDEADOR
    private boolean aprobado;
    private String observacion;
    private LocalDateTime fecha;
}
