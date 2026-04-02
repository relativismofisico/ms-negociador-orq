package com.negociadororq.application.service;

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

@Service
@RequiredArgsConstructor
public class NotificacionServiceImpl implements NotificacionService {

    private final OutboxService outboxService;

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

        String cuerpo = construirCuerpo(negociacion);

        // =========================
        // 🔥 EVENTO
        // =========================

        NotificacionEmailEvent event = NotificacionEmailEvent.builder()
                .idNegociacion(negociacion.getId())
                .destinatarios(destinatarios)
                .asunto(asunto)
                .cuerpo(cuerpo)
                .tipoNotificacion(TipoNotificacion.NEGOCIACION_CREADA.name())
                .fecha(LocalDateTime.now())
                .build();

        outboxService.guardarEvento(
                negociacion.getId(),
                topicNotificacionEmail,
                event
        );
    }

    private String construirCuerpo(Negociacion negociacion) {

        return "Se ha creado una nueva negociación.\n\n" +
                "ID: " + negociacion.getId() + "\n" +
                "Tipo: " + negociacion.getTipoOperacion().getTipo() + "\n" +
                "Total: " + negociacion.getTotalNegociado() + "\n" +
                "Facturas: " + negociacion.getFacturas().size() + "\n\n" +
                "Por favor ingrese a la plataforma para revisar.";
    }
}
