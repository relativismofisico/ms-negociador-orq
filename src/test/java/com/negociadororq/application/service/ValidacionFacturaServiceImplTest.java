package com.negociadororq.application.service;

import com.negociadororq.application.dto.ValidacionFacturaResponseDto;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.domain.model.Factura;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValidacionFacturaServiceImpl — validación de vencimiento de facturas")
class ValidacionFacturaServiceImplTest {

    @Mock
    private FacturaOutputPort facturaPort;

    @InjectMocks
    private ValidacionFacturaServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "diasMinimosVencimiento", 10L);
    }

    @Test
    @DisplayName("Lanza RecursoNoEncontradoException cuando la factura no existe")
    void facturaNoExiste_lanzaExcepcion() {
        when(facturaPort.findByNumeroFactura("FAC-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validarFacturaVencida("FAC-999", LocalDate.now()))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("FAC-999");

        verify(facturaPort).findByNumeroFactura("FAC-999");
    }

    @Test
    @DisplayName("Retorna SIN_FECHA_VENCIMIENTO cuando la factura no tiene fecha de vencimiento")
    void sinFechaVencimiento_retornaCodigo() {
        Factura factura = new Factura();
        factura.setNumeroFactura("FAC-001");
        factura.setFechavencimientoFactura(null);
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));

        ValidacionFacturaResponseDto result = service.validarFacturaVencida("FAC-001", LocalDate.now());

        assertThat(result.isValida()).isFalse();
        assertThat(result.isVencida()).isTrue();
        assertThat(result.getCodigo()).isEqualTo("SIN_FECHA_VENCIMIENTO");
        assertThat(result.getMensaje()).isEqualTo("Factura sin fecha de vencimiento");
    }

    @Test
    @DisplayName("Retorna FACTURA_VIGENTE cuando quedan más de 10 días")
    void facturaDias11_retornaVigente() {
        Factura factura = new Factura();
        factura.setNumeroFactura("FAC-001");
        factura.setFechavencimientoFactura(LocalDate.now().plusDays(11));
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));

        ValidacionFacturaResponseDto result = service.validarFacturaVencida("FAC-001", LocalDate.now());

        assertThat(result.isValida()).isTrue();
        assertThat(result.isVencida()).isFalse();
        assertThat(result.getCodigo()).isEqualTo("FACTURA_VIGENTE");
        assertThat(result.getDiasHastaVencimiento()).isEqualTo(11L);
    }

    @Test
    @DisplayName("Retorna FACTURA_VENCIDA cuando quedan exactamente 10 días (límite del negocio)")
    void facturaDias10_retornaVencida() {
        Factura factura = new Factura();
        factura.setNumeroFactura("FAC-001");
        factura.setFechavencimientoFactura(LocalDate.now().plusDays(10));
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));

        ValidacionFacturaResponseDto result = service.validarFacturaVencida("FAC-001", LocalDate.now());

        assertThat(result.isValida()).isFalse();
        assertThat(result.isVencida()).isTrue();
        assertThat(result.getCodigo()).isEqualTo("FACTURA_VENCIDA");
    }

    @Test
    @DisplayName("Retorna FACTURA_VENCIDA cuando la fecha de vencimiento ya pasó")
    void facturaVencida_retornaCodigo() {
        Factura factura = new Factura();
        factura.setNumeroFactura("FAC-001");
        factura.setFechavencimientoFactura(LocalDate.now().minusDays(5));
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));

        ValidacionFacturaResponseDto result = service.validarFacturaVencida("FAC-001", LocalDate.now());

        assertThat(result.isValida()).isFalse();
        assertThat(result.isVencida()).isTrue();
        assertThat(result.getDiasHastaVencimiento()).isNegative();
        assertThat(result.getMensaje()).contains("días para vencimiento");
    }

    @Test
    @DisplayName("Usa fechaProceso cuando se proporciona en lugar de LocalDate.now()")
    void conFechaProceso_usaFechaPersonalizada() {
        LocalDate fechaProceso = LocalDate.of(2026, 1, 1);
        Factura factura = new Factura();
        factura.setNumeroFactura("FAC-001");
        factura.setFechavencimientoFactura(LocalDate.of(2026, 1, 20));
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));

        ValidacionFacturaResponseDto result = service.validarFacturaVencida("FAC-001", fechaProceso);

        assertThat(result.getDiasHastaVencimiento()).isEqualTo(19L);
        assertThat(result.isValida()).isTrue();
    }

    @Test
    @DisplayName("Usa LocalDate.now() cuando fechaProceso es null")
    void fechaProcesoNula_usaHoy() {
        Factura factura = new Factura();
        factura.setNumeroFactura("FAC-001");
        factura.setFechavencimientoFactura(LocalDate.now().plusDays(15));
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));

        ValidacionFacturaResponseDto result = service.validarFacturaVencida("FAC-001", null);

        assertThat(result.isValida()).isTrue();
        assertThat(result.getDiasHastaVencimiento()).isEqualTo(15L);
    }

    @Test
    @DisplayName("Mensaje FACTURA_VENCIDA incluye días restantes")
    void mensajeVencidaConteneDias() {
        Factura factura = new Factura();
        factura.setNumeroFactura("FAC-001");
        factura.setFechavencimientoFactura(LocalDate.now().plusDays(3));
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));

        ValidacionFacturaResponseDto result = service.validarFacturaVencida("FAC-001", LocalDate.now());

        assertThat(result.getMensaje()).contains("3");
        assertThat(result.getMensaje()).contains("días para vencimiento");
    }
}