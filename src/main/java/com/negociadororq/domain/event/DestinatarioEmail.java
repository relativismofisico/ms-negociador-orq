package com.negociadororq.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un destinatario de una notificación por email, identificado por su rol y RUT dentro
 * de la negociación (pagador, proveedor o fondeador).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DestinatarioEmail {
    private String rolActor;
    private String rutActor;
    private String tipoActor;
}

