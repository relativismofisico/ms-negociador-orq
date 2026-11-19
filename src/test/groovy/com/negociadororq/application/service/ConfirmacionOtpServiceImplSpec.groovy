package com.negociadororq.application.service

import com.negociadororq.application.dto.ConfirmacionOtpRequestDto
import com.negociadororq.application.exception.ConflictoException
import com.negociadororq.application.exception.ServicioDependienteNoDisponibleException
import com.negociadororq.application.port.output.PendingOtpOutputPort
import com.negociadororq.application.port.output.ValidacionOtpOutputPort
import com.negociadororq.domain.event.OtpValidatedEvent
import com.negociadororq.domain.model.PendingOtp
import com.negociadororq.domain.model.ValidacionOtp
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDateTime

/**
 * Fase 28d (A1): confirmar-otp valida por HTTP y entrega la respuesta a la misma lógica de firma que
 * otp.validate.response; sin pendiente que espere el código, no llama a security.
 */
class ConfirmacionOtpServiceImplSpec extends Specification {

    PendingOtpOutputPort pendientes = Mock()
    ValidacionOtpOutputPort security = Mock()
    FirmaOtpService firma = Mock()
    ConfirmacionOtpServiceImpl servicio = new ConfirmacionOtpServiceImpl(pendientes,
            new ValidadorOtpPendiente(security, pendientes), firma)

    static ConfirmacionOtpRequestDto request() {
        new ConfirmacionOtpRequestDto("NEG-1", "EMP-1", "OP-1", "123456", "10.0.0.1", null)
    }

    static PendingOtp pendiente(String estado = "PENDIENTE_OTP", String empresa = "EMP-1") {
        PendingOtp.builder().id("p-1").idNegociacion("NEG-1").rutEmpresa(empresa).rutOperador("OP-1").estado(estado)
                .respuestasOtp(2).build()
    }

    def "correcto: valida con la clave |n y firma con el código, la IP y los datos de security"() {
        given:
        pendientes.findByClave("NEG-1", "EMP-1", "OP-1") >> Optional.of(pendiente())
        OtpValidatedEvent recibido = null

        when:
        servicio.confirmarOtp(request())

        then:
        1 * security.validar({ ValidacionOtp.Solicitud s -> s.claveIdempotencia() == "NEG-1|EMP-1|OP-1|2" && s.otp() == "123456" }) >>
                new ValidacionOtp.Resultado(true, null, 0, "70", LocalDateTime.of(2026, 11, 19, 10, 0),
                        Instant.parse("2026-11-19T15:00:00Z"))
        1 * pendientes.registrarRespuestaOtp("p-1", 2) >> true
        1 * firma.procesarValidacion(_) >> { args -> recibido = args[0] }
        recibido.valido && recibido.otp == "123456" && recibido.otpId == "70" && recibido.ipAprobacion == "10.0.0.1"
        recibido.idNegociacion == "NEG-1" && recibido.rutEmpresa == "EMP-1" && recibido.rutOperador == "OP-1"
        recibido.instanteValidacion == Instant.parse("2026-11-19T15:00:00Z")
    }

    def "incorrecto: la firma recibe valido=false sin el código, con el motivo y los intentos"() {
        given:
        pendientes.findByClave(*_) >> Optional.of(pendiente())
        OtpValidatedEvent recibido = null

        when:
        servicio.confirmarOtp(request())

        then:
        1 * security.validar(_) >> new ValidacionOtp.Resultado(false, "INCORRECTO", 2, "70", null, null)
        1 * firma.procesarValidacion(_) >> { args -> recibido = args[0] }
        !recibido.valido && recibido.otp == null && recibido.motivo == "INCORRECTO" && recibido.intentosRestantes == 2
    }

    def "security no disponible: la excepción sube; ni avanza n ni se toca la firma"() {
        given:
        pendientes.findByClave(*_) >> Optional.of(pendiente())

        when:
        servicio.confirmarOtp(request())

        then:
        1 * security.validar(_) >> { throw new ServicioDependienteNoDisponibleException("no disponible") }
        thrown(ServicioDependienteNoDisponibleException)
        0 * pendientes.registrarRespuestaOtp(*_)
        0 * firma._
    }

    def "sin pendiente que espere el código (o de otra empresa): 409 y no llama a security"() {
        given:
        pendientes.findByClave(*_) >> encontrado

        when:
        servicio.confirmarOtp(request())

        then:
        def e = thrown(ConflictoException)
        e.codigo == "OTP_SIN_SOLICITUD_PENDIENTE"
        0 * security._
        0 * firma._

        where:
        encontrado << [Optional.empty(), Optional.of(pendiente("OTP_VALIDADO")), Optional.of(pendiente("FIRMADO")),
                       Optional.of(pendiente("PENDIENTE_OTP", "OTRA-EMP"))]
    }
}
