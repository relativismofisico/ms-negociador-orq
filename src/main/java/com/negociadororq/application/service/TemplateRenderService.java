package com.negociadororq.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface TemplateRenderService {

    String renderEndosoFormalTemplate(
            String ciudadNegociacion,
            String nombrePagador,
            String direccionPagador,
            String ciudadPagador,
            String nombreProveedor,
            String nitProveedor,
            String numeroFactura,
            String tipoOperacion,
            String nombreFondeador,
            String rutFondeador,
            LocalDateTime fechaOperacion
    );
}
