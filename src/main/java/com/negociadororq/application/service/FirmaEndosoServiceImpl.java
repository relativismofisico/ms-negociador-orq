package com.negociadororq.application.service;

import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FirmaOtpMetadata;
import com.negociadororq.domain.model.PendingOtp;
import com.negociadororq.domain.enums.RolActor;
import com.negociadororq.application.port.output.EndosoOutputPort;
import com.negociadororq.application.port.output.PdfFirmaOutputPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FirmaEndosoServiceImpl implements FirmaEndosoService {

    private final EndosoOutputPort endosoPort;
    private final PdfFirmaOutputPort pdfFirmaPort;

    @Override
    public void firmarPdfEndoso(String idNegociacion, FacturaNegociada factura, PendingOtp pending, String rutEmpresa,
                                String rutOperador, RolActor rolActor, boolean aprobado, String observacion) {

        Endoso endoso = endosoPort
                .findByNegociacionIdAndNumeroFactura(idNegociacion, factura.getNumeroFactura())
                .orElseThrow(() -> new RuntimeException("Endoso no encontrado"));

        byte[] pdfActual = endoso.getDocumentoPdf();

        int indexFirma = factura.getAprobaciones().size();

        // 2. Metadata de firma
        FirmaOtpMetadata firma = FirmaOtpMetadata.builder()
                .rutEmpresa(rutEmpresa)
                .rutOperador(rutOperador)
                .rolActor(rolActor.name())
                .otp(pending.getOtp())
                .ip(pending.getIp())
                .aprobado(aprobado)
                .fecha(LocalDateTime.now())
                .observacion(observacion)
                .build();

        byte[] pdfFirmado = pdfFirmaPort.agregarFirma(pdfActual, firma, indexFirma);

        endoso.setDocumentoPdf(pdfFirmado);
        endosoPort.save(endoso);
    }
}

