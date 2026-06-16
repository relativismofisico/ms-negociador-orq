package com.negociadororq.application.service

import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.domain.model.Factura
import spock.lang.Specification

import java.time.LocalDate

class FacturaVencimientoServiceSpec extends Specification {

    FacturaOutputPort facturaPort = Mock()
    FacturaVencimientoService service = new FacturaVencimientoService(facturaPort)

    def "marca facturas con dias <= 10 como VENCIDA"() {
        given:
        def f1 = new Factura()
        f1.numeroFactura = "F001"
        f1.fechavencimientoFactura = LocalDate.now().plusDays(5)

        def f2 = new Factura()
        f2.numeroFactura = "F002"
        f2.fechavencimientoFactura = LocalDate.now().plusDays(10)

        facturaPort.findFacturasPendientesVencimiento() >> [f1, f2]

        when:
        service.actualizarFacturasVencidas()

        then:
        f1.estadoNegociacion == "VENCIDA"
        f2.estadoNegociacion == "VENCIDA"
        2 * facturaPort.save(_)
    }

    def "no marca facturas con dias > 10"() {
        given:
        def f = new Factura()
        f.numeroFactura = "F_VIGENTE"
        f.fechavencimientoFactura = LocalDate.now().plusDays(20)

        facturaPort.findFacturasPendientesVencimiento() >> [f]

        when:
        service.actualizarFacturasVencidas()

        then:
        f.estadoNegociacion == null
        0 * facturaPort.save(_)
    }

    def "omite facturas sin fecha de vencimiento"() {
        given:
        def f = new Factura()
        f.numeroFactura = "F_SIN_FECHA"
        f.fechavencimientoFactura = null

        facturaPort.findFacturasPendientesVencimiento() >> [f]

        when:
        service.actualizarFacturasVencidas()

        then:
        f.estadoNegociacion == null
        0 * facturaPort.save(_)
    }

    def "cuando no hay facturas pendientes no hace nada"() {
        given:
        facturaPort.findFacturasPendientesVencimiento() >> []

        when:
        service.actualizarFacturasVencidas()

        then:
        0 * facturaPort.save(_)
    }

    def "continua procesando si una factura lanza excepcion"() {
        given:
        def fError = new Factura()
        fError.numeroFactura = "F_ERROR"
        fError.fechavencimientoFactura = LocalDate.now().plusDays(5)

        def fOk = new Factura()
        fOk.numeroFactura = "F_OK"
        fOk.fechavencimientoFactura = LocalDate.now().plusDays(3)

        facturaPort.findFacturasPendientesVencimiento() >> [fError, fOk]
        facturaPort.save(fError) >> { throw new RuntimeException("Error BD") }

        when:
        service.actualizarFacturasVencidas()

        then:
        fOk.estadoNegociacion == "VENCIDA"
        1 * facturaPort.save(fOk)
    }

    def "procesa lista con mezcla de vigentes y vencidas"() {
        given:
        def vencida = new Factura()
        vencida.numeroFactura = "F_VENCIDA"
        vencida.fechavencimientoFactura = LocalDate.now().plusDays(2)

        def vigente = new Factura()
        vigente.numeroFactura = "F_VIGENTE"
        vigente.fechavencimientoFactura = LocalDate.now().plusDays(30)

        def sinFecha = new Factura()
        sinFecha.numeroFactura = "F_SIN"
        sinFecha.fechavencimientoFactura = null

        facturaPort.findFacturasPendientesVencimiento() >> [vencida, vigente, sinFecha]

        when:
        service.actualizarFacturasVencidas()

        then:
        vencida.estadoNegociacion == "VENCIDA"
        vigente.estadoNegociacion == null
        sinFecha.estadoNegociacion == null
        1 * facturaPort.save(vencida)
        0 * facturaPort.save(vigente)
        0 * facturaPort.save(sinFecha)
    }
}