package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.AprobacionRequestDto;
import com.negociadororq.application.service.notificacion.CuerpoNotificacionFactory;
import com.negociadororq.application.service.notificacion.DestinatarioResolver;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificacionServiceImpl implements NotificacionService {

    private final OutboxService outboxService;
    private final CuerpoNotificacionFactory cuerpoFactory;
    private final NegociacionRepository negociacionRepository;
    private final DestinatarioResolver destinatarioResolver;

    @Value("${topics.notificacion-email}")
    private String topicNotificacionEmail;

    @Override
    public void notificarCreacionNegociacion(Negociacion negociacion) {
        List<DestinatarioEmail> destinatarios =
                destinatarioResolver.resolverParaCreacionNegociacion(negociacion);

        String cuerpo = cuerpoFactory.construir(negociacion);

        Map<String, Object> data = safeMap(
                "id", negociacion.getId(),
                "tipoOperacion", negociacion.getTipoOperacion() != null
                        ? negociacion.getTipoOperacion().getTipo() : null,
                "rutPagador", negociacion.getRutPagador(),
                "rutProveedor", negociacion.getRutProveedor(),
                "rutFondeadores", negociacion.getRutFondeadores(),
                "totalNegociado", negociacion.getTotalNegociado(),
                "facturas", negociacion.getFacturas() != null
                        ? negociacion.getFacturas().size() : 0
        );

        NotificacionEmailEvent event = NotificacionEmailEvent.builder()
                .idNegociacion(negociacion.getId())
                .destinatarios(destinatarios)
                .asunto("NEGOCIACION")
                .data(data)
                .tipoNotificacion(TipoNotificacion.NEGOCIACION_CREADA.name())
                .fecha(LocalDateTime.now())
                .build();

        outboxService.guardarEvento(negociacion.getId(), topicNotificacionEmail, event);
    }

    @Override
    public void notificarEndoso(Endoso endoso) {
        List<DestinatarioEmail> destinatarios =
                destinatarioResolver.resolverParaEndoso(endoso);

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

        NotificacionEmailEvent event = NotificacionEmailEvent.builder()
                .idNegociacion(endoso.getNegociacionId())
                .destinatarios(destinatarios)
                .asunto("ENDOSO")
                .data(data)
                .tipoNotificacion(TipoNotificacion.ENDOSO_CREADO.name())
                .fecha(LocalDateTime.now())
                .build();

        outboxService.guardarEvento(endoso.getNegociacionId(), topicNotificacionEmail, event);
    }

    @Override
    public void notificarFirmaEndoso(AprobacionRequestDto aprobacion) {
        Negociacion negociacion = negociacionRepository
                .findById(aprobacion.getIdNegociacion())
                .orElseThrow(() -> new RuntimeException("Negociación no encontrada"));

        List<DestinatarioEmail> destinatarios =
                destinatarioResolver.resolverParaFirmaEndoso(negociacion, aprobacion.getRutEmpresa());

        Map<String, Object> data = safeMap(
                "id", negociacion.getId(),
                "tipoOperacion", negociacion.getTipoOperacion() != null
                        ? negociacion.getTipoOperacion().getTipo() : null,
                "rutPagador", negociacion.getRutPagador(),
                "rutProveedor", negociacion.getRutProveedor(),
                "rutFondeadores", negociacion.getRutFondeadores(),
                "totalNegociado", negociacion.getTotalNegociado(),
                "facturas", negociacion.getFacturas() != null
                        ? negociacion.getFacturas().size() : 0
        );

        NotificacionEmailEvent event = NotificacionEmailEvent.builder()
                .idNegociacion(negociacion.getId())
                .destinatarios(destinatarios)
                .asunto("FIRMA_ENDOSO")
                .data(data)
                .tipoNotificacion(TipoNotificacion.ENDOSO_FIRMADO.name())
                .fecha(LocalDateTime.now())
                .build();

        outboxService.guardarEvento(negociacion.getId(), topicNotificacionEmail, event);
    }

    @Override
    public void notificarLiquidacionFallida(Negociacion negociacion, String motivo) {
        List<DestinatarioEmail> destinatarios =
                destinatarioResolver.resolverParaLiquidacionFallida(negociacion);

        Map<String, Object> data = safeMap(
                "id", negociacion.getId(),
                "tipoOperacion", negociacion.getTipoOperacion() != null
                        ? negociacion.getTipoOperacion().getTipo() : null,
                "rutPagador", negociacion.getRutPagador(),
                "rutProveedor", negociacion.getRutProveedor(),
                "rutFondeadores", negociacion.getRutFondeadores(),
                "totalNegociado", negociacion.getTotalNegociado(),
                "facturas", negociacion.getFacturas() != null
                        ? negociacion.getFacturas().size() : 0
        );

        NotificacionEmailEvent event = NotificacionEmailEvent.builder()
                .idEvento(UUID.randomUUID().toString())
                .idNegociacion(negociacion.getId())
                .tipoNotificacion(TipoNotificacion.LIQUIDACION_FALLIDA.name())
                .destinatarios(destinatarios)
                .asunto("LIQUIDACION_FALLIDA")
                .data(data)
                .fecha(LocalDateTime.now())
                .build();

        outboxService.guardarEvento(negociacion.getId(), topicNotificacionEmail, event);
    }

    private Map<String, Object> safeMap(Object... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("La cantidad de argumentos debe ser par");
        }
        Map<String, Object> map = new java.util.HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }
}