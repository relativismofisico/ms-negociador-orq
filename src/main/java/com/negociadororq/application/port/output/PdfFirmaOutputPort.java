package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.FirmaOtpMetadata;

/**
 * Puerto para estampar una firma electrónica (OTP) sobre un PDF ya generado.
 */
public interface PdfFirmaOutputPort {

    /**
     * @param pdfBytes  contenido del PDF sobre el que se estampa la firma
     * @param firma     metadata de la firma OTP a estampar
     * @param indexFirma posición/orden de esta firma entre las del documento
     * @return el PDF resultante con la firma estampada
     */
    byte[] agregarFirma(byte[] pdfBytes, FirmaOtpMetadata firma, int indexFirma);
}
