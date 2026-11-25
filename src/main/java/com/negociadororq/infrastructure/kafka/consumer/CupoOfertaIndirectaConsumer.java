package com.negociadororq.infrastructure.kafka.consumer;

import com.factoring.commons.outbox.idempotencia.ConsumoIdempotente;
import com.negociadororq.application.service.PropagacionCupoOfertaService;
import com.negociadororq.domain.event.CupoOfertaIndirectaCambiadaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Fase 28d (P-19, decisión de John J-09): consume el cambio del cupo por proveedor de una oferta indirecta (outbox de
 * ms-registroempresa-neg) y ajusta a los proveedores que ya negociaron, una sola vez por evento: la marca de
 * ConsumoIdempotente (tabla evento_procesado) se escribe en la misma transacción que el ajuste. La clave es natural
 * (oferta y versión), así que también cubre el envío directo sin outbox. Si falla, el error handler reintenta y deja el
 * evento en el DLT; la versión de cada fila evita aplicar dos veces el mismo cambio o uno viejo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CupoOfertaIndirectaConsumer {

    /** Consumidor en evento_procesado. */
    static final String CONSUMIDOR = "negociador.cupo-oferta-indirecta";

    private final PropagacionCupoOfertaService propagacion;
    private final ObjectProvider<ConsumoIdempotente> consumoIdempotente;

    /**
     * @param evento cambio de cupo de la oferta
     */
    @KafkaListener(
            topics = "${topics.cupo-oferta-indirecta:cupoOfertaIndirectaCambiadaTopic}",
            groupId = "negociador-group",
            containerFactory = "cupoOfertaKafkaListenerContainerFactory"
    )
    public void consumir(CupoOfertaIndirectaCambiadaEvent evento) {
        String clave = "cupo-oferta|" + evento.offerId() + "|" + evento.version();
        ConsumoIdempotente idempotente = consumoIdempotente.getIfAvailable();
        if (idempotente == null) {
            // sin el almacén de marcas: la versión de cada fila ya descarta reentregas y eventos viejos
            propagacion.aplicar(evento.offerId(), evento.version(), evento.nuevo());
            return;
        }
        boolean aplicado = idempotente.procesar(CONSUMIDOR, clave,
                () -> propagacion.aplicar(evento.offerId(), evento.version(), evento.nuevo()));
        if (!aplicado) {
            log.info("[CUPO-OFERTA-DUPLICADO] oferta {} versión {}: ya procesado, se ignora", evento.offerId(),
                    evento.version());
        }
    }
}
