package com.negociadororq.application.service

import com.negociadororq.application.dto.AprobacionFacturaRequestDto
import com.negociadororq.application.dto.AprobacionRequestDto
import com.negociadororq.application.dto.ConfirmacionOtpRequestDto
import com.negociadororq.application.exception.ConflictoException
import com.negociadororq.application.exception.RecursoNoEncontradoException
import com.negociadororq.application.exception.ReglaDeNegocioException
import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.EndosoPatchOutputPort
import com.negociadororq.application.port.output.EndosoOutputPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.FacturaPatchOutputPort
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort
import com.negociadororq.application.port.output.NegociacionOutputPort
import com.negociadororq.application.port.output.PendingOtpOutputPort
import com.negociadororq.application.port.output.UserOutputPort
import com.negociadororq.domain.enums.EstadoEndoso
import com.negociadororq.domain.enums.EstadoFirma
import com.negociadororq.domain.enums.EstadoNegociacion
import com.negociadororq.domain.enums.RolActor
import com.negociadororq.domain.model.AprobacionActorDto
import com.negociadororq.domain.model.AprobacionFacturaRequest
import com.negociadororq.domain.model.AprobacionRequest
import com.negociadororq.domain.model.Company
import com.negociadororq.domain.model.Endoso
import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.FondeadorAsignacion
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import com.negociadororq.domain.model.PendingOtp
import com.negociadororq.domain.model.User
import spock.lang.Specification

import java.math.BigDecimal
import java.time.LocalDateTime

class AprobacionEndosoDescuentoServiceImplSpec extends Specification {

    NegociacionOutputPort negociacionPort = Mock()
    FacturaPatchOutputPort facturaPatchPort = Mock()
    EndosoPatchOutputPort endosoPatchPort = Mock()
    OutboxService outboxService = Mock()
    CupoReversionService cupoReversionService = Mock()
    CompanyOutputPort companyPort = Mock()
    FondeadorQuotaOutputPort fondeadorQuotaPort = Mock()
    FacturaOutputPort facturaPort = Mock()
    UserOutputPort userPort = Mock()
    PendingOtpOutputPort pendingOtpPort = Mock()
    FirmaEndosoService firmaEndosoService = Mock()
    NotificacionService notificacionService = Mock()
    EndosoOutputPort endosoPort = Mock()
    DocumentoEndosoService documentoEndosoService = Mock()
    ClockPort clock = Mock() {
        now() >> LocalDateTime.now()
    }

    AprobacionEndosoDescuentoServiceImpl service = new AprobacionEndosoDescuentoServiceImpl(
            negociacionPort, facturaPatchPort, endosoPatchPort, outboxService,
            cupoReversionService, companyPort, fondeadorQuotaPort, facturaPort,
            userPort, pendingOtpPort, firmaEndosoService, notificacionService, endosoPort,
            documentoEndosoService, clock,
            "topic-otp-request", "topic-otp-validate", "topic-liquidacion"
    )

    def setup() {
        // Por defecto, cada factura tiene su propio Endoso sin agrupar (comportamiento
        // previo): así el recálculo de estado grupal de actualizarEstadoEndosoGrupal()
        // se reduce al caso de una sola factura, igual que antes de esta funcionalidad.
        endosoPort.findByNegociacionIdAndNumeroFactura(_, _) >> { String idNeg, String numeroFactura ->
            Optional.of(Endoso.builder().numerosFactura([numeroFactura]).build())
        }
    }

    // ─── solicitarOtp ─────────────────────────────────────────────────────────

    def "solicitarOtp guarda PendingOtp y emite evento OTP"() {
        given:
        def request = new AprobacionRequestDto(
                "neg-001", "OP-001", "EMP-001", "FONDEADOR",
                [new AprobacionFacturaRequestDto("FECI001", true, null)], null
        )

        when:
        service.solicitarOtp(request)

        then:
        1 * pendingOtpPort.save({ it.idNegociacion == "neg-001" && it.rutEmpresa == "EMP-001" })
        1 * outboxService.guardarEvento("neg-001", "topic-otp-request", {
            it.idNegociacion == "neg-001" && it.rutEmpresa == "EMP-001"
        })
    }

    def "solicitarOtp con facturas null mapea lista vacia"() {
        given:
        def request = new AprobacionRequestDto("neg-002", "OP-001", "EMP-001", "PAGADOR", null, null)

        when:
        service.solicitarOtp(request)

        then:
        1 * pendingOtpPort.save({ it.request.facturas.isEmpty() })
        1 * outboxService.guardarEvento(_, _, _)
    }

    // ─── confirmarOtp ─────────────────────────────────────────────────────────

    def "confirmarOtp emite evento de validacion"() {
        given:
        def request = new ConfirmacionOtpRequestDto(
                "neg-001", "EMP-001", "OP-001", "123456", "192.168.1.1", null
        )

        when:
        service.confirmarOtp(request)

        then:
        1 * outboxService.guardarEvento("neg-001", "topic-otp-validate", {
            it.otp == "123456" && it.ipAprobacion == "192.168.1.1"
        })
    }

    // ─── aprobarInterno ─────────────────────────────────────────────────────

    def buildPending(String negId, String rutEmp, List<AprobacionFacturaRequest> facturas) {
        def req = new AprobacionRequest(negId, "OP-001", rutEmp, "FONDEADOR", facturas, "obs")
        PendingOtp.builder()
                .id("otp-001")
                .idNegociacion(negId)
                .rutEmpresa(rutEmp)
                .otp("123456")
                .ip("10.0.0.1")
                .request(req)
                .build()
    }

    def buildFacturaNeg(String numero, String rutPag, String rutProv, List<FondeadorAsignacion> fondeadores) {
        FacturaNegociada.builder()
                .numeroFactura(numero)
                .rutPagador(rutPag)
                .rutProveedor(rutProv)
                .fondeadores(fondeadores)
                .aprobaciones([])
                .estadoFirma(EstadoFirma.EN_REVISION)
                .build()
    }

    def "aprobarInterno FACTORING pagador aprueba -> EN_APROBACION (alguienAprobo)"() {
        given:
        def fn = buildFacturaNeg("FECI001", "PAG-001", "PROV-001",
                [FondeadorAsignacion.builder().rutFondeador("FOND-001").build()])

        def neg = Negociacion.builder()
                .id("neg-001")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .facturas([fn])
                .build()

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        negociacionPort.save(_) >> neg

        def facturaReq = new AprobacionFacturaRequest("FECI001", true, null)
        def pending = buildPending("neg-001", "PAG-001", [facturaReq])

        when:
        def resultado = service.aprobarInterno(pending)

        then:
        1 * firmaEndosoService.firmarPdfEndoso("neg-001", fn, pending, "PAG-001", "OP-001", RolActor.PAGADOR, true, "obs")
        resultado.idNegociacion == "neg-001"
        resultado.errores.isEmpty()
    }

    def "aprobarInterno factura no encontrada en negociacion agrega error"() {
        given:
        def fn = buildFacturaNeg("FECI-OTRA", "PAG-001", "PROV-001", [])
        def neg = Negociacion.builder()
                .id("neg-001")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .facturas([fn])
                .build()

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        negociacionPort.save(_) >> neg

        def facturaReq = new AprobacionFacturaRequest("FECI-INEXISTENTE", true, null)
        def pending = buildPending("neg-001", "PAG-001", [facturaReq])

        when:
        def resultado = service.aprobarInterno(pending)

        then:
        resultado.errores.any { it.contains("FECI-INEXISTENTE") }
    }

    def "aprobarInterno lanza ConflictoException si actor ya endoso"() {
        given:
        def aprobacionExistente = new AprobacionActorDto("PAG-001", RolActor.PAGADOR, true, null, LocalDateTime.now())
        def fn = buildFacturaNeg("FECI001", "PAG-001", "PROV-001", [])
        fn.aprobaciones = [aprobacionExistente]

        def neg = Negociacion.builder()
                .id("neg-001")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .facturas([fn])
                .build()

        negociacionPort.findById("neg-001") >> Optional.of(neg)

        def facturaReq = new AprobacionFacturaRequest("FECI001", true, null)
        def pending = buildPending("neg-001", "PAG-001", [facturaReq])

        when:
        service.aprobarInterno(pending)

        then:
        def ex = thrown(ConflictoException)
        ex.codigo == "ENDOSO_DUPLICADO"
    }

    def "aprobarInterno lanza RecursoNoEncontradoException si negociacion no existe"() {
        given:
        negociacionPort.findById("NOPE") >> Optional.empty()

        def pending = buildPending("NOPE", "EMP", [])

        when:
        service.aprobarInterno(pending)

        then:
        def ex = thrown(RecursoNoEncontradoException)
        ex.codigo == "NEGOCIACION_NO_ENCONTRADA"
    }

    def "aprobarInterno FACTORING ambos actores aprueban -> CESION y evento liquidacion"() {
        given:
        def aprobFondeador = new AprobacionActorDto("FOND-001", RolActor.FONDEADOR, true, null, LocalDateTime.now())
        def fn = buildFacturaNeg("FECI001", "PAG-001", "PROV-001",
                [FondeadorAsignacion.builder().rutFondeador("FOND-001").build()])
        fn.aprobaciones = [aprobFondeador]
        fn.estadoFirma = EstadoFirma.EN_APROBACION

        def neg = Negociacion.builder()
                .id("neg-001")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .facturas([fn])
                .build()

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        negociacionPort.save(_) >> neg

        def facturaReq = new AprobacionFacturaRequest("FECI001", true, null)
        def pending = buildPending("neg-001", "PAG-001", [facturaReq])

        when:
        service.aprobarInterno(pending)

        then:
        // Cuando pagador ya esta en aprobaciones y ahora aprueba el pagador de nuevo
        // -> conflicto si mismo rut, pero aqui fondeador ya aprobó y ahora es pagador aprobando
        // En este caso fn.aprobaciones tiene FONDEADOR, el actor es PAG-001
        // actoresRequeridos FACTORING = {PAGADOR, FONDEADOR}
        // rolesQueAprobaron = {FONDEADOR} + {PAGADOR} = todosAprobaron = true
        1 * facturaPatchPort.actualizarEstadosPorNoFactura("FECI001", EstadoFirma.CESION, EstadoNegociacion.APROBADA)
        1 * endosoPatchPort.actualizarEstadoEndosoPorNoFactura("neg-001", "FECI001", EstadoEndoso.ACEPTADO)
        1 * outboxService.guardarEvento("neg-001", "topic-liquidacion", _)
    }

    def "aprobarInterno FACTORING fondeador rechaza -> revierte cupos y RECHAZADA"() {
        given:
        def fn = buildFacturaNeg("FECI001", "PAG-001", "PROV-001",
                [FondeadorAsignacion.builder().rutFondeador("FOND-001").build()])

        def neg = Negociacion.builder()
                .id("neg-001")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .facturas([fn])
                .build()

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        negociacionPort.save(_) >> neg

        def facturaReq = new AprobacionFacturaRequest("FECI001", false, "Rechazo")
        def pending = buildPending("neg-001", "FOND-001", [facturaReq])

        // Para revertirCupos necesitamos mocks adicionales
        def facturaDb = new Factura()
        facturaDb.numeroFactura = "FECI001"
        facturaDb.rutProveedor = "PROV-001"
        facturaDb.rutPagador = "PAG-001"
        facturaDb.valorFactura = new BigDecimal("1000.00")
        facturaPort.findByNumeroFactura("FECI001") >> Optional.of(facturaDb)
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        companyPort.findByNit("FOND-001") >> Optional.of(Company.builder().ideCompany(30L).build())
        companyPort.findByNit("PROV-001") >> Optional.of(Company.builder().ideCompany(40L).build())
        companyPort.findByNit("PAG-001") >> Optional.of(Company.builder().ideCompany(10L).build())

        when:
        def resultado = service.aprobarInterno(pending)

        then:
        1 * facturaPatchPort.actualizarEstadosPorNoFactura("FECI001", EstadoFirma.RECHAZADA, EstadoNegociacion.RECHAZADA)
        1 * endosoPatchPort.actualizarEstadoEndosoPorNoFactura("neg-001", "FECI001", EstadoEndoso.RECHAZADO)
        1 * cupoReversionService.revertirCupoUsuario(1L, _)
        resultado.estado == EstadoNegociacion.RECHAZADA.name()
    }

    def "calcularEstadoNegociacion con todas las facturas aprobadas retorna APROBADA"() {
        given:
        def fn = buildFacturaNeg("FECI001", "PAG-001", "PROV-001", [])

        def neg = Negociacion.builder()
                .id("neg-001")
                .tipoOperacion(new Operacion("CONFIRMING", "DESC"))
                .facturas([fn])
                .build()

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        negociacionPort.save(_) >> neg
        // El proveedor aprueba -> se intenta regenerar el documento con el
        // firmante; sin factura real que devolver, la regeneración se omite.
        facturaPort.findByNumeroFactura(_) >> Optional.empty()

        def facturaReq = new AprobacionFacturaRequest("FECI001", true, null)
        def pending = buildPending("neg-001", "PROV-001", [facturaReq])

        when:
        def resultado = service.aprobarInterno(pending)

        then:
        resultado.estado == EstadoNegociacion.APROBADA.name()
        fn.estadoFirma == EstadoFirma.DESCUENTO
        fn.estadoNegociacion == EstadoNegociacion.APROBADA
    }

    def "calcularEstadoNegociacion con una factura aprobada y otra aun sin solicitar (SIN_DESCUENTO) retorna EN_APROBACION"() {
        given:
        def fnAprobada = buildFacturaNeg("FECI001", "PAG-001", "PROV-001", [])
        def fnSinTocar = buildFacturaNeg("FECI002", "PAG-001", "PROV-001", [])
        fnSinTocar.estadoFirma = EstadoFirma.SIN_DESCUENTO

        def neg = Negociacion.builder()
                .id("neg-001")
                .tipoOperacion(new Operacion("CONFIRMING", "DESC"))
                .facturas([fnAprobada, fnSinTocar])
                .build()

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        negociacionPort.save(_) >> neg
        facturaPort.findByNumeroFactura(_) >> Optional.empty()

        def facturaReq = new AprobacionFacturaRequest("FECI001", true, null)
        def pending = buildPending("neg-001", "PROV-001", [facturaReq])

        when:
        def resultado = service.aprobarInterno(pending)

        then:
        // La negociación no puede quedar APROBADA mientras FECI002 ni siquiera
        // entró al proceso de aprobación (sigue en su estado inicial SIN_DESCUENTO).
        resultado.estado == EstadoNegociacion.EN_APROBACION.name()
    }

    def "calcularEstadoNegociacion con una factura aprobada y otra rechazada retorna PARCIAL"() {
        given:
        def fnAprobada = buildFacturaNeg("FECI001", "PAG-001", "PROV-001", [])
        def fnRechazada = buildFacturaNeg("FECI002", "PAG-001", "PROV-001", [])

        def neg = Negociacion.builder()
                .id("neg-001")
                .tipoOperacion(new Operacion("CONFIRMING", "DESC"))
                .facturas([fnAprobada, fnRechazada])
                .build()

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        negociacionPort.save(_) >> neg

        def facturaDb = new Factura()
        facturaDb.numeroFactura = "FECI002"
        facturaDb.rutProveedor = "PROV-001"
        facturaDb.rutPagador = "PAG-001"
        facturaDb.valorFactura = new BigDecimal("1000.00")
        facturaPort.findByNumeroFactura("FECI002") >> Optional.of(facturaDb)
        facturaPort.findByNumeroFactura(_) >> Optional.empty()
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        companyPort.findByNit(_) >> Optional.of(Company.builder().ideCompany(1L).build())

        def reqAprobada = new AprobacionFacturaRequest("FECI001", true, null)
        def reqRechazada = new AprobacionFacturaRequest("FECI002", false, "Rechazo")
        def pending = buildPending("neg-001", "PROV-001", [reqAprobada, reqRechazada])

        when:
        def resultado = service.aprobarInterno(pending)

        then:
        resultado.estado == EstadoNegociacion.PARCIAL.name()
    }

    def "aprobarInterno CONFIRMING proveedor aprueba -> DESCUENTO y evento liquidacion"() {
        given:
        def fn = buildFacturaNeg("FECI001", "PAG-001", "PROV-001", [])

        def neg = Negociacion.builder()
                .id("neg-001")
                .tipoOperacion(new Operacion("CONFIRMING", "PAGO"))
                .facturas([fn])
                .build()

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        negociacionPort.save(_) >> neg
        facturaPort.findByNumeroFactura(_) >> Optional.empty()

        def facturaReq = new AprobacionFacturaRequest("FECI001", true, null)
        def pending = buildPending("neg-001", "PROV-001", [facturaReq])

        when:
        service.aprobarInterno(pending)

        then:
        1 * facturaPatchPort.actualizarEstadosPorNoFactura("FECI001", EstadoFirma.DESCUENTO, EstadoNegociacion.APROBADA)
        1 * endosoPatchPort.actualizarEstadoEndosoPorNoFactura("neg-001", "FECI001", EstadoEndoso.ACEPTADO)
        1 * outboxService.guardarEvento("neg-001", "topic-liquidacion", _)
    }

    def "resolverRolActor lanza ReglaDeNegocioException si actor no identificable"() {
        given:
        def fn = buildFacturaNeg("FECI001", "PAG-001", "PROV-001", [])

        def neg = Negociacion.builder()
                .id("neg-001")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .facturas([fn])
                .build()

        negociacionPort.findById("neg-001") >> Optional.of(neg)

        // Actor desconocido (no es pagador, proveedor ni fondeador)
        def facturaReq = new AprobacionFacturaRequest("FECI001", true, null)
        def pending = buildPending("neg-001", "ACTOR-DESCONOCIDO", [facturaReq])

        when:
        service.aprobarInterno(pending)

        then:
        def ex = thrown(ReglaDeNegocioException)
        ex.codigo == "ROL_ACTOR_INDETERMINADO"
    }

    def "calcularEstadoNegociacion con pendientes retorna EN_APROBACION"() {
        given:
        def fn = buildFacturaNeg("FECI001", "PAG-001", "PROV-001", [])
        fn.estadoFirma = EstadoFirma.EN_APROBACION

        def neg = Negociacion.builder()
                .id("neg-001")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .facturas([fn])
                .build()

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        negociacionPort.save(_) >> neg

        def facturaReq = new AprobacionFacturaRequest("FECI001", true, null)
        def pending = buildPending("neg-001", "PAG-001", [facturaReq])

        when:
        def resultado = service.aprobarInterno(pending)

        then:
        resultado.estado == EstadoNegociacion.EN_APROBACION.name()
    }

    def "calcularEstadoNegociacion fallback retorna EN_APROBACION cuando ningun estado coincide"() {
        given:
        def fn1 = buildFacturaNeg("FECI001", "PAG-001", "PROV-001", [])
        fn1.estadoFirma = EstadoFirma.CESION

        def fn2 = buildFacturaNeg("FECI002", "PAG-001", "PROV-001", [])
        fn2.estadoFirma = EstadoFirma.RECHAZADA

        def neg = Negociacion.builder()
                .id("neg-001")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .facturas([fn1, fn2])
                .build()

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        negociacionPort.save(_) >> neg

        def facturaReq = new AprobacionFacturaRequest("FECI001", true, null)
        def pending = buildPending("neg-001", "PAG-001", [facturaReq])

        when:
        def resultado = service.aprobarInterno(pending)

        then:
        resultado.estado == EstadoNegociacion.EN_APROBACION.name()
    }

    def "revertirCupos con fondeadores FACTORING revierte cupo fondeador"() {
        given:
        def fn = FacturaNegociada.builder()
                .numeroFactura("FECI-R")
                .fondeadores([FondeadorAsignacion.builder().rutFondeador("FOND-001").build()])
                .build()

        def facturaDb = new Factura()
        facturaDb.valorFactura = new BigDecimal("500.00")
        facturaDb.rutProveedor = "PROV-001"
        facturaDb.rutPagador = "PAG-001"

        facturaPort.findByNumeroFactura("FECI-R") >> Optional.of(facturaDb)
        userPort.findByUsername("OP") >> Optional.of(User.builder().ideUser(5L).build())
        companyPort.findByNit("EMP") >> Optional.of(Company.builder().ideCompany(20L).build())
        companyPort.findByNit("FOND-001") >> Optional.of(Company.builder().ideCompany(30L).build())
        companyPort.findByNit("PROV-001") >> Optional.of(Company.builder().ideCompany(40L).build())

        def op = new Operacion("FACTORING", "DESC")

        when:
        service.revertirCupos(fn, op, "EMP", "OP")

        then:
        1 * cupoReversionService.revertirCupoFondeadorFactoring(30L, 40L, _)
        0 * cupoReversionService.revertirCupoFondeadorConfirming(_, _, _)
    }

    def "revertirCupos con CONFIRMING revierte cupo fondeador confirming"() {
        given:
        def fn = FacturaNegociada.builder()
                .numeroFactura("FECI-C")
                .fondeadores([FondeadorAsignacion.builder().rutFondeador("FOND-CF").build()])
                .build()

        def facturaDb = new Factura()
        facturaDb.valorFactura = new BigDecimal("700.00")
        facturaDb.rutProveedor = "PROV-CF"
        facturaDb.rutPagador = "PAG-CF"

        facturaPort.findByNumeroFactura("FECI-C") >> Optional.of(facturaDb)
        userPort.findByUsername("OP-CF") >> Optional.of(User.builder().ideUser(6L).build())
        companyPort.findByNit("EMP-CF") >> Optional.of(Company.builder().ideCompany(21L).build())
        companyPort.findByNit("FOND-CF") >> Optional.of(Company.builder().ideCompany(31L).build())
        companyPort.findByNit("PAG-CF") >> Optional.of(Company.builder().ideCompany(41L).build())

        def op = new Operacion("CONFIRMING", "PAGO")

        when:
        service.revertirCupos(fn, op, "EMP-CF", "OP-CF")

        then:
        1 * cupoReversionService.revertirCupoFondeadorConfirming(31L, 41L, _)
        0 * cupoReversionService.revertirCupoFondeadorFactoring(_, _, _)
    }

    def "revertirCupos con valorNetoFactura no nulo usa ese valor"() {
        given:
        def fn = FacturaNegociada.builder()
                .numeroFactura("FECI-V")
                .fondeadores([])
                .build()

        def facturaDb = new Factura()
        facturaDb.valorFactura = new BigDecimal("1000.00")
        facturaDb.valorNetoFactura = new BigDecimal("850.00") // este tiene prioridad
        facturaDb.rutProveedor = "PROV"
        facturaDb.rutPagador = "PAG"

        facturaPort.findByNumeroFactura("FECI-V") >> Optional.of(facturaDb)
        userPort.findByUsername("OP") >> Optional.of(User.builder().ideUser(1L).build())
        companyPort.findByNit("EMP") >> Optional.of(Company.builder().ideCompany(10L).build())

        def op = new Operacion("FACTORING", "DESC")

        when:
        service.revertirCupos(fn, op, "EMP", "OP")

        then:
        1 * cupoReversionService.revertirCupoUsuario(1L, new BigDecimal("850.00"))
    }

    def "revertirCupos cuando valorFactura y valorNeto son null usa ZERO"() {
        given:
        def fn = FacturaNegociada.builder()
                .numeroFactura("FECI-NULL")
                .fondeadores([])
                .build()

        def facturaDb = new Factura()
        facturaDb.valorFactura = null
        facturaDb.valorNetoFactura = null
        facturaDb.rutProveedor = "PROV"
        facturaDb.rutPagador = "PAG"

        facturaPort.findByNumeroFactura("FECI-NULL") >> Optional.of(facturaDb)
        userPort.findByUsername("OP") >> Optional.of(User.builder().ideUser(1L).build())
        companyPort.findByNit("EMP") >> Optional.of(Company.builder().ideCompany(10L).build())

        def op = new Operacion("FACTORING", "DESC")

        when:
        service.revertirCupos(fn, op, "EMP", "OP")

        then:
        1 * cupoReversionService.revertirCupoUsuario(1L, BigDecimal.ZERO)
    }
}