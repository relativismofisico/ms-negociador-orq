package com.negociadororq.application.service;

import com.negociadororq.application.service.notificacion.CuerpoNotificacionFactory;
import com.negociadororq.domain.event.DestinatarioEmail;
import com.negociadororq.domain.event.NotificacionEmailEvent;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.enums.TipoNotificacion;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificacionServiceImpl implements NotificacionService {

    private final OutboxService outboxService;
    private final CuerpoNotificacionFactory cuerpoFactory;

    @Value("${topics.notificacion-email}")
    private String topicNotificacionEmail;

    @Override
    public void notificarCreacionNegociacion(Negociacion negociacion) {

        List<DestinatarioEmail> destinatarios = new ArrayList<>();

        String tipo = negociacion.getTipoOperacion().getTipo();

        // =========================
        // 🔥 DESTINATARIOS
        // =========================

        if ("FACTORING".equalsIgnoreCase(tipo)) {

            // PAGADOR
            if (negociacion.getRutPagador() != null) {
                destinatarios.add(new DestinatarioEmail(
                        "PAGADOR",
                        negociacion.getRutPagador(),
                        "EMPRESA"
                ));
            }

            // FONDEADORES
            if (negociacion.getRutFondeadores() != null) {
                negociacion.getRutFondeadores().forEach(rut ->
                        destinatarios.add(new DestinatarioEmail("FONDEADOR", rut, "EMPRESA"))
                );
            }

        } else if ("CONFIRMING".equalsIgnoreCase(tipo)) {

            // PROVEEDOR (sale de las facturas)
            negociacion.getFacturas().stream()
                    .map(f -> f.getRutProveedor())
                    .distinct()
                    .forEach(rut ->
                            destinatarios.add(new DestinatarioEmail("PROVEEDOR", rut, "EMPRESA"))
                    );
        }

        // =========================
        // 🔥 ASUNTO Y CUERPO
        // =========================

        String asunto = "NEGOCIACION";

        String cuerpo = cuerpoFactory.construir(negociacion);
        // =========================
        // 🔥 EVENTO
        // =========================

        Map<String, Object> data = Map.of(
                "id", negociacion.getId(),
                "tipoOperacion", negociacion.getTipoOperacion().getTipo(),
                "rutPagador", negociacion.getRutPagador(),
                "rutProveedor", negociacion.getRutProveedor(),
                "rutFondeadores", negociacion.getRutFondeadores(),
                "totalNegociado", negociacion.getTotalNegociado(),
                "facturas", negociacion.getFacturas().size()

        );

        NotificacionEmailEvent event = NotificacionEmailEvent.builder()
                .idNegociacion(negociacion.getId())
                .destinatarios(destinatarios)
                .asunto(asunto)
                .data(data)
                .tipoNotificacion(TipoNotificacion.NEGOCIACION_CREADA.name())
                .fecha(LocalDateTime.now())
                .build();

        outboxService.guardarEvento(
                negociacion.getId(),
                topicNotificacionEmail,
                event
        );
    }

}
