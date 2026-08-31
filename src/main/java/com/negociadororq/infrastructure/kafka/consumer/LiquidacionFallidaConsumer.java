package com.negociadororq.infrastructure.kafka.consumer;

import com.negociadororq.application.service.ReversionCuposService;
import com.negociadororq.domain.event.LiquidacionFallidaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume el evento de liquidación fallida publicado por ms-liquidador-neg y revierte los cupos
 * consumidos por la factura afectada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiquidacionFallidaConsumer {

    private final ReversionCuposService reversionCuposService;

    @KafkaListener(
            topics = "${topics.liquidacion-fallida}",
            groupId = "negociador-group",
            containerFactory = "liquidacionKafkaListenerContainerFactory"
    )
    public void consumir(LiquidacionFallidaEvent event) {

         log.warn(
                "⚠ Evento liquidacion fallida recibido factura={} motivo={}",
                event.getNumeroFactura(),
                event.getMotivo()
        );

        try {

            reversionCuposService.revertirPorFactura(
                    event.getNumeroFactura(), event.getRutOperador(),
                    event.getMotivo()
            );

            log.info(
                    "✅ Reversión ejecutada correctamente factura={}",
                    event.getNumeroFactura()
            );

        } catch (Exception ex) {

            log.error(
                    "❌ Error ejecutando reversión factura={}",
                    event.getNumeroFactura(),
                    ex
            );

            throw new IllegalStateException(
                    "Error ejecutando reversión de cupos para factura="
                            + event.getNumeroFactura(),
                    ex
            );
        }
    }

}

