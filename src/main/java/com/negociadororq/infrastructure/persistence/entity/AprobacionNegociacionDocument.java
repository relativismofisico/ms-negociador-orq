package com.negociadororq.infrastructure.persistence.entity;

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
public class AprobacionNegociacionDocument {

    @Id
    private String id;
    private String negociacionId;
    private String rutEmpresa;
    private String rol;
    private boolean aprobado;
    private String observacion;
    private LocalDateTime fecha;
}
