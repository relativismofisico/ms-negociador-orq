package com.negociadororq.infrastructure.pdf;

public interface PdfFirmaService {
    byte[] agregarFirma(byte[] pdfBytes, String textoFirma, int indexFirma);
}
