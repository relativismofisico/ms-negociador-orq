package com.negociadororq.application.port.output;

/**
 * Puerto para generar un PDF a partir de HTML ya renderizado.
 */
public interface PdfGeneradorOutputPort {

    /**
     * @param html contenido HTML ya renderizado a convertir
     * @return el PDF generado
     */
    byte[] generarDesdePdfHtml(String html);
}
