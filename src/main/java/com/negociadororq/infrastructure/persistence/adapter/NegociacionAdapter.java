package com.negociadororq.infrastructure.persistence.adapter;

import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.infrastructure.persistence.entity.NegociacionDocument;
import com.negociadororq.infrastructure.persistence.repository.NegociacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NegociacionAdapter implements NegociacionOutputPort {

    private final NegociacionRepository repository;

    @Override
    public Optional<Negociacion> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Negociacion save(Negociacion negociacion) {
        return toDomain(repository.save(toDocument(negociacion)));
    }

    @Override
    public Optional<Negociacion> findByNumeroFactura(String numeroFactura) {
        return repository.findByNumeroFactura(numeroFactura).map(this::toDomain);
    }

    private Negociacion toDomain(NegociacionDocument doc) {
        return Negociacion.builder()
                .id(doc.getId())
                .tipoOperacion(doc.getTipoOperacion())
                .rutEmpresa(doc.getRutEmpresa())
                .rutPagador(doc.getRutPagador())
                .rutProveedor(doc.getRutProveedor())
                .rutFondeadores(doc.getRutFondeadores())
                .rutPagadores(doc.getRutPagadores())
                .totalNegociado(doc.getTotalNegociado())
                .facturas(doc.getFacturas())
                .estado(doc.getEstado())
                .errores(doc.getErrores())
                .fechaCreacion(doc.getFechaCreacion())
                .fechaUltimaActualizacion(doc.getFechaUltimaActualizacion())
                .aprobaciones(doc.getAprobaciones())
                .build();
    }

    private NegociacionDocument toDocument(Negociacion n) {
        return NegociacionDocument.builder()
                .id(n.getId())
                .tipoOperacion(n.getTipoOperacion())
                .rutEmpresa(n.getRutEmpresa())
                .rutPagador(n.getRutPagador())
                .rutProveedor(n.getRutProveedor())
                .rutFondeadores(n.getRutFondeadores())
                .rutPagadores(n.getRutPagadores())
                .totalNegociado(n.getTotalNegociado())
                .facturas(n.getFacturas())
                .estado(n.getEstado())
                .errores(n.getErrores())
                .fechaCreacion(n.getFechaCreacion())
                .fechaUltimaActualizacion(n.getFechaUltimaActualizacion())
                .aprobaciones(n.getAprobaciones())
                .build();
    }
}
