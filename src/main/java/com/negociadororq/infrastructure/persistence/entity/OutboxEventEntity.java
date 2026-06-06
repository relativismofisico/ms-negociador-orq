package com.negociadororq.infrastructure.persistence.entity;

import jakarta.persistence.Lob;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "outbox_event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventEntity {

    @Id
    private String id;

    private String eventType;

    private String aggregateId;

    private String tipoEvento;
    private String msPropietario;

    @Lob
    private String payload;

    private Boolean enviado = false;

    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaEnvio;

    private Integer intentos = 0;
}
