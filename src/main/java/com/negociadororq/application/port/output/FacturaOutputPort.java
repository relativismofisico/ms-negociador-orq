package com.negociadororq.application.port.output;

import java.time.LocalDate;
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
     * Fase 28d (A5): candidatas del vencimiento nocturno.
     *
     * @param limite  fecha de vencimiento máxima (hoy + 10 días)
     * @param estados estados de negociación candidatos
     * @return las facturas que vencen hasta {@code limite} en esos estados
     */
    List<Factura> findCandidatasVencimiento(LocalDate limite, List<String> estados);

    /**
     * @return Fase 28d (A5): las VENCIDA cuya reversión de cupos no terminó (caída a mitad de la corrida)
     */
    List<Factura> findVencidasConReversionPendiente();

    /**
     * @return Fase 28d (A5): las facturas con firma final y solicitud de liquidación sin seguimiento confirmado
     */
    List<Factura> findConSeguimientoLiquidacionPendiente();

    /**
     * @param factura factura a persistir
     * @return la factura persistida
     */
    Factura save(Factura factura);
}
