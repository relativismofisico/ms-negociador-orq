package com.negociadororq.infrastructure.persistence.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.negociadororq.infrastructure.persistence.entity.FacturaDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data MongoDB de {@link FacturaDocument}.
 */
@Repository
public interface FacturaRepository extends MongoRepository<FacturaDocument, String> {

    /**
     * Fase 28d (C): {@code numeroFactura} (//cbc:ID del XML DIAN) es el consecutivo del EMISOR, no es global: dos
     * proveedores emiten legítimamente su factura {@code 1}. Devuelve todas; quien llama resuelve con las empresas
     * (o usa {@link #findByCufe}, la clave única real).
     *
     * @param numeroFactura número de la factura
     * @return las facturas con ese número
     */
    List<FacturaDocument> findByNumeroFactura(String numeroFactura);

    /**
     * @param cufe CUFE de la factura
     * @return la factura, si existe
     */
    Optional<FacturaDocument> findByCufe(String cufe);

    /**
     * Fase 28d (A5): candidatas del vencimiento nocturno (índice {@code ix_facturas_vencimiento_estado}).
     *
     * @param limite  fecha de vencimiento máxima (hoy + 10 días)
     * @param estados estados de negociación candidatos
     * @return las facturas que vencen hasta {@code limite} en esos estados
     */
    @Query("{ 'fechavencimientoFactura': { $lte: ?0 }, 'estadoNegociacion': { $in: ?1 } }")
    List<FacturaDocument> findCandidatasVencimiento(LocalDate limite, List<String> estados);

    /**
     * @return Fase 28d (A5): las VENCIDA cuya reversión de cupos no terminó
     */
    @Query("{ 'estadoNegociacion': 'VENCIDA', 'vencimientoReversionPendiente': true }")
    List<FacturaDocument> findVencidasConReversionPendiente();

    /**
     * @return Fase 28d (A5): las facturas con firma final y solicitud escritas sin seguimiento confirmado
     */
    @Query("{ 'seguimientoLiquidacionPendiente': true }")
    List<FacturaDocument> findConSeguimientoLiquidacionPendiente();
}
