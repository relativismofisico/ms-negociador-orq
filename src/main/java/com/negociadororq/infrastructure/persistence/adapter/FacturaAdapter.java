package com.negociadororq.infrastructure.persistence.adapter;

import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.infrastructure.persistence.entity.FacturaDocument;
import com.negociadororq.infrastructure.persistence.repository.FacturaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FacturaAdapter implements FacturaOutputPort {

    private final FacturaRepository repository;

    @Override
    public Optional<Factura> findByNumeroFactura(String numeroFactura) {
        return repository.findByNumeroFactura(numeroFactura).map(this::toDomain);
    }

    @Override
    public List<Factura> findByNumeroFacturaIn(List<String> numerosFactura) {
        return repository.findByNumeroFacturaIn(numerosFactura).stream()
                .map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public boolean existsByNumeroFacturaInAndRutPagador(List<String> numerosFactura, String rutPagador) {
        return repository.existsByNumeroFacturaInAndRutPagador(numerosFactura, rutPagador);
    }

    @Override
    public boolean existsByNumeroFacturaInAndRutProveedor(List<String> numerosFactura, String rutProveedor) {
        return repository.existsByNumeroFacturaInAndRutProveedor(numerosFactura, rutProveedor);
    }

    @Override
    public List<Factura> findFacturasPendientesVencimiento() {
        return repository.findFacturasPendientesVencimiento().stream()
                .map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Factura save(Factura factura) {
        return toDomain(repository.save(toDocument(factura)));
    }

    @Override
    public List<Factura> saveAll(List<Factura> facturas) {
        return repository.saveAll(facturas.stream().map(this::toDocument).collect(Collectors.toList()))
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    private Factura toDomain(FacturaDocument doc) {
        Factura f = new Factura();
        f.setId(doc.getId());
        f.setNumeroFactura(doc.getNumeroFactura());
        f.setCodigoAlternoFactura(doc.getCodigoAlternoFactura());
        f.setTipoIdentificacionPagador(doc.getTipoIdentificacionPagador());
        f.setRutPagador(doc.getRutPagador());
        f.setRutAlternoPagador(doc.getRutAlternoPagador());
        f.setNombrePagador(doc.getNombrePagador());
        f.setTipoIdentificacionProveedor(doc.getTipoIdentificacionProveedor());
        f.setRutProveedor(doc.getRutProveedor());
        f.setRutAlternoProveedor(doc.getRutAlternoProveedor());
        f.setNombreProveedor(doc.getNombreProveedor());
        f.setTipoDocumento(doc.getTipoDocumento());
        f.setFechaEmisionFactura(doc.getFechaEmisionFactura());
        f.setFechaRadicacionFactura(doc.getFechaRadicacionFactura());
        f.setFechavencimientoFactura(doc.getFechavencimientoFactura());
        f.setFechaEsperadaPagoPagador(doc.getFechaEsperadaPagoPagador());
        f.setMoneda(doc.getMoneda());
        f.setValorFactura(doc.getValorFactura());
        f.setIvaFactura(doc.getIvaFactura());
        f.setValorRetencionFactura(doc.getValorRetencionFactura());
        f.setValorNotaCredito(doc.getValorNotaCredito());
        f.setObservacionesFactura(doc.getObservacionesFactura());
        f.setValorAnticiposFactura(doc.getValorAnticiposFactura());
        f.setValorNetoFactura(doc.getValorNetoFactura());
        f.setTipoBeneficioFactura(doc.getTipoBeneficioFactura());
        f.setObservaciones(doc.getObservaciones());
        f.setFechaExtendidaConCosto(doc.getFechaExtendidaConCosto());
        f.setFechaNegociacionFactura(doc.getFechaNegociacionFactura());
        f.setValorDescuentoPagador(doc.getValorDescuentoPagador());
        f.setOriginalFileName(doc.getOriginalFileName());
        f.setPdfFileName(doc.getPdfFileName());
        f.setCufe(doc.getCufe());
        f.setEstadoNegociacion(doc.getEstadoNegociacion());
        f.setEstadoFirma(doc.getEstadoFirma());
        f.setActores(doc.getActores());
        f.setUsuarioCargaFactura(doc.getUsuarioCargaFactura());
        f.setEmpresaCargaFactura(doc.getEmpresaCargaFactura());
        return f;
    }

    private FacturaDocument toDocument(Factura f) {
        FacturaDocument doc = new FacturaDocument();
        doc.setId(f.getId());
        doc.setNumeroFactura(f.getNumeroFactura());
        doc.setCodigoAlternoFactura(f.getCodigoAlternoFactura());
        doc.setTipoIdentificacionPagador(f.getTipoIdentificacionPagador());
        doc.setRutPagador(f.getRutPagador());
        doc.setRutAlternoPagador(f.getRutAlternoPagador());
        doc.setNombrePagador(f.getNombrePagador());
        doc.setTipoIdentificacionProveedor(f.getTipoIdentificacionProveedor());
        doc.setRutProveedor(f.getRutProveedor());
        doc.setRutAlternoProveedor(f.getRutAlternoProveedor());
        doc.setNombreProveedor(f.getNombreProveedor());
        doc.setTipoDocumento(f.getTipoDocumento());
        doc.setFechaEmisionFactura(f.getFechaEmisionFactura());
        doc.setFechaRadicacionFactura(f.getFechaRadicacionFactura());
        doc.setFechavencimientoFactura(f.getFechavencimientoFactura());
        doc.setFechaEsperadaPagoPagador(f.getFechaEsperadaPagoPagador());
        doc.setMoneda(f.getMoneda());
        doc.setValorFactura(f.getValorFactura());
        doc.setIvaFactura(f.getIvaFactura());
        doc.setValorRetencionFactura(f.getValorRetencionFactura());
        doc.setValorNotaCredito(f.getValorNotaCredito());
        doc.setObservacionesFactura(f.getObservacionesFactura());
        doc.setValorAnticiposFactura(f.getValorAnticiposFactura());
        doc.setValorNetoFactura(f.getValorNetoFactura());
        doc.setTipoBeneficioFactura(f.getTipoBeneficioFactura());
        doc.setObservaciones(f.getObservaciones());
        doc.setFechaExtendidaConCosto(f.getFechaExtendidaConCosto());
        doc.setFechaNegociacionFactura(f.getFechaNegociacionFactura());
        doc.setValorDescuentoPagador(f.getValorDescuentoPagador());
        doc.setOriginalFileName(f.getOriginalFileName());
        doc.setPdfFileName(f.getPdfFileName());
        doc.setCufe(f.getCufe());
        doc.setEstadoNegociacion(f.getEstadoNegociacion());
        doc.setEstadoFirma(f.getEstadoFirma());
        doc.setActores(f.getActores());
        doc.setUsuarioCargaFactura(f.getUsuarioCargaFactura());
        doc.setEmpresaCargaFactura(f.getEmpresaCargaFactura());
        return doc;
    }
}
