package com.negociadororq.infrastructure.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Lob;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Persistencia MongoDB de un evento pendiente de publicar según el patrón Outbox: se guarda
 * junto con el cambio de negocio y un job aparte lo envía y marca {@code enviado}, evitando
 * perder eventos si la publicación falla.
 */
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

    @Builder.Default
    private Boolean enviado = false;

    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaEnvio;

    @Builder.Default
    private Integer intentos = 0;
}

