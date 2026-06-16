package com.negociadororq.application.service

import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.EndosoPatchOutputPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.FacturaPatchOutputPort
import com.negociadororq.application.port.output.NegociacionOutputPort
import com.negociadororq.application.port.output.UserOutputPort
import com.negociadororq.domain.enums.EstadoFirma
import com.negociadororq.domain.model.Company
import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.FondeadorAsignacion
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import com.negociadororq.domain.model.User
import spock.lang.Specification

import java.math.BigDecimal

class ReversionCuposServiceImplSpec extends Specification {

    NegociacionOutputPort negociacionPort = Mock()
    FacturaOutputPort facturaPort = Mock()
    FacturaPatchOutputPort facturaPatchPort = Mock()
    EndosoPatchOutputPort endosoPatchPort = Mock()
    CupoReversionService cupoReversionService = Mock()
    UserOutputPort userPort = Mock()
    CompanyOutputPort companyPort = Mock()
    NotificacionService notificacionService = Mock()

    ReversionCuposServiceImpl service = new ReversionCuposServiceImpl(
            negociacionPort, facturaPort, facturaPatchPort, endosoPatchPort,
            cupoReversionService, userPort, companyPort, notificacionService
    )

    def buildFactura(String numero) {
        def f = new Factura()
        f.numeroFactura = numero
        f.rutProveedor = "PROV-001"
        f.rutPagador = "PAG-001"
        return f
    }

    def buildNegociacion(String tipo, String numero, BigDecimal valor) {
        def fn = FacturaNegociada.builder()
                .numeroFactura(numero)
                .valorFactura(valor)
                .fondeadores([FondeadorAsignacion.builder().rutFondeador("FOND-001").build()])
                .build()
        Negociacion.builder()
                .id("neg-001")
                .rutEmpresa("EMP-001")
                .tipoOperacion(new Operacion(tipo, "DESC"))
                .facturas([fn])
                .build()
    }

    // ─── flujo exitoso FACTORING ─────────────────────────────────────────────

    def "revertirPorFactura FACTORING revierte cupos operador, empresa y fondeador"() {
        given:
        def numero = "FECI001"
        def valor = new BigDecimal("5000.00")
        def factura = buildFactura(numero)
        def neg = buildNegociacion("FACTORING", numero, valor)

        facturaPort.findByNumeroFactura(numero) >> Optional.of(factura)
        negociacionPort.findByNumeroFactura(numero) >> Optional.of(neg)
        userPort.findByUsername("USR-OP") >> Optional.of(User.builder().ideUser(1L).build())
        companyPort.findByNit("EMP-001") >> Optional.of(Company.builder().ideCompany(10L).build())
        companyPort.findByNit("FOND-001") >> Optional.of(Company.builder().ideCompany(20L).build())
        companyPort.findByNit("PROV-001") >> Optional.of(Company.builder().ideCompany(30L).build())

        when:
        service.revertirPorFactura(numero, "USR-OP", "Error de pago")

        then:
        1 * cupoReversionService.revertirCupoUsuario(1L, valor)
        1 * cupoReversionService.revertirCupoEmpresa(10L, valor)
        1 * cupoReversionService.revertirCupoFondeadorFactoring(20L, 30L, valor)
        1 * facturaPatchPort.actualizarEstadoFirmaPorNoFactura(numero, EstadoFirma.RECHAZADA)
        1 * notificacionService.notificarLiquidacionFallida(neg, "Error de pago")
    }

    // ─── flujo exitoso CONFIRMING ─────────────────────────────────────────────

    def "revertirPorFactura CONFIRMING revierte cupo fondeador confirming"() {
        given:
        def numero = "FECI002"
        def valor = new BigDecimal("3000.00")
        def factura = buildFactura(numero)
        def neg = buildNegociacion("CONFIRMING", numero, valor)

        facturaPort.findByNumeroFactura(numero) >> Optional.of(factura)
        negociacionPort.findByNumeroFactura(numero) >> Optional.of(neg)
        userPort.findByUsername("USR-OP") >> Optional.of(User.builder().ideUser(1L).build())
        companyPort.findByNit("EMP-001") >> Optional.of(Company.builder().ideCompany(10L).build())
        companyPort.findByNit("FOND-001") >> Optional.of(Company.builder().ideCompany(20L).build())
        companyPort.findByNit("PAG-001") >> Optional.of(Company.builder().ideCompany(40L).build())

        when:
        service.revertirPorFactura(numero, "USR-OP", "Motivo")

        then:
        1 * cupoReversionService.revertirCupoFondeadorConfirming(20L, 40L, valor)
        0 * cupoReversionService.revertirCupoFondeadorFactoring(_, _, _)
    }

    // ─── factura no encontrada ────────────────────────────────────────────────

    def "revertirPorFactura lanza RuntimeException si factura no existe"() {
        given:
        facturaPort.findByNumeroFactura("NO-EXISTE") >> Optional.empty()

        when:
        service.revertirPorFactura("NO-EXISTE", "USR", "Motivo")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Factura no encontrada")
    }

    // ─── negociacion no encontrada ────────────────────────────────────────────

    def "revertirPorFactura lanza RuntimeException si negociacion no existe"() {
        given:
        facturaPort.findByNumeroFactura("FECI003") >> Optional.of(buildFactura("FECI003"))
        negociacionPort.findByNumeroFactura("FECI003") >> Optional.empty()

        when:
        service.revertirPorFactura("FECI003", "USR", "Motivo")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Negociación no encontrada")
    }

    // ─── usuario no encontrado ────────────────────────────────────────────────

    def "revertirPorFactura lanza RuntimeException si usuario no existe"() {
        given:
        def numero = "FECI004"
        facturaPort.findByNumeroFactura(numero) >> Optional.of(buildFactura(numero))
        negociacionPort.findByNumeroFactura(numero) >> Optional.of(buildNegociacion("FACTORING", numero, new BigDecimal("100")))
        userPort.findByUsername("USR_NO") >> Optional.empty()

        when:
        service.revertirPorFactura(numero, "USR_NO", "Motivo")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Usuario no encontrado")
    }

    // ─── empresa no encontrada ────────────────────────────────────────────────

    def "revertirPorFactura lanza RuntimeException si empresa no existe"() {
        given:
        def numero = "FECI005"
        facturaPort.findByNumeroFactura(numero) >> Optional.of(buildFactura(numero))
        negociacionPort.findByNumeroFactura(numero) >> Optional.of(buildNegociacion("FACTORING", numero, new BigDecimal("100")))
        userPort.findByUsername("USR-OP") >> Optional.of(User.builder().ideUser(1L).build())
        companyPort.findByNit("EMP-001") >> Optional.empty()

        when:
        service.revertirPorFactura(numero, "USR-OP", "Motivo")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Empresa no encontrada")
    }

    // ─── sin fondeadores ─────────────────────────────────────────────────────

    def "revertirPorFactura sin fondeadores no revierte cupo fondeador"() {
        given:
        def numero = "FECI006"
        def valor = new BigDecimal("1000.00")
        def factura = buildFactura(numero)

        def fn = FacturaNegociada.builder()
                .numeroFactura(numero)
                .valorFactura(valor)
                .fondeadores(null)
                .build()
        def neg = Negociacion.builder()
                .id("neg-001")
                .rutEmpresa("EMP-001")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .facturas([fn])
                .build()

        facturaPort.findByNumeroFactura(numero) >> Optional.of(factura)
        negociacionPort.findByNumeroFactura(numero) >> Optional.of(neg)
        userPort.findByUsername("USR-OP") >> Optional.of(User.builder().ideUser(1L).build())
        companyPort.findByNit("EMP-001") >> Optional.of(Company.builder().ideCompany(10L).build())

        when:
        service.revertirPorFactura(numero, "USR-OP", "Motivo")

        then:
        0 * cupoReversionService.revertirCupoFondeadorFactoring(_, _, _)
        0 * cupoReversionService.revertirCupoFondeadorConfirming(_, _, _)
        1 * facturaPatchPort.actualizarEstadoFirmaPorNoFactura(numero, EstadoFirma.RECHAZADA)
    }
}