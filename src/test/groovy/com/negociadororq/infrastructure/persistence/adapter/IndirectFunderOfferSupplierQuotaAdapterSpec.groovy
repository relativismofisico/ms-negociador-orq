package com.negociadororq.infrastructure.persistence.adapter

import com.negociadororq.application.exception.ConflictoException
import com.negociadororq.infrastructure.persistence.entity.IndirectFunderOfferSupplierQuotaEntity
import com.negociadororq.infrastructure.persistence.repository.IndirectFunderOfferSupplierQuotaRepository
import org.springframework.dao.DataIntegrityViolationException
import spock.lang.Specification

class IndirectFunderOfferSupplierQuotaAdapterSpec extends Specification {

    IndirectFunderOfferSupplierQuotaRepository repository = Mock()
    IndirectFunderOfferSupplierQuotaAdapter adapter = new IndirectFunderOfferSupplierQuotaAdapter(repository)

    def "crear traduce el choque del UNIQUE (DataIntegrityViolationException) a ConflictoException sin filtrar tabla ni constraint"() {
        given:
        repository.save(_ as IndirectFunderOfferSupplierQuotaEntity) >> {
            throw new DataIntegrityViolationException(
                    "could not execute statement [Duplicate entry '77-10' for key " +
                    "'indirect_funder_offer_supplier_quota.uq_indirect_supplier_quota']")
        }

        when:
        adapter.crear(77L, 10L, new BigDecimal("2.5"), new BigDecimal("10000.00"))

        then:
        def ex = thrown(ConflictoException)
        ex.codigo == "CUPO_PROVEEDOR_CONCURRENCIA"
        !ex.message.toLowerCase().contains("duplicate entry")
        !ex.message.toLowerCase().contains("constraint")
        !ex.message.contains("indirect_funder_offer_supplier_quota")
        !ex.message.contains("uq_indirect_supplier_quota")
    }

    def "crear devuelve el consumo mapeado cuando el INSERT tiene exito"() {
        given:
        repository.save(_ as IndirectFunderOfferSupplierQuotaEntity) >> { IndirectFunderOfferSupplierQuotaEntity e ->
            e.id = 1L
            e
        }

        when:
        def cupo = adapter.crear(77L, 10L, new BigDecimal("2.5"), new BigDecimal("10000.00"))

        then:
        cupo.id == 1L
        cupo.offerId == 77L
        cupo.supplierId == 10L
        cupo.currentQuota == new BigDecimal("10000.00")
        cupo.originalQuota == new BigDecimal("10000.00")
    }

    def "Fase 28d (P-19): crear toma la plantilla y la versión de la oferta en la misma sentencia y devuelve la fila creada"() {
        given:
        repository.crearDesdeOferta(77L, 10L, new BigDecimal("2.5")) >> 1
        repository.findByOfferIdAndSupplierId(77L, 10L) >> Optional.of(new IndirectFunderOfferSupplierQuotaEntity(
                id: 5L, offerId: 77L, supplierId: 10L, quota: new BigDecimal("12000.00"),
                currentQuota: new BigDecimal("12000.00"), originalQuota: new BigDecimal("12000.00")))

        when:
        def cupo = adapter.crear(77L, 10L, new BigDecimal("2.5"), new BigDecimal("10000.00"))

        then:
        0 * repository.save(_)
        cupo.id == 5L
        cupo.originalQuota == new BigDecimal("12000.00")
    }

    def "Fase 28d (P-19): el choque del UNIQUE al crear desde la oferta también es ConflictoException"() {
        given:
        repository.crearDesdeOferta(77L, 10L, _) >> { throw new DataIntegrityViolationException("Duplicate entry") }

        when:
        adapter.crear(77L, 10L, new BigDecimal("2.5"), new BigDecimal("10000.00"))

        then:
        def ex = thrown(ConflictoException)
        ex.codigo == "CUPO_PROVEEDOR_CONCURRENCIA"
    }

    def "Fase 28d (P-19): propagar completa plantillas faltantes, ajusta y cuenta los que quedan por encima"() {
        when:
        def p = adapter.propagarCupoOferta(77L, 3L, new BigDecimal("5000.00"))

        then:
        1 * repository.completarPlantilla(77L)

        then:
        1 * repository.propagarCupoOferta(77L, 3L, new BigDecimal("5000.00")) >> 4
        1 * repository.registrarExceso(77L, 3L, new BigDecimal("5000.00")) >> 1
        1 * repository.contarPorEncimaDe(77L, new BigDecimal("5000.00")) >> 1L
        p.ajustadas() == 4
        p.porEncima() == 1L
    }

    def "descontarCupo delega el UPDATE atomico al repositorio y propaga las filas afectadas"() {
        when:
        def filas = adapter.descontarCupo(77L, 10L, new BigDecimal("5000.00"))

        then:
        1 * repository.descontarCupo(77L, 10L, new BigDecimal("5000.00")) >> 1
        filas == 1
    }
}
