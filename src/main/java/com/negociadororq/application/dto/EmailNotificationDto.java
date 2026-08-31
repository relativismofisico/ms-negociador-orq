package com.negociadororq.application.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Datos para armar y enviar una notificación por correo sobre una negociación. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailNotificationDto {
    /** FACTORING o CONFIRMING. */
    private String tipoOperacion;

    /** Correo principal del destinatario (pagador o proveedor). */
    private String destinatario;

    /** Correos de los fondeadores a notificar en copia. */
    private List<String> fondeadores;

    /** Facturas negociadas incluidas en la notificación. */
    private List<String> facturas;

    private String asunto;
    private String mensaje;
}

