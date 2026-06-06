package com.negociadororq.application.service;

import com.negociadororq.domain.model.Factura;
import com.negociadororq.infrastructure.persistence.repository.FacturaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacturaServiceImpl implements FacturaService{

    private final FacturaRepository facturaRepository;

    @Override
    @Transactional
    public void actualizarEstadoFirma(String numeroFactura, String nuevoEstado) {
        Factura factura = facturaRepository.findByNumeroFactura(numeroFactura)
                .orElseThrow(() -> new RuntimeException("Factura no encontrada: " + numeroFactura));

        String estadoAnterior = factura.getEstadoFirma();
        factura.setEstadoFirma(nuevoEstado);
        facturaRepository.save(factura);

        log.info("🔄 Estado de factura {} actualizado de {} → {}", numeroFactura, estadoAnterior, nuevoEstado);
    }

    @Override
    @Transactional
    public void actualizarEstadoFirmaMasivo(List<String> numerosFacturas, String nuevoEstado) {
        List<Factura> facturas = facturaRepository.findByNumeroFacturaIn(numerosFacturas);

        if (facturas.isEmpty()) {
            log.warn("⚠️ No se encontraron facturas para actualizar: {}", numerosFacturas);
            return;
        }

        facturas.forEach(f -> {
            String anterior = f.getEstadoFirma();
            f.setEstadoFirma(nuevoEstado);
            log.debug("Factura {} → {} → {}", f.getNumeroFactura(), anterior, nuevoEstado);
        });

        facturaRepository.saveAll(facturas);
        log.info("✅ {} facturas actualizadas al estado {}", facturas.size(), nuevoEstado);

    }

    @Override
    public Factura obtenerPorNumero(String numeroFactura) {
        return facturaRepository.findByNumeroFactura(numeroFactura)
                .orElseThrow(() -> new RuntimeException("Factura no encontrada: " + numeroFactura));
    }

    @Override
    public boolean existenFacturasDePagador(List<String> numerosFacturas, String rutPagador) {
        return facturaRepository.existsByNumeroFacturaInAndRutPagador(numerosFacturas, rutPagador);
    }
}
