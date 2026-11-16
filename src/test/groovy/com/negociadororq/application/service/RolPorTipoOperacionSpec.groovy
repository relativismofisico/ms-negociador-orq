package com.negociadororq.application.service

import com.negociadororq.application.dto.AprobacionFacturaRequestDto
import com.negociadororq.application.dto.AprobacionRequestDto
import com.negociadororq.application.exception.ReglaDeNegocioException
import com.negociadororq.application.exception.RolNoPermitidoException
import com.negociadororq.application.port.output.AlertaFirmaOutputPort
import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.EndosoOutputPort
import com.negociadororq.application.port.output.EndosoPatchOutputPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.FacturaPatchOutputPort
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort
import com.negociadororq.application.port.output.NegociacionOutputPort
import com.negociadororq.application.port.output.PendingOtpOutputPort
import com.negociadororq.application.port.output.UserOutputPort
import com.negociadororq.domain.enums.EstadoFirma
import com.negociadororq.domain.model.AprobacionFacturaRequest
import com.negociadororq.domain.model.AprobacionRequest
import com.negociadororq.domain.model.EstadoFirmaOtp
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.FondeadorAsignacion
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import com.negociadororq.domain.model.PendingOtp
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.LocalDateTime

/**
 * Fase 28d (2.10): solo aprueban los roles que cuentan para el tipo de operación (FACTORING: Pagador y Fondeador;
 * CONFIRMING: Proveedor). El rol se resuelve en el servidor por el RUT; el que manda el cliente no es de fiar.
 */
class RolPorTipoOperacionSpec extends Specification {

    static final String PAG = "PAG-1"
    static final String PROV = "PROV-1"
    static final String FOND = "FOND-1"

    NegociacionOutputPort negociacionPort = Mock()
    OutboxService outboxService = Mock()
    CupoReversionService cupoReversionService = Mock()
    PendingOtpOutputPort pendingOtpPort = Mock()
    FirmaEndosoService firmaEndosoService = Mock()
    EndosoPatchOutputPort endosoPatchPort = Mock()
    FacturaPatchOutputPort facturaPatchPort = Mock()
    NotificacionService notificacionService = Mock()
    ClockPort clock = Stub() { now() >> LocalDateTime.of(2026, 11, 16, 10, 0) }

    AprobacionEndosoDescuentoServiceImpl service = new AprobacionEndosoDescuentoServiceImpl(
            negociacionPort, facturaPatchPort, endosoPatchPort, outboxService,
            cupoReversionService, Mock(CompanyOutputPort), Mock(FondeadorQuotaOutputPort), Mock(FacturaOutputPort),
            Mock(UserOutputPort), pendingOtpPort, firmaEndosoService, notificacionService, Mock(EndosoOutputPort),
            Mock(DocumentoEndosoService), clock,
            "topic-otp-request", "topic-otp-validate", "topic-liquidacion", Mock(AuditoriaNegociacionService))

    Negociacion negociacion(String tipo) {
        def fn = FacturaNegociada.builder().numeroFactura("FC-1").rutPagador(PAG).rutProveedor(PROV)
                .fondeadores([FondeadorAsignacion.builder().rutFondeador(FOND).build()])
                .aprobaciones([]).estadoFirma(EstadoFirma.EN_REVISION).build()
        Negociacion.builder().id("neg-1").tipoOperacion(new Operacion(tipo, "NORMAL")).facturas([fn]).build()
    }

    AprobacionRequestDto solicitud(String rut, String rolCliente) {
        new AprobacionRequestDto("neg-1", "op-1", rut, rolCliente, [new AprobacionFacturaRequestDto("FC-1", true, null)], null)
    }

    @Unroll
    def "#tipo con #rut (#rol): solicitarOtp #resultado"() {
        given:
        negociacionPort.findById("neg-1") >> Optional.of(negociacion(tipo))

        when:
        Exception error = null
        try {
            service.solicitarOtp(solicitud(rut, rol))
        } catch (RolNoPermitidoException e) {
            error = e
        }

        then: "si no está permitido, ni pendiente ni solicitud de OTP"
        (error == null) == permitido
        (permitido ? 1 : 0) * pendingOtpPort.registrarSolicitud({ PendingOtp p -> p.rolActor == rol }) >> { args -> new com.negociadororq.application.port.output.ResultadoSolicitudOtp(com.negociadororq.application.port.output.ResultadoSolicitudOtp.Tipo.NUEVA, args[0], null) }
        (permitido ? 1 : 0) * outboxService.guardarEvento("neg-1", "topic-otp-request", { it.rolActor == rol })
        !permitido ? error.codigo == "ROL_NO_PERMITIDO_PARA_OPERACION" : true
        // el mensaje no revela datos de otras empresas
        !permitido ? [PAG, PROV, FOND].every { !error.message.contains(it) } : true

        where:
        tipo         | rut  | rol         || permitido
        "CONFIRMING" | PAG  | "PAGADOR"   || false
        "CONFIRMING" | FOND | "FONDEADOR" || false
        "CONFIRMING" | PROV | "PROVEEDOR" || true
        "FACTORING"  | PAG  | "PAGADOR"   || true
        "FACTORING"  | FOND | "FONDEADOR" || true
        "FACTORING"  | PROV | "PROVEEDOR" || false
        resultado = permitido ? "pasa" : "se rechaza sin OTP"
    }

    def "el rol que manda el cliente no cuenta: se usa el resuelto por el servidor"() {
        given: "el Pagador dice ser Proveedor en un CONFIRMING"
        negociacionPort.findById("neg-1") >> Optional.of(negociacion("CONFIRMING"))

        when:
        service.solicitarOtp(solicitud(PAG, "PROVEEDOR"))

        then:
        thrown(RolNoPermitidoException)
        0 * pendingOtpPort.registrarSolicitud(_)
        0 * outboxService.guardarEvento(*_)
    }

    def "un rol permitido que el cliente envía mal se corrige al del servidor"() {
        given: "el Fondeador dice ser Pagador en un FACTORING (los dos aprueban)"
        negociacionPort.findById("neg-1") >> Optional.of(negociacion("FACTORING"))

        when:
        service.solicitarOtp(solicitud(FOND, "PAGADOR"))

        then:
        1 * pendingOtpPort.registrarSolicitud({ PendingOtp p -> p.rolActor == "FONDEADOR" && p.request.rolActor == "FONDEADOR" }) >> { args -> new com.negociadororq.application.port.output.ResultadoSolicitudOtp(com.negociadororq.application.port.output.ResultadoSolicitudOtp.Tipo.NUEVA, args[0], null) }
        1 * outboxService.guardarEvento(_, "topic-otp-request", { it.rolActor == "FONDEADOR" })
    }

    def "actor sin rol en la negociación: ROL_ACTOR_INDETERMINADO, como hoy, sin OTP"() {
        given:
        negociacionPort.findById("neg-1") >> Optional.of(negociacion("FACTORING"))

        when:
        service.solicitarOtp(solicitud("OTRA-EMPRESA", "PAGADOR"))

        then:
        def e = thrown(ReglaDeNegocioException)
        e.codigo == "ROL_ACTOR_INDETERMINADO"
        0 * pendingOtpPort.registrarSolicitud(_)
        0 * outboxService.guardarEvento(*_)
    }

    def "aprobarInterno (pendiente anterior al cambio) de un rol no permitido: sin firma, sin aprobación, sin cupos"() {
        given:
        negociacionPort.findById("neg-1") >> Optional.of(negociacion("CONFIRMING"))
        def req = new AprobacionRequest("neg-1", "op-1", PAG, "PAGADOR", [new AprobacionFacturaRequest("FC-1", true, null)], "ok")
        def pending = PendingOtp.builder().id("p-1").idNegociacion("neg-1").rutEmpresa(PAG).otp("111111").otpId("9")
                .request(req).build()

        when:
        service.aprobarInterno(pending)

        then:
        thrown(RolNoPermitidoException)
        0 * firmaEndosoService.firmarPdfEndoso(*_)
        0 * endosoPatchPort._
        0 * facturaPatchPort._
        0 * cupoReversionService._
        0 * negociacionPort.save(_)
        0 * outboxService._
        0 * notificacionService._
    }

    def "la firma deja el pendiente en ROL_NO_PERMITIDO (terminal) con alerta y sin el código"() {
        given:
        AprobacionInternaService aprobacion = Stub() {
            aprobarInterno(_) >> { throw new RolNoPermitidoException("CONFIRMING", "PAGADOR") }
        }
        AlertaFirmaOutputPort alerta = Mock()
        def firma = new FirmaOtpServiceImpl(pendingOtpPort, aprobacion, notificacionService, alerta, clock, 3,
                Duration.ofMinutes(2))
        def pending = PendingOtp.builder().id("p-1").idNegociacion("neg-1").rutEmpresa(PAG).otp("111111").otpId("9")
                .estado(EstadoFirmaOtp.OTP_VALIDADO.name()).build()

        when:
        firma.reintentarPendientes()

        then:
        1 * pendingOtpPort.findByEstadoAndFechaActualizacionBefore(_, _) >> [pending]
        1 * pendingOtpPort.save({ PendingOtp p -> p.estado == "ROL_NO_PERMITIDO" && p.otp == null })
        1 * alerta.rolNoPermitido("neg-1", PAG, "CONFIRMING", "PAGADOR")
        0 * alerta.firmaFallida()
        0 * notificacionService.notificarFirmaFallida(_)
    }
}
