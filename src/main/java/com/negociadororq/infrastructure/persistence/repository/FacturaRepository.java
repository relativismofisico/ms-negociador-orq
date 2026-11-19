package com.negociadororq.infrastructure.persistence.repository;

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
     * @return las facturas negociadas pendientes de evaluar vencimiento (no LIQUIDADA, VENCIDA ni RECHAZADA)
     */
    @Query("{ 'estadoNegociacion': { $nin: ['LIQUIDADA', 'VENCIDA', 'RECHAZADA'] } }")
    List<FacturaDocument> findFacturasPendientesVencimiento();
}
