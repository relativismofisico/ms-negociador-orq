package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.AprobacionRequestDto;
import com.negociadororq.application.service.notificacion.CuerpoNotificacionFactory;
import com.negociadororq.domain.event.DestinatarioEmail;
import com.negociadororq.domain.event.NotificacionEmailEvent;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.enums.TipoNotificacion;
import com.negociadororq.infrastructure.persistence.repository.NegociacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificacionServiceImpl implements NotificacionService {

    private final OutboxService outboxService;
    private final CuerpoNotificacionFactory cuerpoFactory;
    private final NegociacionRepository negociacionRepository;

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
                    .map(FacturaNegociada::getRutProveedor)
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

    @Override
    public void notificarEndoso(Endoso endoso) {
        String tipo = endoso.getTipoOperacion().getTipo();
        List<DestinatarioEmail> destinatarios = new ArrayList<>();

        // =========================
        // 🔥 DESTINATARIOS
        // =========================
        if ("FACTORING".equalsIgnoreCase(tipo)) {

            // PAGADOR
            if (endoso.getRutPagador() != null) {
                destinatarios.add(new DestinatarioEmail(
                        "PAGADOR",
                        endoso.getRutPagador(),
                        "EMPRESA"
                ));
            }

            // FONDEADOR
            if (endoso.getRutFondeador() != null) {
                destinatarios.add(new DestinatarioEmail(
                        "FONDEADOR",
                        endoso.getRutFondeador(),
                        "EMPRESA"
                ));
            }

        } else if ("CONFIRMING".equalsIgnoreCase(tipo)) {

            // PROVEEDOR
            if (endoso.getRutProveedor() != null) {
                destinatarios.add(new DestinatarioEmail(
                        "PROVEEDOR",
                        endoso.getRutProveedor(),
                        "EMPRESA"
                ));
            }
        }

        // 🔥 DATA PARA TEMPLATE
        // =========================

        Map<String, Object> data = Map.of(
                "idEndoso", endoso.getId(),
                "numeroFactura", endoso.getNumeroFactura(),
                "tipoOperacion", endoso.getTipoOperacion().getTipo(),
                "rutProveedor", endoso.getRutProveedor(),
                "rutPagador", endoso.getRutPagador(),
                "rutFondeador", endoso.getRutFondeador(),
                "estado", endoso.getEstado().name(),
                "documento", endoso.getDocumentoPdf()
        );

        // =========================
        // 🔥 EVENTO
        // =========================

        NotificacionEmailEvent event = NotificacionEmailEvent.builder()
                .idNegociacion(endoso.getNegociacionId())
                .destinatarios(destinatarios)
                .asunto("ENDOSO") //NOmbre del template
                .data(data)
                .tipoNotificacion(TipoNotificacion.ENDOSO_CREADO.name())
                .fecha(LocalDateTime.now())
                .build();

        outboxService.guardarEvento(
                endoso.getNegociacionId(),
                topicNotificacionEmail,
                event
        );

    }

    @Override
    public void notificarFirmaEndoso(AprobacionRequestDto aprobacion) {

        Negociacion negociacion = negociacionRepository
                .findById(aprobacion.getIdNegociacion())
                .orElseThrow(() -> new RuntimeException("Negociación no encontrada"));

        String tipo = negociacion.getTipoOperacion().getTipo();
        String rutFirmante = aprobacion.getRutEmpresa();

        List<DestinatarioEmail> destinatarios = new ArrayList<>();

        // =========================
        // 🔥 LOGICA DE DESTINATARIOS
        // =========================

        if ("CONFIRMING".equalsIgnoreCase(tipo)) {

            // Firma PROVEEDOR → notificar PAGADOR
            if (negociacion.getRutPagador() != null) {
                destinatarios.add(new DestinatarioEmail(
                        "PAGADOR",
                        negociacion.getRutPagador(),
                        "EMPRESA"
                ));
            }

        } else if ("FACTORING".equalsIgnoreCase(tipo)) {

            boolean esPagador = rutFirmante.equals(negociacion.getRutPagador());

            boolean esFondeador = negociacion.getRutFondeadores() != null &&
                    negociacion.getRutFondeadores().contains(rutFirmante);

            // 🔹 SI FIRMA PAGADOR
            if (esPagador) {

                // NOTIFICAR FONDEADORES
                if (negociacion.getRutFondeadores() != null) {
                    negociacion.getRutFondeadores().forEach(rut ->
                            destinatarios.add(new DestinatarioEmail("FONDEADOR", rut, "EMPRESA"))
                    );
                }

                // NOTIFICAR PROVEEDOR
                negociacion.getFacturas().stream()
                        .map(f -> f.getRutProveedor())
                        .distinct()
                        .forEach(rut ->
                                destinatarios.add(new DestinatarioEmail("PROVEEDOR", rut, "EMPRESA"))
                        );
            }

            // 🔹 SI FIRMA FONDEADOR
            else if (esFondeador) {

                // NOTIFICAR PAGADOR
                if (negociacion.getRutPagador() != null) {
                    destinatarios.add(new DestinatarioEmail(
                            "PAGADOR",
                            negociacion.getRutPagador(),
                            "EMPRESA"
                    ));
                }

                // NOTIFICAR PROVEEDOR
                negociacion.getFacturas().stream()
                        .map(f -> f.getRutProveedor())
                        .distinct()
                        .forEach(rut ->
                                destinatarios.add(new DestinatarioEmail("PROVEEDOR", rut, "EMPRESA"))
                        );
            }
        }

        // =========================
        // 🔥 DATA PARA TEMPLATE
        // =========================

        Map<String, Object> data = Map.of(
                "idNegociacion", negociacion.getId(),
                "firmante", rutFirmante,
                "tipoOperacion", tipo,
                "estado", "FIRMADO",
                "observacion", aprobacion.getObservacion() != null ? aprobacion.getObservacion() : "N/A",
                "cantidadFacturas", aprobacion.getFacturas().size()
        );

        // =========================
        // 🔥 EVENTO
        // =========================

        NotificacionEmailEvent event = NotificacionEmailEvent.builder()
                .idNegociacion(negociacion.getId())
                .destinatarios(destinatarios)
                .asunto("FIRMA_ENDOSO")
                .data(data)
                .tipoNotificacion(TipoNotificacion.ENDOSO_FIRMADO.name())
                .fecha(LocalDateTime.now())
                .build();

        outboxService.guardarEvento(
                negociacion.getId(),
                topicNotificacionEmail,
                event
        );
    }

    @Override
    public void notificarLiquidacionFallida(Negociacion negociacion, String motivo) {

        List<DestinatarioEmail> destinatarios = new ArrayList<>();

        String tipo = negociacion.getTipoOperacion().getTipo();

        // ==========================================
        // DESTINATARIOS
        // ==========================================

        if ("FACTORING".equalsIgnoreCase(tipo)) {

            // PAGADOR
            if (negociacion.getRutPagador() != null) {

                destinatarios.add(
                        new DestinatarioEmail(
                                "PAGADOR",
                                negociacion.getRutPagador(),
                                "EMPRESA"
                        )
                );
            }

            // PROVEEDORES
            negociacion.getFacturas().stream()
                    .map(FacturaNegociada::getRutProveedor)
                    .distinct()
                    .forEach(rut ->
                            destinatarios.add(
                                    new DestinatarioEmail(
                                            "PROVEEDOR",
                                            rut,
                                            "EMPRESA"
                                    )
                            )
                    );

            // FONDEADORES
            if (negociacion.getRutFondeadores() != null) {

                negociacion.getRutFondeadores()
                        .forEach(rut ->
                                destinatarios.add(
                                        new DestinatarioEmail(
                                                "FONDEADOR",
                                                rut,
                                                "EMPRESA"
                                        )
                                )
                        );
            }

        } else if ("CONFIRMING".equalsIgnoreCase(tipo)) {

            // PROVEEDORES
            negociacion.getFacturas().stream()
                    .map(FacturaNegociada::getRutProveedor)
                    .distinct()
                    .forEach(rut ->
                            destinatarios.add(
                                    new DestinatarioEmail(
                                            "PROVEEDOR",
                                            rut,
                                            "EMPRESA"
                                    )
                            )
                    );

            // PAGADOR
            if (negociacion.getRutPagador() != null) {

                destinatarios.add(
                        new DestinatarioEmail(
                                "PAGADOR",
                                negociacion.getRutPagador(),
                                "EMPRESA"
                        )
                );
            }
        }

        // ==========================================
        // DATA TEMPLATE
        // ==========================================

        Map<String, Object> data = Map.of(
                "idNegociacion", negociacion.getId(),
                "tipoOperacion", negociacion.getTipoOperacion().getTipo(),
                "motivo", motivo,
                "totalNegociado", negociacion.getTotalNegociado(),
                "cantidadFacturas", negociacion.getFacturas().size(),
                "fecha", LocalDateTime.now().toString()
        );

        // ==========================================
        // EVENTO
        // ==========================================

        NotificacionEmailEvent event = NotificacionEmailEvent.builder()
                .idEvento(UUID.randomUUID().toString())
                .idNegociacion(negociacion.getId())
                .tipoNotificacion(
                        TipoNotificacion.LIQUIDACION_FALLIDA.name()
                )
                .destinatarios(destinatarios)
                .asunto("LIQUIDACION_FALLIDA")
                .data(data)
                .fecha(LocalDateTime.now())
                .build();

        outboxService.guardarEvento(
                negociacion.getId(),
                topicNotificacionEmail,
                event
        );
    }


}
