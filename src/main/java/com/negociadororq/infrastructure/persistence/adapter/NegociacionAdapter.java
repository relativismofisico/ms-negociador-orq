package com.negociadororq.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;

import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.Operacion;
import com.negociadororq.infrastructure.persistence.entity.FacturaNegociadaEmbedded;
import com.negociadororq.infrastructure.persistence.entity.NegociacionDocument;
import com.negociadororq.infrastructure.persistence.entity.OperacionEmbedded;
import com.negociadororq.infrastructure.persistence.repository.NegociacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link NegociacionOutputPort}.
 */
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
                .tipoOperacion(toDomain(doc.getTipoOperacion()))
                .rutEmpresa(doc.getRutEmpresa())
                .rutPagador(doc.getRutPagador())
                .rutProveedor(doc.getRutProveedor())
                .rutFondeadores(doc.getRutFondeadores())
                .rutPagadores(doc.getRutPagadores())
                .totalNegociado(doc.getTotalNegociado())
                .facturas(toDomainFacturas(doc.getFacturas()))
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
                .tipoOperacion(toEmbedded(n.getTipoOperacion()))
                .rutEmpresa(n.getRutEmpresa())
                .rutPagador(n.getRutPagador())
                .rutProveedor(n.getRutProveedor())
                .rutFondeadores(n.getRutFondeadores())
                .rutPagadores(n.getRutPagadores())
                .totalNegociado(n.getTotalNegociado())
                .facturas(toEmbeddedFacturas(n.getFacturas()))
                .estado(n.getEstado())
                .errores(n.getErrores())
                .fechaCreacion(n.getFechaCreacion())
                .fechaUltimaActualizacion(n.getFechaUltimaActualizacion())
                .aprobaciones(n.getAprobaciones())
                .build();
    }

    private Operacion toDomain(OperacionEmbedded embedded) {
        if (embedded == null) {
            return null;
        }
        return Operacion.builder()
                .tipo(embedded.getTipo())
                .clase(embedded.getClase())
                .build();
    }

    private OperacionEmbedded toEmbedded(Operacion operacion) {
        if (operacion == null) {
            return null;
        }
        return OperacionEmbedded.builder()
                .tipo(operacion.getTipo())
                .clase(operacion.getClase())
                .build();
    }

    private FacturaNegociada toDomain(FacturaNegociadaEmbedded embedded) {
        return FacturaNegociada.builder()
                .numeroFactura(embedded.getNumeroFactura())
                .valorFactura(embedded.getValorFactura())
                .estadoNegociacion(embedded.getEstadoNegociacion())
                .estadoFirma(embedded.getEstadoFirma())
                .rutPagador(embedded.getRutPagador())
                .rutProveedor(embedded.getRutProveedor())
                .fondeadores(embedded.getFondeadores())
                .aprobaciones(embedded.getAprobaciones())
                .estadoEndoso(embedded.getEstadoEndoso())
                .estadoCreacionEndoso(embedded.getEstadoCreacionEndoso())
                .otpEndoso(embedded.getOtpEndoso())
                .fechaOtp(embedded.getFechaOtp())
                .build();
    }

    private FacturaNegociadaEmbedded toEmbedded(FacturaNegociada factura) {
        return FacturaNegociadaEmbedded.builder()
                .numeroFactura(factura.getNumeroFactura())
                .valorFactura(factura.getValorFactura())
                .estadoNegociacion(factura.getEstadoNegociacion())
                .estadoFirma(factura.getEstadoFirma())
                .rutPagador(factura.getRutPagador())
                .rutProveedor(factura.getRutProveedor())
                .fondeadores(factura.getFondeadores())
                .aprobaciones(factura.getAprobaciones())
                .estadoEndoso(factura.getEstadoEndoso())
                .estadoCreacionEndoso(factura.getEstadoCreacionEndoso())
                .otpEndoso(factura.getOtpEndoso())
                .fechaOtp(factura.getFechaOtp())
                .build();
    }

    private List<FacturaNegociada> toDomainFacturas(List<FacturaNegociadaEmbedded> embeddedList) {
        if (embeddedList == null) {
            return List.of();
        }
        return embeddedList.stream().map(this::toDomain).toList();
    }

    private List<FacturaNegociadaEmbedded> toEmbeddedFacturas(List<FacturaNegociada> facturas) {
        if (facturas == null) {
            return List.of();
        }
        return facturas.stream().map(this::toEmbedded).toList();
    }
}
