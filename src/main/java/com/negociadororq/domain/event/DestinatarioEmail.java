package com.negociadororq.domain.event;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DestinatarioEmail {

    private String rolActor; // PAGADOR, PROVEEDOR, FONDEADOR
    private String rutActor;
    private String tipoActor;
}
