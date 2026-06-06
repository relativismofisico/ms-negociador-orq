package com.negociadororq.application.port.output;

public interface PdfGeneradorOutputPort {
    byte[] generarDesdePdfHtml(String html);
}
