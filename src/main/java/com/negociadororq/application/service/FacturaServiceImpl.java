package com.negociadororq.application.service;

import java.util.List;

import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.domain.model.Factura;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link FacturaService}.
 */
@Log
@RequiredArgsConstructor
public class FacturaServiceImpl implements FacturaService {

    private final FacturaOutputPort facturaPort;

    @Override
    @Transactional
    public void actualizarEstadoFirma(String numeroFactura, String nuevoEstado) {
        Factura factura = facturaPort.findByNumeroFactura(numeroFactura)
                .orElseThrow(() -> new RecursoNoEncontradoException("Factura no encontrada: " + numeroFactura));

        String estadoAnterior = factura.getEstadoFirma();
        factura.setEstadoFirma(nuevoEstado);
        facturaPort.save(factura);

        log.info(String.format("Estado de factura %s actualizado de %s a %s", numeroFactura, estadoAnterior, nuevoEstado));
    }

    @Override
    @Transactional
    public void actualizarEstadoFirmaMasivo(List<String> numerosFacturas, String nuevoEstado) {
        List<Factura> facturas = facturaPort.findByNumeroFacturaIn(numerosFacturas);

        if (facturas.isEmpty()) {
            log.warning(String.format("No se encontraron facturas para actualizar: %s", numerosFacturas));
            return;
        }

        facturas.forEach(f -> {
            String anterior = f.getEstadoFirma();
            f.setEstadoFirma(nuevoEstado);
            log.fine(String.format("Factura %s -> %s -> %s", f.getNumeroFactura(), anterior, nuevoEstado));
        });

        facturaPort.saveAll(facturas);
        log.info(String.format("%d facturas actualizadas al estado %s", facturas.size(), nuevoEstado));

    }

    @Override
    public Factura obtenerPorNumero(String numeroFactura) {
        return facturaPort.findByNumeroFactura(numeroFactura)
                .orElseThrow(() -> new RecursoNoEncontradoException("Factura no encontrada: " + numeroFactura));
    }

    @Override
    public boolean existenFacturasDePagador(List<String> numerosFacturas, String rutPagador) {
        return facturaPort.existsByNumeroFacturaInAndRutPagador(numerosFacturas, rutPagador);
    }
}

