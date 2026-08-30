package com.negociadororq.infrastructure.persistence.adapter;

import java.util.Optional;

import com.negociadororq.application.port.output.EndosoOutputPort;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.infrastructure.persistence.entity.EndosoDocument;
import com.negociadororq.infrastructure.persistence.repository.EndosoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link EndosoOutputPort}.
 */
@Component
@RequiredArgsConstructor
public class EndosoAdapter implements EndosoOutputPort {

    private final EndosoRepository repository;

    @Override
    public boolean existsByNegociacionIdAndNumeroFactura(String negociacionId, String numeroFactura) {
        return repository.existsByNegociacionIdAndNumerosFacturaContains(negociacionId, numeroFactura);
    }

    @Override
    public Optional<Endoso> findByNegociacionIdAndNumeroFactura(String negociacionId, String numeroFactura) {
        return repository.findByNegociacionIdAndNumerosFacturaContains(negociacionId, numeroFactura).map(this::toDomain);
    }

    @Override
    public Endoso save(Endoso endoso) {
        return toDomain(repository.save(toDocument(endoso)));
    }

    private Endoso toDomain(EndosoDocument doc) {
        return Endoso.builder()
                .id(doc.getId())
                .negociacionId(doc.getNegociacionId())
                .numerosFactura(doc.getNumerosFactura())
                .rutActorEndosa(doc.getRutActorEndosa())
                .rutProveedor(doc.getRutProveedor())
                .rutPagador(doc.getRutPagador())
                .rutFondeador(doc.getRutFondeador())
                .tipoOperacion(doc.getTipoOperacion())
                .estado(doc.getEstado())
                .otp(doc.getOtp())
                .aprobaciones(doc.getAprobaciones())
                .documentoUrl(doc.getDocumentoUrl())
                .fechaCreacion(doc.getFechaCreacion())
                .fechaAprobacion(doc.getFechaAprobacion())
                .fechaActualizacion(doc.getFechaActualizacion())
                .documentoHtml(doc.getDocumentoHtml())
                .documentoPdf(doc.getDocumentoPdf())
                .nombreArchivoPdf(doc.getNombreArchivoPdf())
                .build();
    }

    private EndosoDocument toDocument(Endoso e) {
        return EndosoDocument.builder()
                .id(e.getId())
                .negociacionId(e.getNegociacionId())
                .numerosFactura(e.getNumerosFactura())
                .rutActorEndosa(e.getRutActorEndosa())
                .rutProveedor(e.getRutProveedor())
                .rutPagador(e.getRutPagador())
                .rutFondeador(e.getRutFondeador())
                .tipoOperacion(e.getTipoOperacion())
                .estado(e.getEstado())
                .otp(e.getOtp())
                .aprobaciones(e.getAprobaciones())
                .documentoUrl(e.getDocumentoUrl())
                .fechaCreacion(e.getFechaCreacion())
                .fechaAprobacion(e.getFechaAprobacion())
                .fechaActualizacion(e.getFechaActualizacion())
                .documentoHtml(e.getDocumentoHtml())
                .documentoPdf(e.getDocumentoPdf())
                .nombreArchivoPdf(e.getNombreArchivoPdf())
                .build();
    }
}
