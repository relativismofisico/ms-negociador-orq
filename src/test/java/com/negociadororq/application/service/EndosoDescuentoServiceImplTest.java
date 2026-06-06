package com.negociadororq.application.service;

import com.negociadororq.application.dto.EndosoRequestDto;
import com.negociadororq.application.dto.EndosoResponseDto;
import com.negociadororq.application.exception.PeticionInvalidaException;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.port.output.EndosoOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.port.output.NegociacionPatchOutputPort;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.Operacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EndosoDescuentoServiceImpl — registro de endosos")
class EndosoDescuentoServiceImplTest {

    @Mock
    private NegociacionOutputPort negociacionPort;

    @Mock
    private EndosoOutputPort endosoPort;

    @Mock
    private FacturaOutputPort facturaPort;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private FacturaPatchOutputPort facturaPatchPort;

    @Mock
    private DocumentoEndosoService documentoEndosoService;

    @Mock
    private NegociacionPatchOutputPort negociacionPatchPort;

    @InjectMocks
    private EndosoDescuentoServiceImpl service;

    private Negociacion buildNegociacionFactoring(String negId) {
        Operacion op = Operacion.builder().tipo("FACTORING").clase("NORMAL").build();
        FacturaNegociada fn = FacturaNegociada.builder()
                .numeroFactura("FAC-001")
                .rutProveedor("800111222")
                .rutPagador("900333444")
                .fondeadores(List.of(FondeadorAsignacion.builder()
                        .rutFondeador("700555666").nombreFondeador("Fond S.A.").build()))
                .build();
        return Negociacion.builder()
                .id(negId)
                .tipoOperacion(op)
                .facturas(List.of(fn))
                .build();
    }

    private Negociacion buildNegociacionConfirming(String negId) {
        Operacion op = Operacion.builder().tipo("CONFIRMING").clase("NORMAL").build();
        FacturaNegociada fn = FacturaNegociada.builder()
                .numeroFactura("FAC-002")
                .rutProveedor("800111222")
                .rutPagador("900333444")
                .fondeadores(List.of(FondeadorAsignacion.builder()
                        .rutFondeador("700555666").nombreFondeador("Fond S.A.").build()))
                .build();
        return Negociacion.builder()
                .id(negId)
                .tipoOperacion(op)
                .facturas(List.of(fn))
                .build();
    }

    private Factura buildFactura(String numero) {
        Factura f = new Factura();
        f.setId("fac-id-" + numero);
        f.setNumeroFactura(numero);
        f.setRutProveedor("800111222");
        f.setRutPagador("900333444");
        f.setValorFactura(new BigDecimal("5000"));
        return f;
    }

    // ─── Validaciones de entrada ──────────────────────────────────

    @Test
    @DisplayName("Lanza RecursoNoEncontradoException cuando la negociación no existe")
    void negociacionNoExiste_lanzaExcepcion() {
        when(negociacionPort.findById("NEG-X")).thenReturn(Optional.empty());

        EndosoRequestDto req = new EndosoRequestDto();
        req.setIdNegociacion("NEG-X");

        assertThatThrownBy(() -> service.registrarEndoso(req))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("NEG-X");
    }

    @Test
    @DisplayName("Lanza PeticionInvalidaException cuando la lista de facturas es null")
    void facturasNull_lanzaExcepcion() {
        when(negociacionPort.findById("NEG-1")).thenReturn(Optional.of(buildNegociacionFactoring("NEG-1")));

        EndosoRequestDto req = new EndosoRequestDto();
        req.setIdNegociacion("NEG-1");
        req.setNumerosFactura(null);

        assertThatThrownBy(() -> service.registrarEndoso(req))
                .isInstanceOf(PeticionInvalidaException.class);
    }

    @Test
    @DisplayName("Lanza PeticionInvalidaException cuando la lista de facturas está vacía")
    void facturasVacias_lanzaExcepcion() {
        when(negociacionPort.findById("NEG-1")).thenReturn(Optional.of(buildNegociacionFactoring("NEG-1")));

        EndosoRequestDto req = new EndosoRequestDto();
        req.setIdNegociacion("NEG-1");
        req.setNumerosFactura(Collections.emptyList());

        assertThatThrownBy(() -> service.registrarEndoso(req))
                .isInstanceOf(PeticionInvalidaException.class);
    }

    // ─── Flujo exitoso ────────────────────────────────────────────

    @Test
    @DisplayName("FACTORING: registra endoso correctamente cuando el proveedor hace la solicitud")
    void factoring_endosoExitoso() {
        Negociacion neg = buildNegociacionFactoring("NEG-1");
        Factura factura = buildFactura("FAC-001");

        when(negociacionPort.findById("NEG-1")).thenReturn(Optional.of(neg));
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));
        when(endosoPort.existsByNegociacionIdAndNumeroFactura("NEG-1", "FAC-001")).thenReturn(false);
        when(endosoPort.save(any())).thenReturn(new Endoso());

        EndosoRequestDto req = new EndosoRequestDto();
        req.setIdNegociacion("NEG-1");
        req.setRutActor("800111222");
        req.setNumerosFactura(List.of("FAC-001"));

        EndosoResponseDto result = service.registrarEndoso(req);

        assertThat(result.getFacturasEndosadas()).contains("FAC-001");
        assertThat(result.getEstadoEndoso()).isEqualTo("PENDIENTE");
        verify(endosoPort).save(any());
        verify(facturaPatchPort).actualizarEstadoPorId("fac-id-FAC-001", EstadoFirma.EN_REVISION);
    }

    @Test
    @DisplayName("CONFIRMING: registra endoso correctamente cuando el pagador hace la solicitud")
    void confirming_endosoExitoso() {
        Negociacion neg = buildNegociacionConfirming("NEG-2");
        Factura factura = buildFactura("FAC-002");

        when(negociacionPort.findById("NEG-2")).thenReturn(Optional.of(neg));
        when(facturaPort.findByNumeroFactura("FAC-002")).thenReturn(Optional.of(factura));
        when(endosoPort.existsByNegociacionIdAndNumeroFactura("NEG-2", "FAC-002")).thenReturn(false);
        when(endosoPort.save(any())).thenReturn(new Endoso());

        EndosoRequestDto req = new EndosoRequestDto();
        req.setIdNegociacion("NEG-2");
        req.setRutActor("900333444");
        req.setNumerosFactura(List.of("FAC-002"));

        EndosoResponseDto result = service.registrarEndoso(req);

        assertThat(result.getFacturasEndosadas()).contains("FAC-002");
        assertThat(result.getEstadoEndoso()).isEqualTo("PENDIENTE");
    }

    // ─── Flujos de rechazo ────────────────────────────────────────

    @Test
    @DisplayName("FACTORING: rechaza cuando el actor no es el proveedor")
    void factoring_actorNoProv_rechaza() {
        Negociacion neg = buildNegociacionFactoring("NEG-1");
        when(negociacionPort.findById("NEG-1")).thenReturn(Optional.of(neg));

        EndosoRequestDto req = new EndosoRequestDto();
        req.setIdNegociacion("NEG-1");
        req.setRutActor("PAGADOR-EQUIVOCADO");
        req.setNumerosFactura(List.of("FAC-001"));

        EndosoResponseDto result = service.registrarEndoso(req);

        assertThat(result.getFacturasRechazadas()).hasSize(1);
        assertThat(result.getFacturasRechazadas().get(0)).contains("PROVEEDOR");
        assertThat(result.getEstadoEndoso()).isEqualTo("RECHAZADO");
        verify(endosoPort, never()).save(any());
    }

    @Test
    @DisplayName("Rechaza factura que no existe en BD")
    void facturaNoExisteEnBD_rechaza() {
        Negociacion neg = buildNegociacionFactoring("NEG-1");
        when(negociacionPort.findById("NEG-1")).thenReturn(Optional.of(neg));
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.empty());

        EndosoRequestDto req = new EndosoRequestDto();
        req.setIdNegociacion("NEG-1");
        req.setRutActor("800111222");
        req.setNumerosFactura(List.of("FAC-001"));

        EndosoResponseDto result = service.registrarEndoso(req);

        assertThat(result.getFacturasRechazadas()).hasSize(1);
        assertThat(result.getFacturasRechazadas().get(0)).contains("no existe en BD");
    }

    @Test
    @DisplayName("Rechaza factura que ya tiene endoso registrado")
    void facturaYaEndosada_rechaza() {
        Negociacion neg = buildNegociacionFactoring("NEG-1");
        Factura factura = buildFactura("FAC-001");

        when(negociacionPort.findById("NEG-1")).thenReturn(Optional.of(neg));
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));
        when(endosoPort.existsByNegociacionIdAndNumeroFactura("NEG-1", "FAC-001")).thenReturn(true);

        EndosoRequestDto req = new EndosoRequestDto();
        req.setIdNegociacion("NEG-1");
        req.setRutActor("800111222");
        req.setNumerosFactura(List.of("FAC-001"));

        EndosoResponseDto result = service.registrarEndoso(req);

        assertThat(result.getFacturasRechazadas()).hasSize(1);
        assertThat(result.getFacturasRechazadas().get(0)).contains("ya tiene endoso");
        verify(endosoPort, never()).save(any());
    }

    // ─── Estado global ────────────────────────────────────────────

    @Test
    @DisplayName("estadoEndoso es PARCIAL cuando hay facturas aceptadas y rechazadas")
    void estadoGlobal_parcial() {
        Operacion op = Operacion.builder().tipo("FACTORING").clase("NORMAL").build();
        FacturaNegociada fn1 = FacturaNegociada.builder()
                .numeroFactura("FAC-001").rutProveedor("800111222").rutPagador("900333444")
                .fondeadores(List.of(FondeadorAsignacion.builder()
                        .rutFondeador("700555666").nombreFondeador("F").build()))
                .build();
        Negociacion neg = Negociacion.builder()
                .id("NEG-1").tipoOperacion(op).facturas(List.of(fn1)).build();
        Factura factura = buildFactura("FAC-001");

        when(negociacionPort.findById("NEG-1")).thenReturn(Optional.of(neg));
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));
        when(endosoPort.existsByNegociacionIdAndNumeroFactura(anyString(), anyString())).thenReturn(false);
        when(endosoPort.save(any())).thenReturn(new Endoso());

        EndosoRequestDto req = new EndosoRequestDto();
        req.setIdNegociacion("NEG-1");
        req.setRutActor("800111222");
        req.setNumerosFactura(List.of("FAC-001", "FAC-INVALIDA"));

        EndosoResponseDto result = service.registrarEndoso(req);

        assertThat(result.getEstadoEndoso()).isEqualTo("PARCIAL");
        assertThat(result.getFacturasEndosadas()).contains("FAC-001");
        assertThat(result.getFacturasRechazadas()).hasSize(1);
    }

    @Test
    @DisplayName("Notifica el endoso después de guardarlo")
    void notificaEndoso_despuesDeGuardar() {
        Negociacion neg = buildNegociacionFactoring("NEG-1");
        Factura factura = buildFactura("FAC-001");

        when(negociacionPort.findById("NEG-1")).thenReturn(Optional.of(neg));
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));
        when(endosoPort.existsByNegociacionIdAndNumeroFactura("NEG-1", "FAC-001")).thenReturn(false);
        when(endosoPort.save(any())).thenReturn(new Endoso());

        EndosoRequestDto req = new EndosoRequestDto();
        req.setIdNegociacion("NEG-1");
        req.setRutActor("800111222");
        req.setNumerosFactura(List.of("FAC-001"));

        service.registrarEndoso(req);

        verify(notificacionService).notificarEndoso(any());
        verify(negociacionPatchPort).actualizarEstadoEndosoPorId(anyString(), anyString(), anyBoolean());
    }
}