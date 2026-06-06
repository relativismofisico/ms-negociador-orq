package com.negociadororq.application.service;

import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.domain.model.Factura;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FacturaServiceImpl — gestión de facturas")
class FacturaServiceImplTest {

    @Mock
    private FacturaOutputPort facturaPort;

    @InjectMocks
    private FacturaServiceImpl service;

    @Captor
    private ArgumentCaptor<Factura> facturaCaptor;

    // ─── actualizarEstadoFirma ────────────────────────────────────

    @Test
    @DisplayName("actualizarEstadoFirma actualiza y guarda cuando factura existe")
    void actualizarEstadoFirma_facturaExistente() {
        Factura factura = new Factura();
        factura.setNumeroFactura("FAC-001");
        factura.setEstadoFirma("SIN_CESION");
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));
        when(facturaPort.save(any())).thenReturn(factura);

        service.actualizarEstadoFirma("FAC-001", "CESION");

        verify(facturaPort).save(facturaCaptor.capture());
        assertThat(facturaCaptor.getValue().getEstadoFirma()).isEqualTo("CESION");
    }

    @Test
    @DisplayName("actualizarEstadoFirma lanza RuntimeException cuando factura no existe")
    void actualizarEstadoFirma_facturaInexistente() {
        when(facturaPort.findByNumeroFactura("FAC-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizarEstadoFirma("FAC-999", "CESION"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("FAC-999");
    }

    // ─── actualizarEstadoFirmaMasivo ──────────────────────────────

    @Test
    @DisplayName("actualizarEstadoFirmaMasivo actualiza todas las facturas encontradas")
    void actualizarEstadoFirmaMasivo_actualizaTodas() {
        Factura f1 = new Factura();
        f1.setNumeroFactura("FAC-001");
        f1.setEstadoFirma("SIN_CESION");
        Factura f2 = new Factura();
        f2.setNumeroFactura("FAC-002");
        f2.setEstadoFirma("SIN_CESION");
        when(facturaPort.findByNumeroFacturaIn(List.of("FAC-001", "FAC-002")))
                .thenReturn(List.of(f1, f2));

        service.actualizarEstadoFirmaMasivo(List.of("FAC-001", "FAC-002"), "APROBACION");

        assertThat(f1.getEstadoFirma()).isEqualTo("APROBACION");
        assertThat(f2.getEstadoFirma()).isEqualTo("APROBACION");
        verify(facturaPort).saveAll(List.of(f1, f2));
    }

    @Test
    @DisplayName("actualizarEstadoFirmaMasivo no guarda cuando no hay facturas")
    void actualizarEstadoFirmaMasivo_sinFacturas() {
        when(facturaPort.findByNumeroFacturaIn(anyList())).thenReturn(Collections.emptyList());

        service.actualizarEstadoFirmaMasivo(List.of("FAC-999"), "APROBACION");

        verify(facturaPort, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("actualizarEstadoFirmaMasivo con lista de un solo elemento funciona correctamente")
    void actualizarEstadoFirmaMasivo_unElemento() {
        Factura f1 = new Factura();
        f1.setNumeroFactura("FAC-001");
        f1.setEstadoFirma("DESCUENTO");
        when(facturaPort.findByNumeroFacturaIn(List.of("FAC-001"))).thenReturn(List.of(f1));

        service.actualizarEstadoFirmaMasivo(List.of("FAC-001"), "RECHAZADA");

        assertThat(f1.getEstadoFirma()).isEqualTo("RECHAZADA");
        verify(facturaPort).saveAll(List.of(f1));
    }

    // ─── obtenerPorNumero ─────────────────────────────────────────

    @Test
    @DisplayName("obtenerPorNumero retorna factura cuando existe")
    void obtenerPorNumero_retornaFactura() {
        Factura factura = new Factura();
        factura.setNumeroFactura("FAC-001");
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));

        Factura result = service.obtenerPorNumero("FAC-001");

        assertThat(result.getNumeroFactura()).isEqualTo("FAC-001");
    }

    @Test
    @DisplayName("obtenerPorNumero lanza RuntimeException cuando no existe")
    void obtenerPorNumero_noExiste() {
        when(facturaPort.findByNumeroFactura(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerPorNumero("FAC-X"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("FAC-X");
    }

    // ─── existenFacturasDePagador ─────────────────────────────────

    @Test
    @DisplayName("existenFacturasDePagador delega al port y retorna true")
    void existenFacturasDePagador_retornaTrue() {
        List<String> nums = List.of("FAC-001");
        when(facturaPort.existsByNumeroFacturaInAndRutPagador(nums, "800123456")).thenReturn(true);

        boolean result = service.existenFacturasDePagador(nums, "800123456");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("existenFacturasDePagador retorna false cuando no hay facturas del pagador")
    void existenFacturasDePagador_retornaFalse() {
        List<String> nums = List.of("FAC-001");
        when(facturaPort.existsByNumeroFacturaInAndRutPagador(nums, "800999999")).thenReturn(false);

        boolean result = service.existenFacturasDePagador(nums, "800999999");

        assertThat(result).isFalse();
    }
}