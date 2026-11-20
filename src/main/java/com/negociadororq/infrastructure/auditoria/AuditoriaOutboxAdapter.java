package com.negociadororq.infrastructure.auditoria;

import com.factoring.commons.auditoria.AuditoriaAccionEvent;
import com.factoring.commons.auditoria.AuditoriaPort;
import com.negociadororq.application.port.output.AuditoriaOutputPort;
import com.negociadororq.application.port.output.OutboxOutputPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@link AuditoriaOutputPort} del negociador.
 *
 * <p>Fase 28d (A2): toda la auditoría sale por el outbox JDBC de ms-commons-util ({@code auditoria.outbox.*}, tabla
 * {@code outbox_negociador}): mismo topic ({@code auditoria.topic}), mismo JSON ({@code AuditoriaJson}), misma clave
 * ({@code claveKafka()}) y el mismo publicador ({@code KafkaAuditoriaPublisher}) que el envío anterior. El registro
 * participa en la transacción JPA activa, si la hay: es atómico con los cambios MySQL de esa transacción (por ejemplo el
 * descuento o la devolución de cupos). Fuera de una transacción MySQL (flujos que solo escriben en Mongo) se confirma
 * solo, igual que antes respecto de la escritura doble.</p>
 *
 * <p>Con {@code AUDITORIA_OUTBOX_ENABLED=false} (vuelta atrás planificada) commons no crea su {@link AuditoriaPort} y
 * la auditoría vuelve al outbox Mongo viejo, como antes de la A2.</p>
 */
@Component
public class AuditoriaOutboxAdapter implements AuditoriaOutputPort {

    private final ObjectProvider<AuditoriaPort> outboxCommons;
    private final OutboxOutputPort outboxViejo;
    private final String topicAuditoria;

    /**
     * @param outboxCommons  {@code AuditoriaPort} del outbox de commons (si está activo)
     * @param outboxViejo    outbox Mongo viejo (solo con el de commons apagado)
     * @param topicAuditoria {@code auditoria.topic}
     */
    public AuditoriaOutboxAdapter(final ObjectProvider<AuditoriaPort> outboxCommons, final OutboxOutputPort outboxViejo,
            @Value("${auditoria.topic:auditoria.accion}") final String topicAuditoria) {
        this.outboxCommons = outboxCommons;
        this.outboxViejo = outboxViejo;
        this.topicAuditoria = topicAuditoria;
    }

    @Override
    public void registrar(final AuditoriaAccionEvent evento) {
        final AuditoriaPort commons = outboxCommons.getIfAvailable();
        if (commons != null) {
            commons.registrar(evento);
            return;
        }
        outboxViejo.guardarEvento(evento.claveKafka(), topicAuditoria, evento);
    }
}
