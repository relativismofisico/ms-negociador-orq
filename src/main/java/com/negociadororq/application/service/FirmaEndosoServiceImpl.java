package com.negociadororq.application.service;

import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FirmaOtpMetadata;
import com.negociadororq.domain.model.PendingOtp;
import com.negociadororq.enums.RolActor;
import com.negociadororq.infrastructure.pdf.PdfFirmaService;
import com.negociadororq.infrastructure.persistence.repository.EndosoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FirmaEndosoServiceImpl implements FirmaEndosoService {

    private final EndosoRepository endosoRepository;
    private final PdfFirmaService pdfFirmaService;

    @Override
    public void firmarPdfEndoso(String idNegociacion, FacturaNegociada factura, PendingOtp pending, String rutEmpresa,
                                String rutOperador, RolActor rolActor, boolean aprobado, String observacion) {

        // 1. Obtener endoso
        Endoso endoso = endosoRepository
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

        // 3. Firmar PDF
        byte[] pdfFirmado = pdfFirmaService.agregarFirma(pdfActual, firma, indexFirma);

        // 4. Guardar
        endoso.setDocumentoPdf(pdfFirmado);
        endosoRepository.save(endoso);
    }
}
