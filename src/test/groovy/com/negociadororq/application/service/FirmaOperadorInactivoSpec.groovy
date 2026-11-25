package com.negociadororq.application.service

import com.negociadororq.application.dto.AprobacionFacturaRequestDto
import com.negociadororq.application.dto.AprobacionRequestDto
import com.negociadororq.application.dto.ConfirmacionOtpRequestDto
import com.negociadororq.application.exception.AccionNoPermitidaException
import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.EndosoOutputPort
import com.negociadororq.application.port.output.EndosoPatchOutputPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.FacturaPatchOutputPort
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort
import com.negociadororq.application.port.output.NegociacionOutputPort
import com.negociadororq.application.port.output.PendingOtpOutputPort
import com.negociadororq.application.port.output.ResultadoSolicitudOtp
import com.negociadororq.application.port.output.UserOutputPort
import com.negociadororq.domain.enums.EstadoFirma
import com.negociadororq.domain.model.Company
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.FondeadorAsignacion
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import com.negociadororq.domain.model.PendingOtp
import com.negociadororq.domain.model.User
import spock.lang.Specification

import java.time.LocalDateTime

/**
 * Fase 28d (P-09, prompt 2.5): las negociaciones vivas de un operador desactivado siguen su curso; su firma pendiente la
 * completa cualquier operador activo de la empresa (a su nombre, con la traza del reemplazo), y él ya no puede pedir ni
 * confirmar un OTP aunque su JWT no haya vencido.
 */
class FirmaOperadorInactivoSpec extends Specification {

    static final String FOND = "FOND-1"

    NegociacionOutputPort negociacionPort = Mock()
    OutboxService outboxService = Mock()
    PendingOtpOutputPort pendingOtpPort = Mock()
    UserOutputPort userPort = Mock()
    CompanyOutputPort companyPort = Mock()

    AprobacionEndosoDescuentoServiceImpl service = new AprobacionEndosoDescuentoServiceImpl(
            negociacionPort, Stub(FacturaPatchOutputPort), Mock(EndosoPatchOutputPort), outboxService,
            Mock(ReversionExactaService), companyPort, Mock(FondeadorQuotaOutputPort), Mock(FacturaOutputPort),
            userPort, pendingOtpPort, Mock(FirmaEndosoService), Mock(NotificacionService),
            Mock(EndosoOutputPort), Mock(DocumentoEndosoService), Stub(ClockPort) { now() >> LocalDateTime.of(2026, 11, 24, 10, 0) },
            "topic-otp-request", "topic-liquidacion", Mock(AuditoriaNegociacionService), Mock(SeguimientoLiquidacionService),
            TransaccionMongoDirecta.INSTANCIA)

    static User usuario(String username, Long estado) {
        User.builder().username(username).companyIde(9L).stateUserId(estado).build()
    }

    Negociacion negociacion() {
        def fn = FacturaNegociada.builder().numeroFactura("FC-1").rutPagador("PAG-1").rutProveedor("PROV-1")
                .fondeadores([FondeadorAsignacion.builder().rutFondeador(FOND).build()])
                .aprobaciones([]).estadoFirma(EstadoFirma.EN_APROBACION).build()
        Negociacion.builder().id("neg-1").tipoOperacion(new Operacion("FACTORING", "NORMAL")).facturas([fn]).build()
    }

    AprobacionRequestDto solicitud(String operador) {
        new AprobacionRequestDto("neg-1", operador, FOND, "FONDEADOR", [new AprobacionFacturaRequestDto("FC-1", true, null)], null)
    }

    def "un operador desactivado no pide OTP: 403 OPERADOR_DESACTIVADO, sin registrar la solicitud"() {
        given:
        negociacionPort.findById("neg-1") >> Optional.of(negociacion())
        userPort.findByUsername("op-inactivo") >> Optional.of(usuario("op-inactivo", 2L))

        when:
        service.solicitarOtp(solicitud("op-inactivo"))

        then:
        def e = thrown(AccionNoPermitidaException)
        e.codigo == "OPERADOR_DESACTIVADO"
        0 * pendingOtpPort.registrarSolicitud(_)
        0 * outboxService.guardarEvento(*_)
    }

    def "otro operador activo completa la firma que había pedido uno desactivado: a su nombre y con la traza"() {
        given:
        negociacionPort.findById("neg-1") >> Optional.of(negociacion())
        userPort.findByUsername("op-activo") >> Optional.of(usuario("op-activo", 1L))
        userPort.findByUsername("op-inactivo") >> Optional.of(usuario("op-inactivo", 2L))
        userPort.findByUsername("op-otro-activo") >> Optional.of(usuario("op-otro-activo", 1L))
        pendingOtpPort.operadoresConFirmaPendiente("neg-1", FOND, "op-activo") >> ["op-inactivo", "op-otro-activo"]
        PendingOtp registrada = null

        when:
        service.solicitarOtp(solicitud("op-activo"))

        then:
        1 * pendingOtpPort.registrarSolicitud(_) >> { args ->
            registrada = args[0]
            new ResultadoSolicitudOtp(ResultadoSolicitudOtp.Tipo.NUEVA, args[0], null)
        }
        registrada.rutOperador == "op-activo"
        registrada.reemplazaA == "op-inactivo"
    }

    def "sin operadores inactivos con la firma pedida, no hay traza de reemplazo"() {
        given:
        negociacionPort.findById("neg-1") >> Optional.of(negociacion())
        userPort.findByUsername("op-activo") >> Optional.of(usuario("op-activo", 1L))
        pendingOtpPort.operadoresConFirmaPendiente(*_) >> []
        PendingOtp registrada = null

        when:
        service.solicitarOtp(solicitud("op-activo"))

        then:
        1 * pendingOtpPort.registrarSolicitud(_) >> { args ->
            registrada = args[0]
            new ResultadoSolicitudOtp(ResultadoSolicitudOtp.Tipo.NUEVA, args[0], null)
        }
        registrada.reemplazaA == null
    }

    def "un operador desactivado no confirma su OTP aunque tenga la solicitud pendiente"() {
        given:
        def pendientes = Mock(PendingOtpOutputPort)
        def confirmacion = new ConfirmacionOtpServiceImpl(pendientes, Mock(ValidadorOtpPendiente), Mock(FirmaOtpService),
                userPort)
        userPort.findByUsername("op-inactivo") >> Optional.of(usuario("op-inactivo", 2L))
        def request = new ConfirmacionOtpRequestDto()
        request.idNegociacion = "neg-1"
        request.rutEmpresa = FOND
        request.rutOperador = "op-inactivo"

        when:
        confirmacion.confirmarOtp(request)

        then:
        def e = thrown(AccionNoPermitidaException)
        e.codigo == "OPERADOR_DESACTIVADO"
        0 * pendientes._
    }

    def "conteo para el aviso del front: negociaciones esperando la firma de la empresa por operadores inactivos"() {
        given:
        companyPort.findByNit(FOND) >> Optional.of(Company.builder().ideCompany(9L).build())
        companyPort.findByNit("NO-EXISTE") >> Optional.empty()
        userPort.usernamesInactivos(9L) >> ["op-inactivo"]
        pendingOtpPort.contarEsperandoFirmaDe(FOND, ["op-inactivo"]) >> 2L

        expect:
        service.contarEsperandoFirma(FOND) == 2L
        service.contarEsperandoFirma("NO-EXISTE") == 0L
    }
}
