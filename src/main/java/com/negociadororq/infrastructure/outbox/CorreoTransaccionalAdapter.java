package com.negociadororq.infrastructure.outbox;

import com.factoring.commons.outbox.OutboxPort;
import com.negociadororq.application.port.output.CorreoTransaccionalOutputPort;
import com.negociadororq.domain.event.NotificacionEmailEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * {@link CorreoTransaccionalOutputPort} sobre el {@link OutboxPort} JDBC de ms-commons-util (tipo
 * {@link OutboxNegociadorConfig#TIPO_NOTIFICACION_EMAIL}). Participa en la transacción MySQL activa.
 */
@Component
public class CorreoTransaccionalAdapter implements CorreoTransaccionalOutputPort {

    private final ObjectProvider<OutboxPort> outbox;

    /**
     * @param outbox outbox JDBC de commons (existe con {@code auditoria.outbox.enabled=true})
     */
    public CorreoTransaccionalAdapter(final ObjectProvider<OutboxPort> outbox) {
        this.outbox = outbox;
    }

    @Override
    public boolean registrar(final String eventId, final NotificacionEmailEvent evento) {
        final OutboxPort port = outbox.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException("el outbox JDBC de commons está apagado (AUDITORIA_OUTBOX_ENABLED=false): "
                    + "los correos de la reversión no se pueden registrar con su cambio");
        }
        return port.registrar(OutboxNegociadorConfig.TIPO_NOTIFICACION_EMAIL, eventId, evento).isPresent();
    }
}
