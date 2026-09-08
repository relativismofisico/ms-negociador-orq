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

    def "descontarCupo delega el UPDATE atomico al repositorio y propaga las filas afectadas"() {
        when:
        def filas = adapter.descontarCupo(77L, 10L, new BigDecimal("5000.00"))

        then:
        1 * repository.descontarCupo(77L, 10L, new BigDecimal("5000.00")) >> 1
        filas == 1
    }
}
