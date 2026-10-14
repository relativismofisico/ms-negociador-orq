package com.negociadororq.infrastructure.auditoria;

import com.factoring.commons.auditoria.AuditoriaAccionEvent;
import com.negociadororq.application.port.output.AuditoriaOutputPort;
import com.negociadororq.application.port.output.OutboxOutputPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@link AuditoriaOutputPort} sobre el outbox existente: el evento se guarda en la misma transacción que el cambio de
 * negocio, con el topic de auditoría como tipo, y el {@code OutboxScheduler} lo publica con
 * {@code AuditoriaPublisher.publicarConfirmado} (si Kafka falla, queda pendiente y se reintenta).
 */
@Component
public class AuditoriaOutboxAdapter implements AuditoriaOutputPort {

    private final OutboxOutputPort outboxPort;
    private final String topicAuditoria;

    /**
     * @param outboxPort     outbox del servicio
     * @param topicAuditoria {@code auditoria.topic}
     */
    public AuditoriaOutboxAdapter(final OutboxOutputPort outboxPort,
            @Value("${auditoria.topic:auditoria.accion}") final String topicAuditoria) {
        this.outboxPort = outboxPort;
        this.topicAuditoria = topicAuditoria;
    }

    @Override
    public void registrar(final AuditoriaAccionEvent evento) {
        outboxPort.guardarEvento(evento.claveKafka(), topicAuditoria, evento);
    }
}
