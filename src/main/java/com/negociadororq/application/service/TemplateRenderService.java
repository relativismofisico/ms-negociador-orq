package com.negociadororq.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface TemplateRenderService {

    String renderEndosoTemplate(String rutProveedor, String rutPagador, String rutFondeador,
            LocalDate fechaPago, LocalDateTime fechaSolicitud);
}
