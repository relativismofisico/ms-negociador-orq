package com.negociadororq.application.service

import com.negociadororq.application.exception.ConflictoException
import com.negociadororq.application.exception.RecursoNoEncontradoException
import com.negociadororq.application.port.output.AlertaFirmaOutputPort
import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.PendingOtpOutputPort
import com.negociadororq.domain.event.OtpValidatedEvent
import com.negociadororq.domain.model.PendingOtp
import spock.lang.Specification

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Máquina de estados de la firma por OTP (Fase 28c). */
class FirmaOtpServiceImplSpec extends Specification {

    static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 13, 10, 0)

    PendingOtpOutputPort pendientes = Mock()
    AprobacionInternaService aprobacion = Mock()
    NotificacionService notificaciones = Mock()
    AlertaFirmaOutputPort alerta = Mock()
    ClockPort clock = Stub() { now() >> AHORA }
    FirmaOtpServiceImpl servicio = new FirmaOtpServiceImpl(pendientes, aprobacion, notificaciones, alerta, clock, 3,
            Duration.ofMinutes(2))

    static PendingOtp pendiente(String estado = "PENDIENTE_OTP") {
        PendingOtp.builder().id("p-1").idNegociacion("NEG-1").rutEmpresa("EMP").estado(estado).build()
    }

    static OtpValidatedEvent valido(String otpId = "77") {
        OtpValidatedEvent.builder().idNegociacion("NEG-1").rutEmpresa("EMP").valido(true).otp("123456")
                .ipAprobacion("10.0.0.1").otpId(otpId).fechaValidacion(AHORA.minusSeconds(5)).build()
    }

    static OtpValidatedEvent rechazado(String motivo, Integer restantes) {
        OtpValidatedEvent.builder().idNegociacion("NEG-1").rutEmpresa("EMP").valido(false).motivo(motivo)
                .intentosRestantes(restantes).build()
    }

    def "valido=true: pasa a OTP_VALIDADO con código, referencia, fecha e IP ANTES de firmar; luego FIRMADO sin código"() {
        given:
        def p = pendiente()
        pendientes.findByClave("NEG-1", _, _) >> Optional.of(p)
        def estados = []
        pendientes.save(_) >> { PendingOtp x -> estados << [x.estado, x.otp]; x }

        when:
        servicio.procesarValidacion(valido())

        then:
        1 * aprobacion.aprobarInterno({ PendingOtp x ->
            x.estado == "OTP_VALIDADO" && x.otp == "123456" && x.otpId == "77" && x.ip == "10.0.0.1" &&
                    x.fechaValidacion == AHORA.minusSeconds(5).toInstant(ZoneOffset.UTC)
        })
        estados == [["OTP_VALIDADO", "123456"], ["FIRMADO", null]]
        0 * alerta.firmaFallida()
    }

    def "código incorrecto con intentos restantes: la solicitud sigue pendiente (no se borra)"() {
        given:
        pendientes.findByClave("NEG-1", _, _) >> Optional.of(pendiente())

        when:
        servicio.procesarValidacion(rechazado("INCORRECTO", 2))

        then:
        0 * pendientes.deleteById(_)
        0 * aprobacion.aprobarInterno(_)
    }

    def "rechazo terminal (#motivo, restantes #restantes) descarta la solicitud, como antes"() {
        given:
        pendientes.findByClave("NEG-1", _, _) >> Optional.of(pendiente())

        when:
        servicio.procesarValidacion(rechazado(motivo, restantes))

        then:
        1 * pendientes.deleteById("p-1")

        where:
        motivo       | restantes
        "INCORRECTO" | 0
        "BLOQUEADO"  | 0
        "EXPIRADO"   | null
        "USADO"      | null
        null         | null
    }

    def "duplicado: sin solicitud activa (ya FIRMADA) se ignora sin firmar"() {
        given:
        pendientes.findByClave("NEG-1", _, _) >> Optional.empty()

        when:
        servicio.procesarValidacion(valido())

        then:
        0 * aprobacion.aprobarInterno(_)
        0 * pendientes.save(_)
    }

    def "una validación de otra empresa o con otro otpId mientras hay una firma en curso se ignora"() {
        given:
        pendientes.findByClave("NEG-1", _, _) >> Optional.of(p)

        when:
        servicio.procesarValidacion(evento)

        then:
        0 * aprobacion.aprobarInterno(_)
        0 * pendientes.save(_)

        where:
        p                                                     | evento
        pendiente().tap { rutEmpresa = "OTRA" }               | valido()
        pendiente("OTP_VALIDADO").tap { otpId = "76" }        | valido("77")
    }

    def "fallo permanente (#excepcion.class.simpleName): FALLO_FIRMA, alerta y aviso a la empresa"() {
        given:
        pendientes.findByClave("NEG-1", _, _) >> Optional.of(pendiente())
        PendingOtp guardada = null
        pendientes.save(_) >> { PendingOtp x -> guardada = x; x }
        aprobacion.aprobarInterno(_) >> { throw excepcion }

        when:
        servicio.procesarValidacion(valido())

        then:
        guardada.estado == "FALLO_FIRMA"
        guardada.ultimoError.contains(excepcion.class.simpleName)
        1 * alerta.firmaFallida()
        1 * notificaciones.notificarFirmaFallida({ it.estado == "FALLO_FIRMA" })

        where:
        excepcion << [new RecursoNoEncontradoException("Endoso no encontrado"),
                      new ConflictoException("otro", "OTRO_CONFLICTO")]
    }

    def "ENDOSO_DUPLICADO (el actor ya firmó con otro OTP): se descarta con [ENDOSO-DUPLICADO] y contador, sin alerta de firma"() {
        given:
        pendientes.findByClave("NEG-1", _, _) >> Optional.of(pendiente())
        pendientes.save(_) >> { PendingOtp x -> x }
        aprobacion.aprobarInterno(_) >> { throw new ConflictoException("dup", "ENDOSO_DUPLICADO") }

        when:
        servicio.procesarValidacion(valido())

        then:
        1 * pendientes.deleteById("p-1")
        1 * alerta.endosoDuplicado("NEG-1")
        0 * alerta.firmaFallida()
        0 * notificaciones.notificarFirmaFallida(_)
    }

    def "fallo transitorio: queda OTP_VALIDADO con el intento contado; el reconciliador reintenta y al tercero FALLO_FIRMA"() {
        given:
        def p = pendiente()
        pendientes.findByClave("NEG-1", _, _) >> Optional.of(p)
        pendientes.save(_) >> { PendingOtp x -> x }
        pendientes.findByEstadoAndFechaActualizacionBefore("OTP_VALIDADO", AHORA.minusMinutes(2)) >> [p]
        aprobacion.aprobarInterno(_) >> { throw new IllegalStateException("mongo caído") }

        when:
        servicio.procesarValidacion(valido())

        then:
        p.estado == "OTP_VALIDADO"
        p.intentosFirma == 1
        0 * alerta.firmaFallida()

        when:
        servicio.reintentarPendientes()

        then:
        p.estado == "OTP_VALIDADO"
        p.intentosFirma == 2

        when:
        servicio.reintentarPendientes()

        then:
        p.estado == "FALLO_FIRMA"
        p.intentosFirma == 3
        1 * alerta.firmaFallida()
        1 * notificaciones.notificarFirmaFallida(p)
    }

    def "reconciliador: una firma que quedó en OTP_VALIDADO tras una caída se completa"() {
        given:
        def p = pendiente("OTP_VALIDADO").tap { otpId = "77"; otp = "123456" }
        pendientes.findByEstadoAndFechaActualizacionBefore("OTP_VALIDADO", AHORA.minusMinutes(2)) >> [p]
        pendientes.save(_) >> { PendingOtp x -> x }

        when:
        def n = servicio.reintentarPendientes()

        then:
        n == 1
        1 * aprobacion.aprobarInterno(p)
        p.estado == "FIRMADO"
        p.otp == null
    }

    def "si el aviso a la empresa falla, la firma igual queda en FALLO_FIRMA con su alerta"() {
        given:
        pendientes.findByClave("NEG-1", _, _) >> Optional.of(pendiente())
        pendientes.save(_) >> { PendingOtp x -> x }
        aprobacion.aprobarInterno(_) >> { throw new RecursoNoEncontradoException("x") }
        notificaciones.notificarFirmaFallida(_) >> { throw new IllegalStateException("outbox caído") }

        when:
        servicio.procesarValidacion(valido())

        then:
        notThrown(Exception)
        1 * alerta.firmaFallida()
    }

    def "Fase 28c: instanteValidacion (con zona) manda; el formato anterior sin zona se toma como UTC (hora de security)"() {
        expect:
        FirmaOtpServiceImpl.instanteValidacion(OtpValidatedEvent.builder()
                .instanteValidacion(Instant.parse("2026-11-13T15:00:00Z"))
                .fechaValidacion(LocalDateTime.of(2000, 1, 1, 0, 0)).build()) == Instant.parse("2026-11-13T15:00:00Z")
        FirmaOtpServiceImpl.instanteValidacion(OtpValidatedEvent.builder()
                .fechaValidacion(LocalDateTime.of(2026, 11, 13, 15, 0)).build()) == Instant.parse("2026-11-13T15:00:00Z")
        FirmaOtpServiceImpl.instanteValidacion(OtpValidatedEvent.builder().build()) == null
    }

    def "Fase 28c: el evento nuevo de security (instanteValidacion en ISO con Z) se deserializa y llega a la solicitud"() {
        given:
        def json = '{"idNegociacion":"NEG-1","rutEmpresa":"EMP","valido":true,"otpId":"77",' +
                '"fechaValidacion":"2026-11-13T15:00:00","instanteValidacion":"2026-11-13T15:00:00Z","campoNuevo":1}'
        def mapper = org.springframework.kafka.support.JacksonUtils.enhancedObjectMapper()
        def evento = mapper.readValue(json, OtpValidatedEvent)
        def p = pendiente()
        pendientes.findByClave("NEG-1", _, _) >> Optional.of(p)
        pendientes.save(_) >> { PendingOtp x -> x }

        when:
        servicio.procesarValidacion(evento)

        then:
        1 * aprobacion.aprobarInterno({ PendingOtp x -> x.fechaValidacion == Instant.parse("2026-11-13T15:00:00Z") })
    }
}
