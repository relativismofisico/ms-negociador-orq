package com.negociadororq.application.service

import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.NegociacionOutputPort
import com.negociadororq.domain.model.Factura
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime

class FacturaVencimientoServiceSpec extends Specification {

    FacturaOutputPort facturaPort = Mock()
    NegociacionOutputPort negociacionPort = Mock()
    CompanyOutputPort companyPort = Mock()
    CupoReversionService cupoReversionService = Mock()
    ClockPort clock = Mock() {
        now() >> LocalDateTime.now()
    }
    FacturaVencimientoService service = new FacturaVencimientoService(
            facturaPort, negociacionPort, companyPort, cupoReversionService, clock)

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

    // ─── reversion de cupos para negociaciones abandonadas ───────────────────

    def "factura SIN_NEGOCIAR que vence no reversa cupos (nunca se consumieron)"() {
        given:
        def f = new Factura()
        f.numeroFactura = "F_SIN_NEG"
        f.estadoNegociacion = "SIN_NEGOCIAR"
        f.fechavencimientoFactura = LocalDate.now().plusDays(5)
        f.negociacionId = "neg-1"

        facturaPort.findFacturasPendientesVencimiento() >> [f]

        when:
        service.actualizarFacturasVencidas()

        then:
        f.estadoNegociacion == "VENCIDA"
        0 * negociacionPort.findById(_)
        0 * cupoReversionService.revertirCupoEmpresa(_, _)
    }

    def "factura NEGOCIADA que vence reversa cupos de empresa y fondeadores antes de marcarla VENCIDA"() {
        given:
        def f = new Factura()
        f.numeroFactura = "F_NEG"
        f.estadoNegociacion = "NEGOCIADA"
        f.fechavencimientoFactura = LocalDate.now().plusDays(5)
        f.negociacionId = "neg-1"
        f.rutProveedor = "RUT-PROV"
        f.rutPagador = "RUT-PAG"
        f.valorNetoFactura = new BigDecimal("7000.00")

        facturaPort.findFacturasPendientesVencimiento() >> [f]
        negociacionPort.findById("neg-1") >> Optional.of(
                com.negociadororq.domain.model.Negociacion.builder()
                        .id("neg-1")
                        .rutEmpresa("RUT-PROV")
                        .tipoOperacion(new com.negociadororq.domain.model.Operacion("FACTORING", "DESC"))
                        .rutFondeadores(["FOND-001"])
                        .build()
        )
        companyPort.findByNit("RUT-PROV") >> Optional.of(
                com.negociadororq.domain.model.Company.builder().ideCompany(2L).build())
        companyPort.findByNit("FOND-001") >> Optional.of(
                com.negociadororq.domain.model.Company.builder().ideCompany(1L).build())

        when:
        service.actualizarFacturasVencidas()

        then:
        f.estadoNegociacion == "VENCIDA"
        1 * cupoReversionService.revertirCupoEmpresa(2L, new BigDecimal("7000.00"))
        1 * cupoReversionService.revertirCupoFondeadorFactoring(1L, 2L, new BigDecimal("7000.00"))
        1 * facturaPort.save(f)
    }

    def "factura EN_REVISION sin negociacionId no reversa nada (no hay como resolverla)"() {
        given:
        def f = new Factura()
        f.numeroFactura = "F_SIN_NEGID"
        f.estadoNegociacion = "EN_REVISION"
        f.fechavencimientoFactura = LocalDate.now().plusDays(5)
        f.negociacionId = null

        facturaPort.findFacturasPendientesVencimiento() >> [f]

        when:
        service.actualizarFacturasVencidas()

        then:
        f.estadoNegociacion == "VENCIDA"
        0 * negociacionPort.findById(_)
        0 * cupoReversionService.revertirCupoEmpresa(_, _)
    }
}