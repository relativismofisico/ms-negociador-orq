package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.Factura;

import java.util.List;
import java.util.Optional;

public interface FacturaOutputPort {
    Optional<Factura> findByNumeroFactura(String numeroFactura);
    List<Factura> findByNumeroFacturaIn(List<String> numerosFactura);
    boolean existsByNumeroFacturaInAndRutPagador(List<String> numerosFactura, String rutPagador);
    boolean existsByNumeroFacturaInAndRutProveedor(List<String> numerosFactura, String rutProveedor);
    List<Factura> findFacturasPendientesVencimiento();
    Factura save(Factura factura);
    List<Factura> saveAll(List<Factura> facturas);
}
