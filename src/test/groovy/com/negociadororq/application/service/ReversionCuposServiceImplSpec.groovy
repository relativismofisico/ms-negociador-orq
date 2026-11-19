package com.negociadororq.application.service

import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.EndosoPatchOutputPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.FacturaPatchOutputPort
import com.negociadororq.application.port.output.NegociacionOutputPort
import com.negociadororq.application.port.output.ReversionCuposReclamoOutputPort
import com.negociadororq.application.port.output.UserOutputPort
import com.negociadororq.domain.enums.EstadoFirma
import com.negociadororq.domain.model.Company
import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.FondeadorAsignacion
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import com.negociadororq.domain.model.User
import org.springframework.transaction.support.TransactionOperations
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
    ReversionCuposReclamoOutputPort reclamoPort = Mock()

    ReversionCuposServiceImpl service = new ReversionCuposServiceImpl(
            negociacionPort, facturaPort, facturaPatchPort, endosoPatchPort,
            cupoReversionService, userPort, companyPort, notificacionService,
            reclamoPort, TransactionOperations.withoutTransaction()
    )

    def setup() {
        reclamoPort.reclamar(_, _) >> true
    }

    def buildFactura(String numero) {
        def f = new Factura()
        f.numeroFactura = numero
        f.cufe = "CUFE-" + numero
        f.rutProveedor = "PROV-001"
        f.rutPagador = "PAG-001"
        return f
    }

    def buildNegociacion(String tipo, String numero, BigDecimal valor) {
        def fn = FacturaNegociada.builder()
                .numeroFactura(numero)
                .cufe("CUFE-" + numero).rutProveedor("PROV-001").rutPagador("PAG-001")
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

        facturaPort.findByCufe("CUFE-" + numero) >> Optional.of(factura)
        negociacionPort.findById("neg-001") >> Optional.of(neg)
        userPort.findByUsername("USR-OP") >> Optional.of(User.builder().ideUser(1L).build())
        companyPort.findByNit("EMP-001") >> Optional.of(Company.builder().ideCompany(10L).build())
        companyPort.findByNit("FOND-001") >> Optional.of(Company.builder().ideCompany(20L).build())
        companyPort.findByNit("PROV-001") >> Optional.of(Company.builder().ideCompany(30L).build())

        when:
        service.revertirPorFactura("neg-001", "CUFE-" + numero, numero, "USR-OP", "Error de pago")

        then:
        1 * cupoReversionService.revertirCupoUsuario(1L, valor)
        1 * cupoReversionService.revertirCupoEmpresaEnTransaccionActual(10L, valor)
        0 * cupoReversionService.revertirCupoEmpresa(_, _)
        1 * cupoReversionService.revertirCupoFondeadorFactoring(20L, 30L, valor)
        1 * facturaPatchPort.actualizarEstadoFirmaPorCufe({ it.cufe() == "CUFE-" + numero }, EstadoFirma.RECHAZADA)
        1 * notificacionService.notificarLiquidacionFallida(neg, numero, "Error de pago")
    }

    // ─── flujo exitoso CONFIRMING ─────────────────────────────────────────────

    def "revertirPorFactura CONFIRMING revierte cupo fondeador confirming"() {
        given:
        def numero = "FECI002"
        def valor = new BigDecimal("3000.00")
        def factura = buildFactura(numero)
        def neg = buildNegociacion("CONFIRMING", numero, valor)

        facturaPort.findByCufe("CUFE-" + numero) >> Optional.of(factura)
        negociacionPort.findById("neg-001") >> Optional.of(neg)
        userPort.findByUsername("USR-OP") >> Optional.of(User.builder().ideUser(1L).build())
        companyPort.findByNit("EMP-001") >> Optional.of(Company.builder().ideCompany(10L).build())
        companyPort.findByNit("FOND-001") >> Optional.of(Company.builder().ideCompany(20L).build())
        companyPort.findByNit("PAG-001") >> Optional.of(Company.builder().ideCompany(40L).build())

        when:
        service.revertirPorFactura("neg-001", "CUFE-" + numero, numero, "USR-OP", "Motivo")

        then:
        1 * cupoReversionService.revertirCupoFondeadorConfirming(20L, 40L, valor)
        0 * cupoReversionService.revertirCupoFondeadorFactoring(_, _, _)
    }

    // ─── factura no encontrada ────────────────────────────────────────────────

    def "revertirPorFactura lanza RuntimeException si factura no existe"() {
        given:
        negociacionPort.findById("neg-001") >> Optional.of(buildNegociacion("FACTORING", "NO-EXISTE", BigDecimal.ONE))
        facturaPort.findByCufe("CUFE-" + "NO-EXISTE") >> Optional.empty()

        when:
        service.revertirPorFactura("neg-001", "CUFE-" + "NO-EXISTE", "NO-EXISTE", "USR", "Motivo")

        then:
        def ex = thrown(com.negociadororq.application.exception.ConflictoException)
        ex.codigo == ResolutorFactura.FACTURA_NO_COINCIDE
    }

    // ─── negociacion no encontrada ────────────────────────────────────────────

    def "revertirPorFactura lanza RuntimeException si negociacion no existe"() {
        given:
        facturaPort.findByCufe("CUFE-" + "FECI003") >> Optional.of(buildFactura("FECI003"))
        negociacionPort.findById("neg-001") >> Optional.empty()

        when:
        service.revertirPorFactura("neg-001", "CUFE-" + "FECI003", "FECI003", "USR", "Motivo")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Negociación no encontrada")
    }

    // ─── usuario no encontrado ────────────────────────────────────────────────

    def "revertirPorFactura lanza RuntimeException si usuario no existe"() {
        given:
        def numero = "FECI004"
        facturaPort.findByCufe("CUFE-" + numero) >> Optional.of(buildFactura(numero))
        negociacionPort.findById("neg-001") >> Optional.of(buildNegociacion("FACTORING", numero, new BigDecimal("100")))
        userPort.findByUsername("USR_NO") >> Optional.empty()

        when:
        service.revertirPorFactura("neg-001", "CUFE-" + numero, numero, "USR_NO", "Motivo")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Usuario no encontrado")
    }

    // ─── empresa no encontrada ────────────────────────────────────────────────

    def "revertirPorFactura lanza RuntimeException si empresa no existe"() {
        given:
        def numero = "FECI005"
        facturaPort.findByCufe("CUFE-" + numero) >> Optional.of(buildFactura(numero))
        negociacionPort.findById("neg-001") >> Optional.of(buildNegociacion("FACTORING", numero, new BigDecimal("100")))
        userPort.findByUsername("USR-OP") >> Optional.of(User.builder().ideUser(1L).build())
        companyPort.findByNit("EMP-001") >> Optional.empty()

        when:
        service.revertirPorFactura("neg-001", "CUFE-" + numero, numero, "USR-OP", "Motivo")

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
                .cufe("CUFE-" + numero).rutProveedor("PROV-001").rutPagador("PAG-001")
                .valorFactura(valor)
                .fondeadores(null)
                .build()
        def neg = Negociacion.builder()
                .id("neg-001")
                .rutEmpresa("EMP-001")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .facturas([fn])
                .build()

        facturaPort.findByCufe("CUFE-" + numero) >> Optional.of(factura)
        negociacionPort.findById("neg-001") >> Optional.of(neg)
        userPort.findByUsername("USR-OP") >> Optional.of(User.builder().ideUser(1L).build())
        companyPort.findByNit("EMP-001") >> Optional.of(Company.builder().ideCompany(10L).build())

        when:
        service.revertirPorFactura("neg-001", "CUFE-" + numero, numero, "USR-OP", "Motivo")

        then:
        0 * cupoReversionService.revertirCupoFondeadorFactoring(_, _, _)
        0 * cupoReversionService.revertirCupoFondeadorConfirming(_, _, _)
        1 * facturaPatchPort.actualizarEstadoFirmaPorCufe({ it.cufe() == "CUFE-" + numero }, EstadoFirma.RECHAZADA)
    }

    // ─── idempotencia ────────────────────────────────────────────────────────

    def "revertirPorFactura sobre factura #estadoNegociacion / firma #estadoFirma no revierte, no marca ni notifica"() {
        given:
        def numero = "FECI-GUARDA"
        def factura = buildFactura(numero)
        factura.estadoNegociacion = estadoNegociacion
        factura.estadoFirma = estadoFirma
        facturaPort.findByCufe("CUFE-" + numero) >> Optional.of(factura)
        negociacionPort.findById("neg-001") >> Optional.of(buildNegociacion("FACTORING", numero, BigDecimal.ONE))

        when:
        service.revertirPorFactura("neg-001", "CUFE-" + numero, numero, "USR-OP", "Motivo")

        then:
        0 * negociacionPort.save(_)
        0 * reclamoPort._
        0 * cupoReversionService._
        0 * facturaPatchPort._
        0 * notificacionService._

        where:
        estadoNegociacion | estadoFirma
        "LIQUIDADA"       | "CESION"
        "LIQUIDADA"       | "DESCUENTO"
        "NEGOCIADA"       | "RECHAZADA"
    }

    def "revertirPorFactura con la reversión ya reclamada no revierte ni notifica; solo completa la firma"() {
        given:
        def numero = "FECI-DUP"
        def neg = buildNegociacion("FACTORING", numero, new BigDecimal("700.00"))
        facturaPort.findByCufe("CUFE-" + numero) >> Optional.of(buildFactura(numero))
        negociacionPort.findById("neg-001") >> Optional.of(neg)

        when:
        service.revertirPorFactura("neg-001", "CUFE-" + numero, numero, "USR-OP", "Motivo")

        then:
        1 * reclamoPort.reclamar(numero, "neg-001") >> false
        0 * cupoReversionService._
        0 * userPort._
        1 * facturaPatchPort.actualizarEstadoFirmaPorCufe({ it.cufe() == "CUFE-" + numero }, EstadoFirma.RECHAZADA)
        0 * notificacionService._
    }

    // ─── Fase 28d (C): por negociación y CUFE ────────────────────────────────

    def "Fase 28d (C): sin CUFE o sin negociación => [EVENTO-SIN-CUFE], contador y nada se toca (#caso)"() {
        given:
        def antes = EventosSinCufe.rechazados()

        when:
        service.revertirPorFactura(negociacion, cufe, "FECI-X", "USR-OP", "Motivo")

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.startsWith("[EVENTO-SIN-CUFE]")
        EventosSinCufe.rechazados() == antes + 1
        0 * negociacionPort._
        0 * facturaPort._
        0 * reclamoPort._
        0 * cupoReversionService._
        0 * facturaPatchPort._

        where:
        caso              | negociacion | cufe
        "sin CUFE"        | "neg-001"   | null
        "CUFE vacío"      | "neg-001"   | " "
        "sin negociación" | null        | "CUFE-FECI-X"
    }

    def "Fase 28d (C): un CUFE que no está en la negociación => FACTURA_NO_COINCIDE sin revertir"() {
        given:
        negociacionPort.findById("neg-001") >> Optional.of(buildNegociacion("FACTORING", "FECI-1", BigDecimal.TEN))

        when:
        service.revertirPorFactura("neg-001", "CUFE-DE-OTRA-EMPRESA", "FECI-1", "USR-OP", "Motivo")

        then:
        def ex = thrown(com.negociadororq.application.exception.ConflictoException)
        ex.codigo == ResolutorFactura.FACTURA_NO_COINCIDE
        0 * reclamoPort._
        0 * cupoReversionService._
        0 * facturaPatchPort._
    }

    def "Fase 28d (C): dos empresas con la factura 1: se revierte y marca solo la de la negociación (por su CUFE)"() {
        given: "la negociación de A tiene su factura 1; B tiene otra factura 1 en la base"
        def neg = buildNegociacion("FACTORING", "1", new BigDecimal("300.00"))
        negociacionPort.findById("neg-001") >> Optional.of(neg)
        facturaPort.findByCufe("CUFE-1") >> Optional.of(buildFactura("1"))
        userPort.findByUsername("USR-OP") >> Optional.of(User.builder().ideUser(1L).build())
        companyPort.findByNit(_) >> Optional.of(Company.builder().ideCompany(10L).build())

        when:
        service.revertirPorFactura("neg-001", "CUFE-1", "1", "USR-OP", "Motivo")

        then:
        0 * facturaPort.findAllByNumeroFactura(_)
        1 * facturaPatchPort.actualizarEstadoFirmaPorCufe(
                new com.negociadororq.domain.model.ReferenciaFactura("CUFE-1", "PROV-001", "PAG-001"), EstadoFirma.RECHAZADA)
        0 * facturaPatchPort.actualizarEstadoFirmaPorCufe({ it.cufe() != "CUFE-1" }, _)
    }
}
