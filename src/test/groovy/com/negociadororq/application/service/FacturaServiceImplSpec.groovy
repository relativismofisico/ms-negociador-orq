package com.negociadororq.application.service

import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.FacturaPatchOutputPort
import com.negociadororq.domain.model.Factura
import spock.lang.Specification

class FacturaServiceImplSpec extends Specification {

    FacturaOutputPort facturaPort = Mock()
    FacturaPatchOutputPort facturaPatchPort = Mock()
    FacturaServiceImpl service = new FacturaServiceImpl(facturaPort, facturaPatchPort)

    // ─── actualizarEstadoFirma ────────────────────────────────────────────────

    def "actualizarEstadoFirma actualiza factura existente"() {
        given:
        def factura = new Factura()
        factura.numeroFactura = "FECI001"
        factura.id = "id-1"
        factura.estadoFirma = "SIN_CESION"
        facturaPort.findByNumeroFactura("FECI001") >> Optional.of(factura)

        when:
        service.actualizarEstadoFirma("FECI001", "CESION")

        then:
        1 * facturaPatchPort.actualizarEstadoFirmaPorIds(["id-1"], "CESION")
        0 * facturaPort.save(_)
        factura.estadoFirma == "CESION"
    }

    def "actualizarEstadoFirma lanza RuntimeException cuando factura no existe"() {
        given:
        facturaPort.findByNumeroFactura("INEXISTENTE") >> Optional.empty()

        when:
        service.actualizarEstadoFirma("INEXISTENTE", "CESION")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("INEXISTENTE")
    }

    // ─── actualizarEstadoFirmaMasivo ──────────────────────────────────────────

    def "actualizarEstadoFirmaMasivo actualiza todas las facturas encontradas"() {
        given:
        def f1 = new Factura()
        f1.numeroFactura = "F001"
        f1.id = "id-1"
        f1.estadoFirma = "SIN_CESION"
        def f2 = new Factura()
        f2.numeroFactura = "F002"
        f2.id = "id-2"
        f2.estadoFirma = "EN_REVISION"

        facturaPort.findByNumeroFacturaIn(["F001", "F002"]) >> [f1, f2]

        when:
        service.actualizarEstadoFirmaMasivo(["F001", "F002"], "CESION")

        then:
        f1.estadoFirma == "CESION"
        f2.estadoFirma == "CESION"
        1 * facturaPatchPort.actualizarEstadoFirmaPorIds(["id-1", "id-2"], "CESION")
        0 * facturaPort.saveAll(_)
    }

    def "actualizarEstadoFirmaMasivo no hace nada cuando no hay facturas"() {
        given:
        facturaPort.findByNumeroFacturaIn(["F_NO_EXISTE"]) >> []

        when:
        service.actualizarEstadoFirmaMasivo(["F_NO_EXISTE"], "CESION")

        then:
        0 * facturaPatchPort.actualizarEstadoFirmaPorIds(*_)
    }

    def "actualizarEstadoFirmaMasivo con un solo elemento"() {
        given:
        def f = new Factura()
        f.numeroFactura = "F_SOLO"
        f.estadoFirma = "SIN_DESCUENTO"
        facturaPort.findByNumeroFacturaIn(["F_SOLO"]) >> [f]

        when:
        service.actualizarEstadoFirmaMasivo(["F_SOLO"], "DESCUENTO")

        then:
        f.estadoFirma == "DESCUENTO"
        1 * facturaPatchPort.actualizarEstadoFirmaPorIds(_, "DESCUENTO")
    }

    // ─── obtenerPorNumero ─────────────────────────────────────────────────────

    def "obtenerPorNumero retorna factura cuando existe"() {
        given:
        def factura = new Factura()
        factura.numeroFactura = "FECI100"
        facturaPort.findByNumeroFactura("FECI100") >> Optional.of(factura)

        when:
        def resultado = service.obtenerPorNumero("FECI100")

        then:
        resultado.numeroFactura == "FECI100"
    }

    def "obtenerPorNumero lanza RuntimeException cuando no existe"() {
        given:
        facturaPort.findByNumeroFactura("NOPE") >> Optional.empty()

        when:
        service.obtenerPorNumero("NOPE")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("NOPE")
    }

    // ─── existenFacturasDePagador ─────────────────────────────────────────────

    def "existenFacturasDePagador retorna true cuando existen"() {
        given:
        facturaPort.existsByNumeroFacturaInAndRutPagador(["F001", "F002"], "900000001") >> true

        when:
        def resultado = service.existenFacturasDePagador(["F001", "F002"], "900000001")

        then:
        resultado == true
    }

    def "existenFacturasDePagador retorna false cuando no existen"() {
        given:
        facturaPort.existsByNumeroFacturaInAndRutPagador(["F999"], "999") >> false

        when:
        def resultado = service.existenFacturasDePagador(["F999"], "999")

        then:
        resultado == false
    }
}