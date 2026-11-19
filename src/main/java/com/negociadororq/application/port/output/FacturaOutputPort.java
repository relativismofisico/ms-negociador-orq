package com.negociadororq.application.port.output;

import java.util.List;
import java.util.Optional;

import com.negociadororq.domain.model.Factura;

/**
 * Puerto de persistencia de facturas.
 */
public interface FacturaOutputPort {

    /**
     * @param cufe CUFE (único)
     * @return la factura, si existe
     */
    Optional<Factura> findByCufe(String cufe);

    /**
     * Fase 28d (C): todas las facturas con ese número (el número es el consecutivo de cada emisor, no es único). Solo
     * para resolver con las empresas ({@code ResolutorFactura}); nunca para elegir "la primera".
     *
     * @param numeroFactura número
     * @return las facturas con ese número
     */
    List<Factura> findAllByNumeroFactura(String numeroFactura);

    /**
     * @return las facturas negociadas pendientes de evaluar vencimiento
     */
    List<Factura> findFacturasPendientesVencimiento();

    /**
     * @param factura factura a persistir
     * @return la factura persistida
     */
    Factura save(Factura factura);
}
