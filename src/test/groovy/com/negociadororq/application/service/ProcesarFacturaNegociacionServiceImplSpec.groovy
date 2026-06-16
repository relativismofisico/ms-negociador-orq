package com.negociadororq.application.service

import com.negociadororq.application.dto.NegociacionRequestDto
import com.negociadororq.application.dto.ValidacionFacturaResponseDto
import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.EmpresaQuotaOutputPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.FacturaPatchOutputPort
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort
import com.negociadororq.application.port.output.UserOutputPort
import com.negociadororq.domain.enums.EstadoFirma
import com.negociadororq.domain.model.ActorFactura
import com.negociadororq.domain.model.Company
import com.negociadororq.domain.model.EmpresaQuota
import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.FondeadorAsignacion
import com.negociadororq.domain.model.FondeadorQuota
import com.negociadororq.domain.model.Operacion
import com.negociadororq.domain.model.User
import com.negociadororq.domain.model.UserQuota
import spock.lang.Specification

import java.math.BigDecimal

class ProcesarFacturaNegociacionServiceImplSpec extends Specification {

    FacturaOutputPort facturaPort = Mock()
    CupoConsumidorService cupoConsumidorService = Mock()
    UserOutputPort userPort = Mock()
    CompanyOutputPort companyPort = Mock()
    EmpresaQuotaOutputPort empresaQuotaPort = Mock()
    FondeadorQuotaOutputPort fondeadorQuotaPort = Mock()
    FacturaPatchOutputPort facturaPatchPort = Mock()
    ValidacionFacturaService validacionFacturaService = Mock()

    ProcesarFacturaNegociacionServiceImpl service = new ProcesarFacturaNegociacionServiceImpl(
            facturaPort, cupoConsumidorService, userPort, companyPort,
            empresaQuotaPort, fondeadorQuotaPort, facturaPatchPort, validacionFacturaService
    )

    def buildFactura(String numero, String rutProv, String rutPag) {
        def f = new Factura()
        f.id = "id-" + numero
        f.numeroFactura = numero
        f.rutProveedor = rutProv
        f.rutPagador = rutPag
        f.estadoNegociacion = "SIN_NEGOCIAR"
        f.valorFactura = new BigDecimal("5000.00")
        return f
    }

    def buildRequest(String tipo, String rutEmpresa, String rutOp) {
        NegociacionRequestDto.builder()
                .tipoOperacion(new Operacion(tipo, tipo == "FACTORING" ? "DESC" : "PAGO"))
                .rutEmpresa(rutEmpresa)
                .rutOperador(rutOp)
                .rolEmpresa("PROVEEDOR")
                .fondeadoresGlobales([FondeadorAsignacion.builder()
                        .rutFondeador("FOND-001")
                        .nombreFondeador("Fondo")
                        .build()])
                .build()
    }

    def buildReqFact(String numero) {
        FacturaNegociada.builder()
                .numeroFactura(numero)
                .build()
    }

    def setupMocksFactoring(String numero, String rutProv, BigDecimal valor = new BigDecimal("5000.00")) {
        def factura = buildFactura(numero, rutProv, "PAG-001")
        factura.valorFactura = valor

        facturaPort.findByNumeroFactura(numero) >> Optional.of(factura)
        validacionFacturaService.validarFacturaVencida(numero, null) >>
                ValidacionFacturaResponseDto.builder().valida(true).build()
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        cupoConsumidorService.obtenerCupoDisponible(1L) >> new BigDecimal("10000.00")
        companyPort.findByNit(rutProv) >> Optional.of(Company.builder().ideCompany(10L).build())
        empresaQuotaPort.findByCompanyId(10L) >> Optional.of(
                EmpresaQuota.builder().currentQuota(new BigDecimal("9000.00")).build()
        )
        // Fondeador FACTORING
        companyPort.findByNit("FOND-001") >> Optional.of(Company.builder().ideCompany(20L).build())
        companyPort.findByNit("PAG-001") >> Optional.of(Company.builder().ideCompany(30L).build())

        def fq = FondeadorQuota.builder()
                .funderId(20L).supplierId(10L).payerId(30L)
                .supplierCurrentQuota(new BigDecimal("8000.00"))
                .payerCurrentQuota(new BigDecimal("8000.00"))
                .build()

        fondeadorQuotaPort.findByFunderIdAndSupplierId(20L, 10L) >> Optional.of(fq)
        fondeadorQuotaPort.findByFunderIdAndPayerId(20L, 30L) >> Optional.of(fq)

        return factura
    }

    // ─── flujo exitoso FACTORING ──────────────────────────────────────────────

    def "procesarFactura FACTORING exitoso descuenta cupos y actualiza factura"() {
        given:
        setupMocksFactoring("FECI001", "PROV-001")
        def request = buildRequest("FACTORING", "PROV-001", "OP-001")
        def reqFact = buildReqFact("FECI001")

        when:
        def resultado = service.procesarFactura(request, reqFact)

        then:
        resultado.numeroFactura == "FECI001"
        resultado.rutProveedor == "PROV-001"
        resultado.rutPagador == "PAG-001"
        1 * cupoConsumidorService.descontarCupo(1L, _)
        1 * empresaQuotaPort.save(_)
        1 * facturaPatchPort.marcarComoNegociada(_, EstadoFirma.SIN_CESION, _)
    }

    // ─── flujo exitoso CONFIRMING ─────────────────────────────────────────────

    def "procesarFactura CONFIRMING exitoso usa EstadoFirma.SIN_DESCUENTO"() {
        given:
        def factura = buildFactura("FECI002", "PROV-001", "PAG-001")
        factura.estadoNegociacion = "SIN_NEGOCIAR"

        facturaPort.findByNumeroFactura("FECI002") >> Optional.of(factura)
        validacionFacturaService.validarFacturaVencida("FECI002", null) >>
                ValidacionFacturaResponseDto.builder().valida(true).build()
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        cupoConsumidorService.obtenerCupoDisponible(1L) >> new BigDecimal("10000.00")
        companyPort.findByNit("PAG-001") >> Optional.of(Company.builder().ideCompany(10L).build())
        empresaQuotaPort.findByCompanyId(10L) >> Optional.of(
                EmpresaQuota.builder().currentQuota(new BigDecimal("9000.00")).build()
        )
        companyPort.findByNit("FOND-001") >> Optional.of(Company.builder().ideCompany(20L).build())
        companyPort.findByNit("PROV-001") >> Optional.of(Company.builder().ideCompany(40L).build())
        def fqp = FondeadorQuota.builder()
                .funderId(20L).payerId(10L)
                .payerCurrentQuota(new BigDecimal("8000.00"))
                .supplierCurrentQuota(new BigDecimal("0.00"))
                .build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(20L, 10L) >> Optional.of(fqp)
        fondeadorQuotaPort.findByFunderIdAndSupplierId(_, _) >> Optional.of(fqp)

        def request = buildRequest("CONFIRMING", "PAG-001", "OP-001")
        def reqFact = buildReqFact("FECI002")

        when:
        def resultado = service.procesarFactura(request, reqFact)

        then:
        1 * facturaPatchPort.marcarComoNegociada(_, EstadoFirma.SIN_DESCUENTO, _)
    }

    // ─── validacion falla ─────────────────────────────────────────────────────

    def "procesarFactura lanza RuntimeException cuando factura vencida"() {
        given:
        def factura = buildFactura("FECI003", "PROV-001", "PAG-001")
        facturaPort.findByNumeroFactura("FECI003") >> Optional.of(factura)
        validacionFacturaService.validarFacturaVencida("FECI003", null) >>
                ValidacionFacturaResponseDto.builder()
                        .valida(false)
                        .mensaje("Factura no negociable")
                        .build()

        def request = buildRequest("FACTORING", "PROV-001", "OP-001")

        when:
        service.procesarFactura(request, buildReqFact("FECI003"))

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Factura no negociable")
    }

    // ─── estado incorrecto ────────────────────────────────────────────────────

    def "procesarFactura lanza RuntimeException cuando estado no es SIN_NEGOCIAR"() {
        given:
        def factura = buildFactura("FECI004", "PROV-001", "PAG-001")
        factura.estadoNegociacion = "NEGOCIADA"

        facturaPort.findByNumeroFactura("FECI004") >> Optional.of(factura)
        validacionFacturaService.validarFacturaVencida("FECI004", null) >>
                ValidacionFacturaResponseDto.builder().valida(true).build()

        def request = buildRequest("FACTORING", "PROV-001", "OP-001")

        when:
        service.procesarFactura(request, buildReqFact("FECI004"))

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("no negociable")
    }

    // ─── permiso incorrecto FACTORING ─────────────────────────────────────────

    def "procesarFactura FACTORING lanza RuntimeException si empresa no es proveedor"() {
        given:
        def factura = buildFactura("FECI005", "OTRO-PROV", "PAG-001")
        facturaPort.findByNumeroFactura("FECI005") >> Optional.of(factura)
        validacionFacturaService.validarFacturaVencida("FECI005", null) >>
                ValidacionFacturaResponseDto.builder().valida(true).build()

        def request = buildRequest("FACTORING", "PROV-INCORRECTO", "OP-001")

        when:
        service.procesarFactura(request, buildReqFact("FECI005"))

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("proveedor")
    }

    // ─── permiso incorrecto CONFIRMING ────────────────────────────────────────

    def "procesarFactura CONFIRMING lanza RuntimeException si empresa no es pagador"() {
        given:
        def factura = buildFactura("FECI006", "PROV-001", "OTRO-PAG")
        facturaPort.findByNumeroFactura("FECI006") >> Optional.of(factura)
        validacionFacturaService.validarFacturaVencida("FECI006", null) >>
                ValidacionFacturaResponseDto.builder().valida(true).build()

        def request = buildRequest("CONFIRMING", "PAG-INCORRECTO", "OP-001")

        when:
        service.procesarFactura(request, buildReqFact("FECI006"))

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("pagador")
    }

    // ─── cupo operador insuficiente ───────────────────────────────────────────

    def "procesarFactura lanza RuntimeException cuando cupo operador insuficiente"() {
        given:
        def factura = buildFactura("FECI007", "PROV-001", "PAG-001")
        facturaPort.findByNumeroFactura("FECI007") >> Optional.of(factura)
        validacionFacturaService.validarFacturaVencida("FECI007", null) >>
                ValidacionFacturaResponseDto.builder().valida(true).build()
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        cupoConsumidorService.obtenerCupoDisponible(1L) >> new BigDecimal("1.00") // insuficiente

        def request = buildRequest("FACTORING", "PROV-001", "OP-001")

        when:
        service.procesarFactura(request, buildReqFact("FECI007"))

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("sin cupo")
    }

    // ─── empresa sin cupo ─────────────────────────────────────────────────────

    def "procesarFactura lanza RuntimeException cuando empresa sin cupo"() {
        given:
        def factura = buildFactura("FECI008", "PROV-001", "PAG-001")
        facturaPort.findByNumeroFactura("FECI008") >> Optional.of(factura)
        validacionFacturaService.validarFacturaVencida("FECI008", null) >>
                ValidacionFacturaResponseDto.builder().valida(true).build()
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        cupoConsumidorService.obtenerCupoDisponible(1L) >> new BigDecimal("10000.00")
        companyPort.findByNit("PROV-001") >> Optional.of(Company.builder().ideCompany(10L).build())
        empresaQuotaPort.findByCompanyId(10L) >> Optional.of(
                EmpresaQuota.builder().currentQuota(new BigDecimal("1.00")).build() // insuficiente
        )

        def request = buildRequest("FACTORING", "PROV-001", "OP-001")

        when:
        service.procesarFactura(request, buildReqFact("FECI008"))

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("sin cupo")
    }

    // ─── fondeadores de reqFact tienen prioridad ──────────────────────────────

    def "obtenerFondeadores usa fondeadores de reqFact si los tiene"() {
        given:
        def factura = buildFactura("FECI009", "PROV-001", "PAG-001")
        facturaPort.findByNumeroFactura("FECI009") >> Optional.of(factura)
        validacionFacturaService.validarFacturaVencida("FECI009", null) >>
                ValidacionFacturaResponseDto.builder().valida(true).build()
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        cupoConsumidorService.obtenerCupoDisponible(1L) >> new BigDecimal("10000.00")
        companyPort.findByNit("PROV-001") >> Optional.of(Company.builder().ideCompany(10L).build())
        empresaQuotaPort.findByCompanyId(10L) >> Optional.of(
                EmpresaQuota.builder().currentQuota(new BigDecimal("9000.00")).build()
        )
        companyPort.findByNit("FOND-LOCAL") >> Optional.of(Company.builder().ideCompany(50L).build())
        companyPort.findByNit("PAG-001") >> Optional.of(Company.builder().ideCompany(30L).build())

        def fq = FondeadorQuota.builder()
                .funderId(50L).supplierId(10L)
                .supplierCurrentQuota(new BigDecimal("8000.00"))
                .payerCurrentQuota(new BigDecimal("8000.00"))
                .build()
        fondeadorQuotaPort.findByFunderIdAndSupplierId(50L, 10L) >> Optional.of(fq)
        fondeadorQuotaPort.findByFunderIdAndPayerId(50L, 30L) >> Optional.of(fq)

        def request = buildRequest("FACTORING", "PROV-001", "OP-001")
        // fondeadoresGlobales tiene FOND-001, pero reqFact tiene FOND-LOCAL
        def reqFact = FacturaNegociada.builder()
                .numeroFactura("FECI009")
                .fondeadores([FondeadorAsignacion.builder().rutFondeador("FOND-LOCAL").build()])
                .build()

        when:
        def resultado = service.procesarFactura(request, reqFact)

        then:
        resultado.fondeadores[0].rutFondeador == "FOND-LOCAL"
    }

    // ─── fondeador sin cupo factoring ────────────────────────────────────────

    def "procesarFactura FACTORING lanza RuntimeException si fondeador sin cupo"() {
        given:
        def factura = buildFactura("FECI010", "PROV-001", "PAG-001")
        facturaPort.findByNumeroFactura("FECI010") >> Optional.of(factura)
        validacionFacturaService.validarFacturaVencida("FECI010", null) >>
                ValidacionFacturaResponseDto.builder().valida(true).build()
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        cupoConsumidorService.obtenerCupoDisponible(1L) >> new BigDecimal("10000.00")
        companyPort.findByNit("PROV-001") >> Optional.of(Company.builder().ideCompany(10L).build())
        empresaQuotaPort.findByCompanyId(10L) >> Optional.of(
                EmpresaQuota.builder().currentQuota(new BigDecimal("9000.00")).build()
        )
        companyPort.findByNit("FOND-001") >> Optional.of(Company.builder().ideCompany(20L).build())
        companyPort.findByNit("PAG-001") >> Optional.of(Company.builder().ideCompany(30L).build())

        def fq = FondeadorQuota.builder()
                .supplierCurrentQuota(new BigDecimal("1.00")) // insuficiente
                .payerCurrentQuota(new BigDecimal("1.00"))
                .build()
        fondeadorQuotaPort.findByFunderIdAndSupplierId(20L, 10L) >> Optional.of(fq)

        def request = buildRequest("FACTORING", "PROV-001", "OP-001")

        when:
        service.procesarFactura(request, buildReqFact("FECI010"))

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("cupo factoring")
    }

    // ─── construirActores filtra FONDEADOR existentes ─────────────────────────

    def "construirActores filtra actores FONDEADOR existentes y agrega nuevos"() {
        given:
        def actorExistente = new ActorFactura("PROV-001", "PROVEEDOR")
        def actorFondViejo = new ActorFactura("FOND-VIEJO", "FONDEADOR")
        def factura = buildFactura("FECI011", "PROV-001", "PAG-001")
        factura.actores = [actorExistente, actorFondViejo]

        facturaPort.findByNumeroFactura("FECI011") >> Optional.of(factura)
        validacionFacturaService.validarFacturaVencida("FECI011", null) >>
                ValidacionFacturaResponseDto.builder().valida(true).build()
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        cupoConsumidorService.obtenerCupoDisponible(1L) >> new BigDecimal("10000.00")
        companyPort.findByNit("PROV-001") >> Optional.of(Company.builder().ideCompany(10L).build())
        empresaQuotaPort.findByCompanyId(10L) >> Optional.of(
                EmpresaQuota.builder().currentQuota(new BigDecimal("9000.00")).build()
        )
        companyPort.findByNit("FOND-001") >> Optional.of(Company.builder().ideCompany(20L).build())
        companyPort.findByNit("PAG-001") >> Optional.of(Company.builder().ideCompany(30L).build())

        def fq = FondeadorQuota.builder()
                .supplierCurrentQuota(new BigDecimal("8000.00"))
                .payerCurrentQuota(new BigDecimal("8000.00"))
                .build()
        fondeadorQuotaPort.findByFunderIdAndSupplierId(20L, 10L) >> Optional.of(fq)
        fondeadorQuotaPort.findByFunderIdAndPayerId(20L, 30L) >> Optional.of(fq)

        def request = buildRequest("FACTORING", "PROV-001", "OP-001")

        when:
        service.procesarFactura(request, buildReqFact("FECI011"))

        then:
        1 * facturaPatchPort.marcarComoNegociada(_, _, { actores ->
            actores.any { it.rolEmpresaFactura == "PROVEEDOR" } &&
            actores.any { it.rolEmpresaFactura == "FONDEADOR" && it.rutEmpresa == "FOND-001" } &&
            !actores.any { it.rutEmpresa == "FOND-VIEJO" }
        })
    }

    // ─── normalizarNit ────────────────────────────────────────────────────────

    def "normalizarNit en comparacion factoring con puntos y guiones"() {
        given:
        def factura = buildFactura("FECI012", "900.341.104-5", "PAG-001")
        facturaPort.findByNumeroFactura("FECI012") >> Optional.of(factura)
        validacionFacturaService.validarFacturaVencida("FECI012", null) >>
                ValidacionFacturaResponseDto.builder().valida(true).build()
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        cupoConsumidorService.obtenerCupoDisponible(1L) >> new BigDecimal("10000.00")

        // rutEmpresa con puntos y guion debe coincidir con rutProveedor con puntos y guion
        companyPort.findByNit("900.341.104-5") >> Optional.of(Company.builder().ideCompany(10L).build())
        empresaQuotaPort.findByCompanyId(10L) >> Optional.of(
                EmpresaQuota.builder().currentQuota(new BigDecimal("9000.00")).build()
        )
        companyPort.findByNit("FOND-001") >> Optional.of(Company.builder().ideCompany(20L).build())
        companyPort.findByNit("PAG-001") >> Optional.of(Company.builder().ideCompany(30L).build())

        def fq = FondeadorQuota.builder()
                .supplierCurrentQuota(new BigDecimal("8000.00"))
                .payerCurrentQuota(new BigDecimal("8000.00"))
                .build()
        fondeadorQuotaPort.findByFunderIdAndSupplierId(20L, 10L) >> Optional.of(fq)
        fondeadorQuotaPort.findByFunderIdAndPayerId(20L, 30L) >> Optional.of(fq)

        // Empresa con puntos y guion que coincide con proveedor al normalizar
        def request = buildRequest("FACTORING", "900.341.104-5", "OP-001")

        when:
        def resultado = service.procesarFactura(request, buildReqFact("FECI012"))

        then:
        resultado != null
    }
}