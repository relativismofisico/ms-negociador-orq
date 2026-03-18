package com.negociadororq.infrastructure.pdf;

import com.negociadororq.domain.model.FirmaOtpMetadata;

public interface PdfFirmaService {
    byte[] agregarFirma(byte[] pdfBytes, FirmaOtpMetadata firma, int indexFirma);
}
