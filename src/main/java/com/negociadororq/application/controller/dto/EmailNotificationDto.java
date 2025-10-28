package com.negociadororq.application.controller.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailNotificationDto {
    private String tipoOperacion;       // FACTORING o CONFIRMING
    private String destinatario;        // correo principal (pagador o proveedor)
    private List<String> fondeadores;   // correos fondeadores
    private List<String> facturas;      // facturas negociadas
    private String asunto;
    private String mensaje;
}
