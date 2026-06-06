package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.FirmaOtpMetadata;

public interface PdfFirmaOutputPort {
    byte[] agregarFirma(byte[] pdfBytes, FirmaOtpMetadata firma, int indexFirma);
}
