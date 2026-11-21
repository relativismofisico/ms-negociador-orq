package com.negociadororq.application.service

import com.negociadororq.application.dto.AprobacionFacturaRequestDto
import com.negociadororq.application.dto.AprobacionRequestDto
import com.negociadororq.application.exception.ConflictoException
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
import com.negociadororq.domain.enums.RolActor
import com.negociadororq.domain.model.AprobacionActorDto
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.FondeadorAsignacion
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import spock.lang.Specification

import java.time.LocalDateTime

/**
 * Fase 28d (firma única): una firma por empresa y por solicitud. Un segundo operador de una empresa que ya firmó se
 * rechaza al pedir el OTP (sin gastar OTP ni estampar un segundo bloque).
 */
class FirmaUnicaPorEmpresaSpec extends Specification {

    static final String PAG = "PAG-1"
    static final String PROV = "PROV-1"
    static final String FOND = "FOND-1"

    NegociacionOutputPort negociacionPort = Mock()
    EndosoPatchOutputPort endosoPatchPort = Mock()
    FirmaEndosoService firmaEndosoService = Mock()
    OutboxService outboxService = Mock()
    PendingOtpOutputPort pendingOtpPort = Mock()
    ClockPort clock = Stub() { now() >> LocalDateTime.of(2026, 11, 16, 10, 0) }

    AprobacionEndosoDescuentoServiceImpl service = new AprobacionEndosoDescuentoServiceImpl(
            negociacionPort, Stub(FacturaPatchOutputPort) { marcarFirmaFinalConSolicitud(*_) >> true; actualizarEstadosSiNoVencida(*_) >> true }, endosoPatchPort, outboxService,
            Mock(ReversionExactaService), Mock(CompanyOutputPort), Mock(FondeadorQuotaOutputPort), Mock(FacturaOutputPort),
            Mock(UserOutputPort), pendingOtpPort, firmaEndosoService, Mock(NotificacionService),
            Mock(EndosoOutputPort), Mock(DocumentoEndosoService), clock,
            "topic-otp-request", "topic-liquidacion", Mock(AuditoriaNegociacionService), Mock(SeguimientoLiquidacionService), TransaccionMongoDirecta.INSTANCIA)

    Negociacion negociacion(String tipo, String rutQueYaFirmo, RolActor rol) {
        def aprobaciones = rutQueYaFirmo == null ? [] : [new AprobacionActorDto(rutActor: rutQueYaFirmo, rolActor: rol,
                aprobado: true, observacion: "ok", fecha: LocalDateTime.of(2026, 11, 16, 9, 0), otpId: "50")]
        def fn = FacturaNegociada.builder().numeroFactura("FC-1").rutPagador(PAG).rutProveedor(PROV)
                .fondeadores([FondeadorAsignacion.builder().rutFondeador(FOND).build()])
                .aprobaciones(aprobaciones).estadoFirma(EstadoFirma.EN_APROBACION).build()
        Negociacion.builder().id("neg-1").tipoOperacion(new Operacion(tipo, "NORMAL")).facturas([fn]).build()
    }

    AprobacionRequestDto solicitud(String rut, String operador, String rol) {
        new AprobacionRequestDto("neg-1", operador, rut, rol, [new AprobacionFacturaRequestDto("FC-1", true, null)], null)
    }

    def "CONFIRMING: el Proveedor ya firmó; otro operador del Proveedor es rechazado sin pedir OTP"() {
        given:
        negociacionPort.findById("neg-1") >> Optional.of(negociacion("CONFIRMING", PROV, RolActor.PROVEEDOR))

        when:
        service.solicitarOtp(solicitud(PROV, "op-prov-2", "PROVEEDOR"))

        then:
        def e = thrown(ConflictoException)
        e.codigo == "FIRMA_EMPRESA_YA_REALIZADA"
        0 * outboxService.guardarEvento(*_)
    }

    def "FACTORING: el Pagador ya firmó; otro operador del Pagador es rechazado sin pedir OTP"() {
        given:
        negociacionPort.findById("neg-1") >> Optional.of(negociacion("FACTORING", PAG, RolActor.PAGADOR))

        when:
        service.solicitarOtp(solicitud(PAG, "op-pag-2", "PAGADOR"))

        then:
        def e = thrown(ConflictoException)
        e.codigo == "FIRMA_EMPRESA_YA_REALIZADA"
        0 * outboxService.guardarEvento(*_)
    }

    def "FACTORING: que el Pagador haya firmado no impide que el Fondeador pida su OTP"() {
        given:
        negociacionPort.findById("neg-1") >> Optional.of(negociacion("FACTORING", PAG, RolActor.PAGADOR))
        pendingOtpPort.registrarSolicitud(_) >> { args ->
            new com.negociadororq.application.port.output.ResultadoSolicitudOtp(
                    com.negociadororq.application.port.output.ResultadoSolicitudOtp.Tipo.NUEVA, args[0], null)
        }

        when:
        service.solicitarOtp(solicitud(FOND, "op-fond", "FONDEADOR"))

        then:
        1 * outboxService.guardarEvento("neg-1", "topic-otp-request", { it.rutEmpresa == FOND })
    }

    def "otro operador de la misma empresa con la firma en curso: el segundo es rechazado sin pedir OTP"() {
        given:
        negociacionPort.findById("neg-1") >> Optional.of(negociacion("FACTORING", null, null))
        pendingOtpPort.existeFirmaDeOtroOperador("neg-1", FOND, "op-fond-2") >> true

        when:
        service.solicitarOtp(solicitud(FOND, "op-fond-2", "FONDEADOR"))

        then:
        def e = thrown(ConflictoException)
        e.codigo == "FIRMA_EMPRESA_YA_REALIZADA"
        0 * pendingOtpPort.registrarSolicitud(_)
        0 * outboxService.guardarEvento(*_)
    }

    def "el mismo operador con una firma en curso no puede pedir otro OTP (FIRMA_EN_CURSO)"() {
        given:
        negociacionPort.findById("neg-1") >> Optional.of(negociacion("FACTORING", null, null))
        pendingOtpPort.registrarSolicitud(_) >> { args ->
            new com.negociadororq.application.port.output.ResultadoSolicitudOtp(
                    com.negociadororq.application.port.output.ResultadoSolicitudOtp.Tipo.FIRMA_EN_CURSO, args[0], null)
        }

        when:
        service.solicitarOtp(solicitud(FOND, "op-fond", "FONDEADOR"))

        then:
        def e = thrown(ConflictoException)
        e.codigo == "FIRMA_EN_CURSO"
        0 * outboxService.guardarEvento(*_)
    }

    def "al firmar: si otro OTP de la misma empresa ya reclamó la firma (dos operadores a la vez), no se estampa otro bloque"() {
        given:
        negociacionPort.findById("neg-1") >> Optional.of(negociacion("FACTORING", null, null))
        endosoPatchPort.reclamarFirmaEmpresa("neg-1", "FC-1", FOND, "91") >> false
        def req = new com.negociadororq.domain.model.AprobacionRequest("neg-1", "op-fond-2", FOND, "FONDEADOR",
                [new com.negociadororq.domain.model.AprobacionFacturaRequest("FC-1", true, null)], "ok")
        def pending = com.negociadororq.domain.model.PendingOtp.builder().id("p-91").idNegociacion("neg-1").rutEmpresa(FOND)
                .rutOperador("op-fond-2").otpId("91").request(req).build()

        when:
        service.aprobarInterno(pending)

        then:
        def e = thrown(ConflictoException)
        e.codigo == "ENDOSO_DUPLICADO"
        0 * firmaEndosoService.firmarPdfEndoso(*_)
        0 * endosoPatchPort.registrarAprobacionActor(*_)
    }
}
