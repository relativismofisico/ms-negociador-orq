package com.negociadororq.application.service;

import com.negociadororq.domain.model.MessageTemplate;
import com.negociadororq.infrastructure.persistence.repository.MessageTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Service
@RequiredArgsConstructor
public class TemplateRenderServiceImpl implements  TemplateRenderService{

    private final MessageTemplateRepository templateRepository;


    @Override
    public String renderEndosoTemplate(String rutProveedor, String rutPagador, String rutFondeador, LocalDate fechaPago, LocalDateTime fechaSolicitud) {
        MessageTemplate template = templateRepository
                .findByTemplateName("Endoso")
                .orElseThrow(() -> new RuntimeException("Plantilla Endoso no encontrada"));

        return template.getContent()
                .replace("{{RUT_PROVEEDOR}}", rutProveedor)
                .replace("{{RUT_PAGADOR}}", rutPagador)
                .replace("{{RUT_FONDEADOR}}", rutFondeador)
                .replace("{{FECHA_PAGO}}", fechaPago != null ? fechaPago.toString() : "N/A")
                .replace("{{FECHA_SOLICITUD}}", fechaSolicitud.toString());
    }
}
