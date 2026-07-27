package com.negociadororq.application.service

import com.negociadororq.application.dto.NegociacionRequestDto
import com.negociadororq.application.dto.ValidacionFacturaResponseDto
import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.EmpresaQuotaOutputPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.FacturaPatchOutputPort
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort
import com.negociadororq.application.port.output.IndirectFunderOfferOutputPort
import com.negociadororq.application.port.output.IndirectFunderOfferSupplierQuotaOutputPort
import com.negociadororq.application.port.output.PayerFundingVisibilityOutputPort
import com.negociadororq.application.port.output.UserOutputPort
import com.negociadororq.domain.enums.EstadoFirma
import com.negociadororq.domain.model.ActorFactura
import com.negociadororq.domain.model.Company
import com.negociadororq.domain.model.EmpresaQuota
import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.FondeadorAsignacion
import com.negociadororq.domain.model.FondeadorQuota
import com.negociadororq.domain.model.IndirectFunderOffer
import com.negociadororq.domain.model.IndirectFunderOfferSupplierQuota
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
    PayerFundingVisibilityOutputPort payerFundingVisibilityPort = Mock()
    IndirectFunderOfferOutputPort indirectFunderOfferPort = Mock()
    IndirectFunderOfferSupplierQuotaOutputPort indirectFunderOfferSupplierQuotaPort = Mock()

    ProcesarFacturaNegociacionServiceImpl service = new ProcesarFacturaNegociacionServiceImpl(
            facturaPort, cupoConsumidorService, userPort, companyPort,
            empresaQuotaPort, fondeadorQuotaPort, facturaPatchPort, validacionFacturaService,
            payerFundingVisibilityPort, indirectFunderOfferPort, indirectFunderOfferSupplierQuotaPort
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
                .estado("APROBADO")
                .aprobadoPorFondeador(true)
                .build()

        fondeadorQuotaPort.findByFunderIdAndSupplierId(20L, 10L) >> Optional.of(fq)
        fondeadorQuotaPort.findByFunderIdAndSupplierIdActivo(20L, 10L) >> Optional.of(fq)
        fondeadorQuotaPort.findByFunderIdAndPayerId(20L, 30L) >> Optional.of(fq)
        fondeadorQuotaPort.findByFunderIdAndPayerIdActivo(20L, 30L) >> Optional.of(fq)

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
                .estado("APROBADO")
                .aprobadoPorFondeador(true)
                .build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(20L, 10L) >> Optional.of(fqp)
        fondeadorQuotaPort.findByFunderIdAndPayerIdActivo(20L, 10L) >> Optional.of(fqp)
        fondeadorQuotaPort.findByFunderIdAndSupplierId(_, _) >> Optional.of(fqp)
        fondeadorQuotaPort.findByFunderIdAndSupplierIdActivo(_, _) >> Optional.of(fqp)

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
                .estado("APROBADO")
                .aprobadoPorFondeador(true)
                .build()
        fondeadorQuotaPort.findByFunderIdAndSupplierId(50L, 10L) >> Optional.of(fq)
        fondeadorQuotaPort.findByFunderIdAndSupplierIdActivo(50L, 10L) >> Optional.of(fq)
        fondeadorQuotaPort.findByFunderIdAndPayerId(50L, 30L) >> Optional.of(fq)
        fondeadorQuotaPort.findByFunderIdAndPayerIdActivo(50L, 30L) >> Optional.of(fq)

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
                .estado("APROBADO")
                .aprobadoPorFondeador(true)
                .build()
        fondeadorQuotaPort.findByFunderIdAndSupplierId(20L, 10L) >> Optional.of(fq)
        fondeadorQuotaPort.findByFunderIdAndSupplierIdActivo(20L, 10L) >> Optional.of(fq)

        def request = buildRequest("FACTORING", "PROV-001", "OP-001")

        when:
        service.procesarFactura(request, buildReqFact("FECI010"))

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("cupo factoring")
    }

    // ─── fondeador con la asignación desactivada ──────────────────────────────

    def "procesarFactura lanza excepcion si el fondeador desactivo la asignacion (no aparece via findByFunderIdAndSupplierIdActivo)"() {
        given:
        def factura = buildFactura("FECI014", "PROV-001", "PAG-001")
        facturaPort.findByNumeroFactura("FECI014") >> Optional.of(factura)
        validacionFacturaService.validarFacturaVencida("FECI014", null) >>
                ValidacionFacturaResponseDto.builder().valida(true).build()
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        cupoConsumidorService.obtenerCupoDisponible(1L) >> new BigDecimal("10000.00")
        companyPort.findByNit("PROV-001") >> Optional.of(Company.builder().ideCompany(10L).build())
        empresaQuotaPort.findByCompanyId(10L) >> Optional.of(
                EmpresaQuota.builder().currentQuota(new BigDecimal("9000.00")).build()
        )
        companyPort.findByNit("FOND-001") >> Optional.of(Company.builder().ideCompany(20L).build())
        companyPort.findByNit("PAG-001") >> Optional.of(Company.builder().ideCompany(30L).build())

        // La asignación existe (findByFunderIdAndSupplierId la encontraría),
        // pero está desactivada: la búsqueda "Activo" no la debe encontrar.
        fondeadorQuotaPort.findByFunderIdAndSupplierIdActivo(20L, 10L) >> Optional.empty()

        def request = buildRequest("FACTORING", "PROV-001", "OP-001")

        when:
        service.procesarFactura(request, buildReqFact("FECI014"))

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("no tiene una asignación de cupo activa")
    }

    // ─── fondeador con asignacion pendiente de aprobacion ─────────────────────

    def "procesarFactura lanza RuntimeException si la asignacion de fondeo no esta APROBADA"() {
        given:
        def factura = buildFactura("FECI013", "PROV-001", "PAG-001")
        facturaPort.findByNumeroFactura("FECI013") >> Optional.of(factura)
        validacionFacturaService.validarFacturaVencida("FECI013", null) >>
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
                .estado("PENDIENTE")
                .aprobadoPorFondeador(true)
                .build()
        fondeadorQuotaPort.findByFunderIdAndSupplierId(20L, 10L) >> Optional.of(fq)
        fondeadorQuotaPort.findByFunderIdAndSupplierIdActivo(20L, 10L) >> Optional.of(fq)

        def request = buildRequest("FACTORING", "PROV-001", "OP-001")

        when:
        service.procesarFactura(request, buildReqFact("FECI013"))

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("no está aprobada")
    }

    def "procesarFactura lanza RuntimeException si la asignacion aun no fue confirmada por el fondeador"() {
        given:
        def factura = buildFactura("FECI014", "PROV-001", "PAG-001")
        facturaPort.findByNumeroFactura("FECI014") >> Optional.of(factura)
        validacionFacturaService.validarFacturaVencida("FECI014", null) >>
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
                .estado("APROBADO")
                .aprobadoPorFondeador(false)
                .build()
        fondeadorQuotaPort.findByFunderIdAndSupplierId(20L, 10L) >> Optional.of(fq)
        fondeadorQuotaPort.findByFunderIdAndSupplierIdActivo(20L, 10L) >> Optional.of(fq)

        def request = buildRequest("FACTORING", "PROV-001", "OP-001")

        when:
        service.procesarFactura(request, buildReqFact("FECI014"))

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("no está aprobada")
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
                .estado("APROBADO")
                .aprobadoPorFondeador(true)
                .build()
        fondeadorQuotaPort.findByFunderIdAndSupplierId(20L, 10L) >> Optional.of(fq)
        fondeadorQuotaPort.findByFunderIdAndSupplierIdActivo(20L, 10L) >> Optional.of(fq)
        fondeadorQuotaPort.findByFunderIdAndPayerId(20L, 30L) >> Optional.of(fq)
        fondeadorQuotaPort.findByFunderIdAndPayerIdActivo(20L, 30L) >> Optional.of(fq)

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
                .estado("APROBADO")
                .aprobadoPorFondeador(true)
                .build()
        fondeadorQuotaPort.findByFunderIdAndSupplierId(20L, 10L) >> Optional.of(fq)
        fondeadorQuotaPort.findByFunderIdAndSupplierIdActivo(20L, 10L) >> Optional.of(fq)
        fondeadorQuotaPort.findByFunderIdAndPayerId(20L, 30L) >> Optional.of(fq)
        fondeadorQuotaPort.findByFunderIdAndPayerIdActivo(20L, 30L) >> Optional.of(fq)

        // Empresa con puntos y guion que coincide con proveedor al normalizar
        def request = buildRequest("FACTORING", "900.341.104-5", "OP-001")

        when:
        def resultado = service.procesarFactura(request, buildReqFact("FECI012"))

        then:
        resultado != null
    }

    // ─── factoring indirecto vía pagador (sin asignación directa) ─────────────

    def setupMocksIndirecto(String numero, BigDecimal valor = new BigDecimal("5000.00")) {
        def factura = buildFactura(numero, "PROV-001", "PAG-001")
        factura.valorFactura = valor

        facturaPort.findByNumeroFactura(numero) >> Optional.of(factura)
        validacionFacturaService.validarFacturaVencida(numero, null) >>
                ValidacionFacturaResponseDto.builder().valida(true).build()
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        cupoConsumidorService.obtenerCupoDisponible(1L) >> new BigDecimal("10000.00")
        companyPort.findByNit("PROV-001") >> Optional.of(Company.builder().ideCompany(10L).build())
        empresaQuotaPort.findByCompanyId(10L) >> Optional.of(
                EmpresaQuota.builder().currentQuota(new BigDecimal("9000.00")).build()
        )
        companyPort.findByNit("FOND-001") >> Optional.of(Company.builder().ideCompany(20L).build())
        companyPort.findByNit("PAG-001") >> Optional.of(Company.builder().ideCompany(30L).build())

        // Sin asignación directa funder->supplier: cae al fallback indirecto.
        fondeadorQuotaPort.findByFunderIdAndSupplierIdActivo(20L, 10L) >> Optional.empty()
        fondeadorQuotaPort.findByFunderIdAndSupplierId(20L, 10L) >> Optional.empty()

        return factura
    }

    def "procesarFactura FACTORING indirecto rechaza si el pagador no habilito el toggle"() {
        given:
        setupMocksIndirecto("FECI020")
        payerFundingVisibilityPort.estaHabilitado(30L) >> false

        def request = buildRequest("FACTORING", "PROV-001", "OP-001")

        when:
        service.procesarFactura(request, buildReqFact("FECI020"))

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("no tiene una asignación de cupo activa")
        0 * indirectFunderOfferPort.findActivaAprobada(_, _)
    }

    def "procesarFactura FACTORING indirecto rechaza si no hay oferta aprobada del fondeador hacia el pagador"() {
        given:
        setupMocksIndirecto("FECI021")
        payerFundingVisibilityPort.estaHabilitado(30L) >> true
        indirectFunderOfferPort.findActivaAprobada(20L, 30L) >> Optional.empty()

        def request = buildRequest("FACTORING", "PROV-001", "OP-001")

        when:
        service.procesarFactura(request, buildReqFact("FECI021"))

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("no tiene una asignación de cupo activa")
    }

    def "procesarFactura FACTORING indirecto rechaza si el cupo del proveedor (ya consumido antes) es insuficiente"() {
        given:
        setupMocksIndirecto("FECI022", new BigDecimal("5000.00"))
        payerFundingVisibilityPort.estaHabilitado(30L) >> true
        def oferta = IndirectFunderOffer.builder().id(77L).funderId(20L).payerId(30L)
                .payerRate(new BigDecimal("2.5")).supplierQuotaTemplate(new BigDecimal("10000.00")).build()
        indirectFunderOfferPort.findActivaAprobada(20L, 30L) >> Optional.of(oferta)
        indirectFunderOfferSupplierQuotaPort.find(77L, 10L) >> Optional.of(
                IndirectFunderOfferSupplierQuota.builder().id(1L).offerId(77L).supplierId(10L)
                        .currentQuota(new BigDecimal("1000.00")).build()
        )

        def request = buildRequest("FACTORING", "PROV-001", "OP-001")

        when:
        service.procesarFactura(request, buildReqFact("FECI022"))

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Fondeador sin cupo factoring")
    }

    def "procesarFactura FACTORING indirecto exitoso (primera negociacion) crea la fila de consumo con el cupo integro y descuenta"() {
        given:
        setupMocksIndirecto("FECI023", new BigDecimal("5000.00"))
        payerFundingVisibilityPort.estaHabilitado(30L) >> true
        def oferta = IndirectFunderOffer.builder().id(77L).funderId(20L).payerId(30L)
                .payerRate(new BigDecimal("2.5")).supplierQuotaTemplate(new BigDecimal("10000.00")).build()
        indirectFunderOfferPort.findActivaAprobada(20L, 30L) >> Optional.of(oferta)
        // Primera negociación de este proveedor contra la oferta: aún no existe fila de consumo.
        indirectFunderOfferSupplierQuotaPort.find(77L, 10L) >> Optional.empty()

        def request = buildRequest("FACTORING", "PROV-001", "OP-001")

        when:
        def resultado = service.procesarFactura(request, buildReqFact("FECI023"))

        then:
        resultado.numeroFactura == "FECI023"
        1 * indirectFunderOfferSupplierQuotaPort.crear(77L, 10L, new BigDecimal("2.5"), new BigDecimal("10000.00")) >>
                IndirectFunderOfferSupplierQuota.builder().id(1L).offerId(77L).supplierId(10L)
                        .rate(new BigDecimal("2.5")).quota(new BigDecimal("10000.00"))
                        .currentQuota(new BigDecimal("10000.00")).originalQuota(new BigDecimal("10000.00")).build()
        1 * indirectFunderOfferSupplierQuotaPort.save({ IndirectFunderOfferSupplierQuota q ->
            q.currentQuota == new BigDecimal("5000.00")
        })
        1 * facturaPatchPort.marcarComoNegociada(_, EstadoFirma.SIN_CESION, _)
    }

    def "procesarFactura FACTORING indirecto exitoso (segunda negociacion) descuenta sobre la fila de consumo existente sin recrearla"() {
        given:
        setupMocksIndirecto("FECI024", new BigDecimal("2000.00"))
        payerFundingVisibilityPort.estaHabilitado(30L) >> true
        def oferta = IndirectFunderOffer.builder().id(77L).funderId(20L).payerId(30L)
                .payerRate(new BigDecimal("2.5")).supplierQuotaTemplate(new BigDecimal("10000.00")).build()
        indirectFunderOfferPort.findActivaAprobada(20L, 30L) >> Optional.of(oferta)
        indirectFunderOfferSupplierQuotaPort.find(77L, 10L) >> Optional.of(
                IndirectFunderOfferSupplierQuota.builder().id(1L).offerId(77L).supplierId(10L)
                        .currentQuota(new BigDecimal("8000.00")).build()
        )

        def request = buildRequest("FACTORING", "PROV-001", "OP-001")

        when:
        service.procesarFactura(request, buildReqFact("FECI024"))

        then:
        0 * indirectFunderOfferSupplierQuotaPort.crear(_, _, _, _)
        1 * indirectFunderOfferSupplierQuotaPort.save({ IndirectFunderOfferSupplierQuota q ->
            q.currentQuota == new BigDecimal("6000.00")
        })
    }

    def "procesarFactura CONFIRMING no consulta el fallback indirecto (fuera de alcance para Confirming)"() {
        given:
        def factura = buildFactura("FECI025", "PROV-001", "PAG-001")
        facturaPort.findByNumeroFactura("FECI025") >> Optional.of(factura)
        validacionFacturaService.validarFacturaVencida("FECI025", null) >>
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
                .estado("APROBADO")
                .aprobadoPorFondeador(true)
                .build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(20L, 10L) >> Optional.of(fqp)
        fondeadorQuotaPort.findByFunderIdAndPayerIdActivo(20L, 10L) >> Optional.of(fqp)

        def request = buildRequest("CONFIRMING", "PAG-001", "OP-001")

        when:
        service.procesarFactura(request, buildReqFact("FECI025"))

        then:
        0 * payerFundingVisibilityPort.estaHabilitado(_)
        0 * indirectFunderOfferPort.findActivaAprobada(_, _)
    }
}
