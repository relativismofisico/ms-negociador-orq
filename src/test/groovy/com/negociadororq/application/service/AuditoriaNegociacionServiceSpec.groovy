package com.negociadororq.application.service

import com.factoring.commons.auditoria.AuditoriaAccionEvent
import com.factoring.commons.auditoria.AuditoriaActor
import com.negociadororq.application.dto.EndosoRequestDto
import com.negociadororq.application.dto.NegociacionRequestDto
import com.negociadororq.application.port.output.AuditoriaOutputPort
import com.negociadororq.application.port.output.ContextoAuditoriaOutputPort
import com.negociadororq.domain.enums.EstadoEndoso
import com.negociadororq.domain.enums.RolActor
import com.negociadororq.domain.model.AprobacionRequest
import com.negociadororq.domain.model.Endoso
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import com.negociadororq.domain.model.PendingOtp
import spock.lang.Specification

/**
 * Acciones auditadas de negociación y endoso: actor, IP y requestId de la request HTTP, o los de la solicitud del OTP
 * guardados en PendingOtp cuando la aprobación llega por el listener (sin request).
 */
class AuditoriaNegociacionServiceSpec extends Specification {

    static final AuditoriaActor OPERARIO = AuditoriaActor.usuario("op1", "OPERARIO", 7L)

    AuditoriaOutputPort auditoria = Mock()
    ContextoAuditoriaOutputPort contexto = Stub() {
        actorActual() >> OPERARIO
        ipActual() >> "10.0.0.7"
        requestIdActual() >> "req-123"
    }
    AuditoriaNegociacionService servicio = new AuditoriaNegociacionService(auditoria, contexto)

    private static PendingOtp solicitudConActor() {
        PendingOtp.builder().idNegociacion("NEG-1").rutEmpresa("900").ip("10.0.0.9")
                .request(new AprobacionRequest("NEG-1", "op-declarado", "900", "PAGADOR", [], null))
                .actorTipo("USUARIO").actorId("op1").actorRol("OPERARIO").actorEmpresaId(7L).requestId("req-123")
                .build()
    }

    def "completarSolicitud guarda en el PendingOtp el actor autenticado y el requestId de la request"() {
        given:
        def pending = PendingOtp.builder().idNegociacion("NEG-1").build()

        when:
        servicio.completarSolicitud(pending)

        then:
        pending.actorTipo == "USUARIO"
        pending.actorId == "op1"
        pending.actorRol == "OPERARIO"
        pending.actorEmpresaId == 7L
        pending.requestId == "req-123"
    }

    def "negociacionCreada: NEGOCIACION_CREADA con el actor del JWT y el operador declarado en el detalle"() {
        given:
        def negociacion = new Negociacion(id: "NEG-1", estado: "PENDIENTE", totalNegociado: 1500G,
                tipoOperacion: Operacion.builder().tipo("FACTORING").clase("SIN_RESPONSABILIDAD").build(),
                facturas: [new FacturaNegociada(), new FacturaNegociada()])
        def request = new NegociacionRequestDto(rutEmpresa: "900", rolEmpresa: "PROVEEDOR", rutOperador: "op-declarado")
        AuditoriaAccionEvent e = null

        when:
        servicio.negociacionCreada(negociacion, request, 1)

        then:
        1 * auditoria.registrar(_) >> { args -> e = args[0] }
        e.accion() == "NEGOCIACION_CREADA"
        e.claveKafka() == "NEGOCIACION:NEG-1"
        e.actor() == OPERARIO
        e.ip() == "10.0.0.7"
        e.estadoAnterior() == null
        e.estadoNuevo() == "PENDIENTE"
        e.detalle() == [rutEmpresa: "900", rolEmpresa: "PROVEEDOR", rutOperadorDeclarado: "op-declarado",
                        tipoOperacion: "FACTORING", totalNegociado: 1500G, facturasNegociadas: 2, facturasRechazadas: 1]
    }

    def "negociacionCreada sin tipo de operación ni facturas no falla"() {
        when:
        servicio.negociacionCreada(new Negociacion(id: "NEG-2", estado: "RECHAZADA"), new NegociacionRequestDto(), 3)

        then:
        1 * auditoria.registrar({ it.detalle().facturasNegociadas == 0 && !it.detalle().containsKey("tipoOperacion") })
    }

    def "endosoRegistrado: ENDOSO_REGISTRADO por endoso"() {
        given:
        def endoso = Endoso.builder().id("END-1").negociacionId("NEG-1").numerosFactura(["F1", "F2"])
                .rutFondeador("800").estado(EstadoEndoso.EN_REVISION).build()

        when:
        servicio.endosoRegistrado(endoso, new EndosoRequestDto(rutActor: "900", rutOperador: "op-declarado"))

        then:
        1 * auditoria.registrar({ AuditoriaAccionEvent e ->
            e.accion() == "ENDOSO_REGISTRADO" && e.claveKafka() == "ENDOSO:END-1" && e.estadoNuevo() == "EN_REVISION" &&
                    e.detalle().numerosFactura == ["F1", "F2"] && e.detalle().rutActorEndosa == "900"
        })
    }

    def "facturaRevisada: APROBADA o RECHAZADA con el actor y el requestId de la solicitud del OTP y la IP del pending"() {
        when:
        servicio.facturaRevisada(solicitudConActor(), "NEG-1", "F1", RolActor.PAGADOR, aprobado)

        then:
        1 * auditoria.registrar({ AuditoriaAccionEvent e ->
            e.accion() == accion && e.actor() == OPERARIO.conEmpresaNit("900") && e.requestId() == "req-123" &&
                    e.ip() == "10.0.0.9" && e.detalle() == [numeroFactura: "F1", rolActor: "PAGADOR", rutEmpresa: "900"]
        })

        where:
        aprobado || accion
        true     || "NEGOCIACION_FACTURA_APROBADA"
        false    || "NEGOCIACION_FACTURA_RECHAZADA"
    }

    def "estadoNegociacion: audita solo si el estado global cambió"() {
        when:
        servicio.estadoNegociacion(solicitudConActor(), "NEG-1", anterior, nuevo)

        then:
        veces * auditoria.registrar({ it.accion() == "NEGOCIACION_ESTADO_CAMBIADO" && it.estadoAnterior() == anterior })

        where:
        anterior       | nuevo          || veces
        "PENDIENTE"    | "EN_APROBACION" || 1
        "EN_APROBACION" | "EN_APROBACION" || 0
    }

    def "endosoFirmado: ENDOSO_FIRMADO con el resultado y el rol"() {
        given:
        def endoso = Endoso.builder().id("END-1").negociacionId("NEG-1").build()

        when:
        servicio.endosoFirmado(solicitudConActor(), endoso, "F1", RolActor.FONDEADOR, false)

        then:
        1 * auditoria.registrar({ AuditoriaAccionEvent e ->
            e.accion() == "ENDOSO_FIRMADO" && e.claveKafka() == "ENDOSO:END-1" &&
                    e.detalle() == [idNegociacion: "NEG-1", numeroFactura: "F1", rolActor: "FONDEADOR", aprobado: false]
        })
    }

    def "una solicitud de OTP anterior a este cambio (sin actor guardado) se audita a nombre del operador declarado"() {
        given:
        def pending = PendingOtp.builder().rutEmpresa("900")
                .request(new AprobacionRequest("NEG-1", "op-declarado", "900", "PAGADOR", [], null)).build()

        expect:
        AuditoriaNegociacionService.actorDe(pending) == AuditoriaActor.usuario("op-declarado", null, null).conEmpresaNit("900")
    }
}
