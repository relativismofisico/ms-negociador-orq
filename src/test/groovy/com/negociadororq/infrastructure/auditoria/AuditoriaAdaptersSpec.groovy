package com.negociadororq.infrastructure.auditoria

import com.factoring.commons.auditoria.AuditoriaAccionEvent
import com.factoring.commons.auditoria.AuditoriaActor
import com.factoring.commons.auditoria.AuditoriaNoPublicadaException
import com.factoring.commons.auditoria.AuditoriaPublisher
import com.negociadororq.application.port.output.OutboxOutputPort
import com.negociadororq.infrastructure.kafka.config.TopicsProperties
import com.negociadororq.infrastructure.kafka.producer.OutboxEventPublisher
import org.slf4j.MDC
import org.springframework.beans.factory.ObjectProvider
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import spock.lang.Specification

class AuditoriaAdaptersSpec extends Specification {

    def cleanup() {
        RequestContextHolder.resetRequestAttributes()
        SecurityContextHolder.clearContext()
        MDC.clear()
    }

    private static AuditoriaAccionEvent evento() {
        AuditoriaAccionEvent.builder().accion("NEGOCIACION_CREADA").entidad("NEGOCIACION", "NEG-1")
                .actor(AuditoriaActor.anonimo()).canal("WEB").build()
    }

    def "el adaptador del outbox guarda el evento con su clave por entidad y el topic de auditoría como tipo"() {
        given:
        OutboxOutputPort outbox = Mock()
        def e = evento()

        when:
        new AuditoriaOutboxAdapter(outbox, "auditoria.accion").registrar(e)

        then:
        1 * outbox.guardarEvento("NEGOCIACION:NEG-1", "auditoria.accion", e)
    }

    def "contexto sin request: ANONIMO, sin IP; el requestId sale del MDC"() {
        given:
        MDC.put("requestId", "req-9")
        def contexto = new ContextoAuditoriaAdapter()

        expect:
        contexto.actorActual().tipo() == "ANONIMO"
        contexto.ipActual() == null
        contexto.requestIdActual() == "req-9"
    }

    def "contexto con request y JWT: USUARIO con su rol e IP de X-Forwarded-For"() {
        given:
        def request = new MockHttpServletRequest()
        request.addHeader("X-Forwarded-For", "10.0.0.7")
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))
        SecurityContextHolder.context.authentication = new UsernamePasswordAuthenticationToken(
                "op1", null, [new SimpleGrantedAuthority("OPERARIO")])
        def contexto = new ContextoAuditoriaAdapter()

        expect:
        contexto.actorActual() == AuditoriaActor.usuario("op1", "OPERARIO", null)
        contexto.ipActual() == "10.0.0.7"
    }

    def "el publisher del outbox manda la auditoría confirmada y propaga el fallo para que se reintente"() {
        given:
        AuditoriaPublisher auditoriaPublisher = Mock()
        KafkaTemplate<String, Object> template = Mock()
        def publisher = new OutboxEventPublisher(template, new TopicsProperties(),
                Stub(ObjectProvider) { getIfAvailable() >> auditoriaPublisher })
        def e = evento()

        when:
        publisher.publicar(e)

        then:
        1 * auditoriaPublisher.publicarConfirmado(e) >> { throw new AuditoriaNoPublicadaException(e.eventId(), null) }
        0 * template._
        thrown(AuditoriaNoPublicadaException)
    }

    def "con la auditoría desactivada (sin publicador) el evento se descarta sin error"() {
        given:
        def publisher = new OutboxEventPublisher(Mock(KafkaTemplate), new TopicsProperties(),
                Stub(ObjectProvider) { getIfAvailable() >> null })

        when:
        publisher.publicar(evento())

        then:
        noExceptionThrown()
    }
}
