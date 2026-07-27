package com.negociadororq.application.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TemplateRenderService {

    record FacturaEndosoTemplateData(String numeroFactura, BigDecimal valorFactura) {
    }

    /**
     * Datos de la persona natural que firma el documento en representación de
     * la empresa proveedora. Puede ser null si todavía no se conoce (en
     * Confirming, quien solicita es el Pagador; el firmante se resuelve
     * cuando el Proveedor aprueba y el documento se regenera).
     */
    record DatosFirmante(String nombreCompleto, String tipoDocumento, String numeroDocumento) {
    }

    /**
     * Renderiza el documento formal de endoso/descuento a partir de la plantilla
     * almacenada en messages_templates (no en el código, ver ENDOSO_DOCUMENTO),
     * cubriendo una o varias facturas del mismo fondeador.
     */
    String renderEndosoFormalTemplate(
            String nombrePagador,
            String rutPagador,
            String nombreProveedor,
            String rutProveedor,
            List<FacturaEndosoTemplateData> facturas,
            String tipoOperacion,
            String claseOperacion,
            String nombreFondeador,
            String rutFondeador,
            LocalDateTime fechaOperacion,
            DatosFirmante firmante
    );
}
