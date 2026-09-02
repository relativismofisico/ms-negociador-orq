package com.negociadororq.application.service;


import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.EndosoOutputPort;
import com.negociadororq.application.port.output.PdfFirmaOutputPort;
import com.negociadororq.domain.enums.RolActor;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FirmaOtpMetadata;
import com.negociadororq.domain.model.PendingOtp;
import lombok.RequiredArgsConstructor;

/**
 * Implementación de {@link FirmaEndosoService}.
 */
@RequiredArgsConstructor
public class FirmaEndosoServiceImpl implements FirmaEndosoService {

    private final EndosoOutputPort endosoPort;
    private final PdfFirmaOutputPort pdfFirmaPort;
    private final ClockPort clock;

    @Override
    public void firmarPdfEndoso(String idNegociacion, FacturaNegociada factura, PendingOtp pending, String rutEmpresa,
                                String rutOperador, RolActor rolActor, boolean aprobado, String observacion) {

        Endoso endoso = endosoPort
                .findByNegociacionIdAndNumeroFactura(idNegociacion, factura.getNumeroFactura())
                .orElseThrow(() -> new RuntimeException("Endoso no encontrado"));

        byte[] pdfActual = endoso.getDocumentoPdf();

        // OJO: el índice de posición de la firma debe salir de endoso.getAprobaciones()
        // (el contador atado al documento físico donde realmente se estampa el bloque
        // de texto), NO de factura.getAprobaciones(). Con endosoGrupal=true, varias
        // facturas comparten el mismo Endoso/documentoPdf pero cada una lleva su propio
        // contador de aprobaciones independiente — usar el de la factura hacía que cada
        // firma nueva reiniciara en índice 0 y se estampara encima de la anterior.
        int indexFirma = 0;
        if (endoso.getAprobaciones() != null) {
            indexFirma = endoso.getAprobaciones().size();
        }

        // 2. Metadata de firma
        FirmaOtpMetadata firma = FirmaOtpMetadata.builder()
                .rutEmpresa(rutEmpresa)
                .rutOperador(rutOperador)
                .rolActor(rolActor.name())
                .otp(pending.getOtp())
                .ip(pending.getIp())
                .aprobado(aprobado)
                .fecha(clock.now())
                .observacion(observacion)
                .build();

        byte[] pdfFirmado = pdfFirmaPort.agregarFirma(pdfActual, firma, indexFirma);

        endoso.setDocumentoPdf(pdfFirmado);
        endosoPort.save(endoso);
    }
}

