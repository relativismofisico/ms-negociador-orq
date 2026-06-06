package com.negociadororq.application.service;

import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.service.notificacion.CuerpoNotificacionFactory;
import com.negociadororq.application.service.notificacion.DestinatarioResolver;
import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.domain.event.DestinatarioEmail;
import com.negociadororq.domain.model.AprobacionRequest;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.Operacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificacionServiceImpl — envío de notificaciones de dominio")
class NotificacionServiceImplTest {

    @Mock
    private OutboxService outboxService;

    @Mock
    private CuerpoNotificacionFactory cuerpoFactory;

    @Mock
    private NegociacionOutputPort negociacionPort;

    @Mock
    private DestinatarioResolver destinatarioResolver;

    @InjectMocks
    private NotificacionServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "topicNotificacionEmail", "notificacion.email");
    }

    private Negociacion buildNegociacion(String tipo) {
        return Negociacion.builder()
                .id("NEG-001")
                .tipoOperacion(Operacion.builder().tipo(tipo).clase("NORMAL").build())
                .rutPagador("900111222")
                .rutProveedor("800333444")
                .rutFondeadores(List.of("700555666"))
                .totalNegociado(new BigDecimal("10000"))
                .facturas(List.of(FacturaNegociada.builder()
                        .numeroFactura("FAC-001")
                        .rutProveedor("800333444")
                        .build()))
                .build();
    }

    // ─── notificarCreacionNegociacion ─────────────────────────────

    @Test
    @DisplayName("notificarCreacionNegociacion guarda evento en outbox")
    void notificarCreacionNegociacion_guardaEvento() {
        Negociacion neg = buildNegociacion("FACTORING");
        when(destinatarioResolver.resolverParaCreacionNegociacion(neg))
                .thenReturn(List.of(new DestinatarioEmail("PAGADOR", "900111222", "EMPRESA")));

        service.notificarCreacionNegociacion(neg);

        verify(outboxService).guardarEvento(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("notificarCreacionNegociacion maneja negociacion sin facturas")
    void notificarCreacionNegociacion_sinFacturas() {
        Negociacion neg = Negociacion.builder()
                .id("NEG-002")
                .tipoOperacion(Operacion.builder().tipo("CONFIRMING").clase("NORMAL").build())
                .facturas(null)
                .build();
        when(destinatarioResolver.resolverParaCreacionNegociacion(neg))
                .thenReturn(List.of());

        service.notificarCreacionNegociacion(neg);

        verify(outboxService).guardarEvento(anyString(), anyString(), any());
    }

    // ─── notificarEndoso ──────────────────────────────────────────

    @Test
    @DisplayName("notificarEndoso guarda evento en outbox con datos del endoso")
    void notificarEndoso_guardaEvento() {
        Endoso endoso = Endoso.builder()
                .id("end-001")
                .negociacionId("NEG-001")
                .numeroFactura("FAC-001")
                .tipoOperacion(Operacion.builder().tipo("FACTORING").clase("NORMAL").build())
                .rutProveedor("800111222")
                .rutPagador("900333444")
                .rutFondeador("700555666")
                .estado(EstadoEndoso.EN_REVISION)
                .documentoPdf(new byte[]{1, 2})
                .build();

        when(destinatarioResolver.resolverParaEndoso(endoso))
                .thenReturn(List.of(new DestinatarioEmail("PAGADOR", "900333444", "EMPRESA")));

        service.notificarEndoso(endoso);

        verify(outboxService).guardarEvento(anyString(), anyString(), any());
    }

    // ─── notificarFirmaEndoso ─────────────────────────────────────

    @Test
    @DisplayName("notificarFirmaEndoso guarda evento cuando negociacion existe")
    void notificarFirmaEndoso_guardaEvento() {
        Negociacion neg = buildNegociacion("FACTORING");
        AprobacionRequest req = new AprobacionRequest();
        req.setIdNegociacion("NEG-001");
        req.setRutEmpresa("800333444");

        when(negociacionPort.findById("NEG-001")).thenReturn(Optional.of(neg));
        when(destinatarioResolver.resolverParaFirmaEndoso(any(), anyString()))
                .thenReturn(List.of());

        service.notificarFirmaEndoso(req);

        verify(outboxService).guardarEvento(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("notificarFirmaEndoso lanza RuntimeException cuando negociacion no existe")
    void notificarFirmaEndoso_negociacionNoExiste() {
        AprobacionRequest req = new AprobacionRequest();
        req.setIdNegociacion("NEG-X");
        req.setRutEmpresa("800333444");

        when(negociacionPort.findById("NEG-X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.notificarFirmaEndoso(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Negociación no encontrada");
    }

    // ─── notificarLiquidacionFallida ──────────────────────────────

    @Test
    @DisplayName("notificarLiquidacionFallida guarda evento con idEvento UUID")
    void notificarLiquidacionFallida_guardaEventoConUuid() {
        Negociacion neg = buildNegociacion("CONFIRMING");
        when(destinatarioResolver.resolverParaLiquidacionFallida(neg))
                .thenReturn(List.of());

        service.notificarLiquidacionFallida(neg, "sin fondos");

        verify(outboxService).guardarEvento(anyString(), anyString(), any());
    }

    // ─── safeMap (indirectamente via notificaciones) ──────────────

    @Test
    @DisplayName("notificarCreacionNegociacion con tipoOperacion null no lanza NPE")
    void notificarCreacion_tipoOperacionNull() {
        Negociacion neg = Negociacion.builder()
                .id("NEG-003")
                .tipoOperacion(null)
                .facturas(List.of())
                .build();
        when(destinatarioResolver.resolverParaCreacionNegociacion(neg))
                .thenReturn(List.of());

        service.notificarCreacionNegociacion(neg);

        verify(outboxService).guardarEvento(anyString(), anyString(), any());
    }
}