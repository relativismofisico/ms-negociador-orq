package com.negociadororq.infrastructure.persistence

import com.negociadororq.application.exception.ConflictoException
import com.negociadororq.application.service.ProcesarFacturaNegociacionService
import com.negociadororq.domain.model.FacturaNegociada
import org.springframework.dao.CannotAcquireLockException
import org.springframework.dao.PessimisticLockingFailureException
import spock.lang.Specification
import spock.lang.Unroll

/** Fase 28d (P-19): deadlock y lock wait timeout al procesar una factura -> ConflictoException CUPO_BLOQUEO_REINTENTAR. */
class BloqueosProcesarFacturaSpec extends Specification {

    ProcesarFacturaNegociacionService delegado = Mock()
    BloqueosProcesarFactura bloqueos = new BloqueosProcesarFactura(delegado)

    @Unroll
    def "#caso se traduce a CUPO_BLOQUEO_REINTENTAR (con y sin contexto de cupo indirecto)"() {
        given:
        delegado.procesarFactura(_, _, _) >> { throw excepcion }
        delegado.procesarFactura(_, _, _, _) >> { throw excepcion }

        when:
        bloqueos.procesarFactura(null, new FacturaNegociada(numeroFactura: "F1"), "N1")

        then:
        def e1 = thrown(ConflictoException)
        e1.codigo == "CUPO_BLOQUEO_REINTENTAR"

        when:
        bloqueos.procesarFactura(null, new FacturaNegociada(numeroFactura: "F1"), "N1", null)

        then:
        def e2 = thrown(ConflictoException)
        e2.codigo == "CUPO_BLOQUEO_REINTENTAR"

        where:
        caso                | excepcion
        "lock wait timeout" | new CannotAcquireLockException("Lock wait timeout exceeded")
        "deadlock"          | new PessimisticLockingFailureException("Deadlock found when trying to get lock")
    }

    def "otros errores y los resultados pasan sin tocar"() {
        given:
        def ok = new FacturaNegociada(numeroFactura: "F2")
        delegado.procesarFactura(_, _, _) >> ok

        expect:
        bloqueos.procesarFactura(null, ok, "N1").is(ok)

        when:
        bloqueos.procesarFactura(null, ok, "N1", null)

        then:
        1 * delegado.procesarFactura(_, _, _, _) >> { throw new IllegalStateException("otro") }
        thrown(IllegalStateException)
    }
}
