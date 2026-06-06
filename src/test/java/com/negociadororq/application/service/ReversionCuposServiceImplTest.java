package com.negociadororq.application.service;

import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.EndosoPatchOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.Operacion;
import com.negociadororq.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReversionCuposServiceImpl — reversión de cupos por factura")
class ReversionCuposServiceImplTest {

    @Mock
    private NegociacionOutputPort negociacionPort;

    @Mock
    private FacturaOutputPort facturaPort;

    @Mock
    private FacturaPatchOutputPort facturaPatchPort;

    @Mock
    private EndosoPatchOutputPort endosoPatchPort;

    @Mock
    private CupoReversionService cupoReversionService;

    @Mock
    private UserOutputPort userPort;

    @Mock
    private CompanyOutputPort companyPort;

    @Mock
    private NotificacionService notificacionService;

    @InjectMocks
    private ReversionCuposServiceImpl service;

    private Factura buildFactura(String numero, String rutProveedor, String rutPagador) {
        Factura f = new Factura();
        f.setNumeroFactura(numero);
        f.setRutProveedor(rutProveedor);
        f.setRutPagador(rutPagador);
        return f;
    }

    private Negociacion buildNegociacion(String tipoOp, String numero, BigDecimal valor,
                                         List<FondeadorAsignacion> fondeadores) {
        FacturaNegociada fn = FacturaNegociada.builder()
                .numeroFactura(numero)
                .valorFactura(valor)
                .fondeadores(fondeadores)
                .build();
        return Negociacion.builder()
                .id("NEG-001")
                .tipoOperacion(Operacion.builder().tipo(tipoOp).clase("NORMAL").build())
                .rutEmpresa("800111222")
                .facturas(List.of(fn))
                .build();
    }

    // ─── Validaciones de existencia ───────────────────────────────

    @Test
    @DisplayName("Lanza RuntimeException cuando la factura no existe")
    void facturaNoExiste_lanzaExcepcion() {
        when(facturaPort.findByNumeroFactura("FAC-X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revertirPorFactura("FAC-X", "actor", "motivo"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Factura no encontrada");
    }

    @Test
    @DisplayName("Lanza RuntimeException cuando la negociación no existe")
    void negociacionNoExiste_lanzaExcepcion() {
        when(facturaPort.findByNumeroFactura("FAC-001"))
                .thenReturn(Optional.of(buildFactura("FAC-001", "800111", "900222")));
        when(negociacionPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revertirPorFactura("FAC-001", "actor", "motivo"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Negociación no encontrada");
    }

    @Test
    @DisplayName("Lanza NoSuchElementException cuando la factura no está en la negociación")
    void facturaNegociadaNoEncontrada_lanzaExcepcion() {
        Factura factura = buildFactura("FAC-001", "800111", "900222");
        Negociacion neg = buildNegociacion("FACTORING", "OTRA-FAC", BigDecimal.TEN,
                List.of());

        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));
        when(negociacionPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(neg));

        assertThatThrownBy(() -> service.revertirPorFactura("FAC-001", "actor", "motivo"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("Lanza RuntimeException cuando el usuario no existe")
    void usuarioNoExiste_lanzaExcepcion() {
        Factura factura = buildFactura("FAC-001", "800111", "900222");
        Negociacion neg = buildNegociacion("FACTORING", "FAC-001", new BigDecimal("5000"),
                Collections.emptyList());

        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));
        when(negociacionPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(neg));
        when(userPort.findByUsername("actor")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revertirPorFactura("FAC-001", "actor", "motivo"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    @DisplayName("Lanza RuntimeException cuando la empresa no existe")
    void empresaNoExiste_lanzaExcepcion() {
        Factura factura = buildFactura("FAC-001", "800111", "900222");
        Negociacion neg = buildNegociacion("FACTORING", "FAC-001", new BigDecimal("5000"),
                Collections.emptyList());

        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));
        when(negociacionPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(neg));
        when(userPort.findByUsername("actor"))
                .thenReturn(Optional.of(User.builder().ideUser(1L).build()));
        when(companyPort.findByNit("800111222")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revertirPorFactura("FAC-001", "actor", "motivo"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Empresa no encontrada");
    }

    // ─── Flujo FACTORING con fondeadores ─────────────────────────

    @Test
    @DisplayName("FACTORING: revierte cupos de usuario, empresa y fondeador(factoring)")
    void factoring_revierteCorrectamente() {
        Factura factura = buildFactura("FAC-001", "800PROV", "900PAG");
        FondeadorAsignacion fon = FondeadorAsignacion.builder()
                .rutFondeador("700FOND").nombreFondeador("Fond").build();
        Negociacion neg = buildNegociacion("FACTORING", "FAC-001", new BigDecimal("3000"),
                List.of(fon));

        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));
        when(negociacionPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(neg));
        when(userPort.findByUsername("actor"))
                .thenReturn(Optional.of(User.builder().ideUser(10L).build()));
        when(companyPort.findByNit("800111222"))
                .thenReturn(Optional.of(Company.builder().ideCompany(20L).build()));
        when(companyPort.findByNit("700FOND"))
                .thenReturn(Optional.of(Company.builder().ideCompany(30L).build()));
        when(companyPort.findByNit("800PROV"))
                .thenReturn(Optional.of(Company.builder().ideCompany(40L).build()));

        service.revertirPorFactura("FAC-001", "actor", "liquidacion fallida");

        verify(cupoReversionService).revertirCupoUsuario(10L, new BigDecimal("3000"));
        verify(cupoReversionService).revertirCupoEmpresa(20L, new BigDecimal("3000"));
        verify(cupoReversionService).revertirCupoFondeadorFactoring(30L, 40L, new BigDecimal("3000"));
    }

    // ─── Flujo CONFIRMING con fondeadores ────────────────────────

    @Test
    @DisplayName("CONFIRMING: revierte cupos de usuario, empresa y fondeador(confirming)")
    void confirming_revierteCorrectamente() {
        Factura factura = buildFactura("FAC-001", "800PROV", "900PAG");
        FondeadorAsignacion fon = FondeadorAsignacion.builder()
                .rutFondeador("700FOND").nombreFondeador("Fond").build();
        Negociacion neg = buildNegociacion("CONFIRMING", "FAC-001", new BigDecimal("2000"),
                List.of(fon));

        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));
        when(negociacionPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(neg));
        when(userPort.findByUsername("actor"))
                .thenReturn(Optional.of(User.builder().ideUser(10L).build()));
        when(companyPort.findByNit("800111222"))
                .thenReturn(Optional.of(Company.builder().ideCompany(20L).build()));
        when(companyPort.findByNit("700FOND"))
                .thenReturn(Optional.of(Company.builder().ideCompany(30L).build()));
        when(companyPort.findByNit("900PAG"))
                .thenReturn(Optional.of(Company.builder().ideCompany(50L).build()));

        service.revertirPorFactura("FAC-001", "actor", "motivo");

        verify(cupoReversionService).revertirCupoFondeadorConfirming(30L, 50L, new BigDecimal("2000"));
        verify(cupoReversionService, never()).revertirCupoFondeadorFactoring(anyLong(), anyLong(), any());
    }

    // ─── Actualización de estados ─────────────────────────────────

    @Test
    @DisplayName("Actualiza estado de firma de factura a RECHAZADA y endoso a RECHAZADO")
    void actualizaEstados_correctamente() {
        Factura factura = buildFactura("FAC-001", "800PROV", "900PAG");
        Negociacion neg = buildNegociacion("FACTORING", "FAC-001", new BigDecimal("1000"),
                Collections.emptyList());

        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));
        when(negociacionPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(neg));
        when(userPort.findByUsername("actor"))
                .thenReturn(Optional.of(User.builder().ideUser(1L).build()));
        when(companyPort.findByNit("800111222"))
                .thenReturn(Optional.of(Company.builder().ideCompany(2L).build()));

        service.revertirPorFactura("FAC-001", "actor", "test");

        verify(facturaPatchPort).actualizarEstadoFirmaPorNoFactura("FAC-001", EstadoFirma.RECHAZADA);
        verify(endosoPatchPort).actualizarEstadoEndosoPorNoFactura("NEG-001", "FAC-001", EstadoEndoso.RECHAZADO);
    }

    @Test
    @DisplayName("Notifica la liquidación fallida al finalizar")
    void notificaLiquidacionFallida() {
        Factura factura = buildFactura("FAC-001", "800PROV", "900PAG");
        Negociacion neg = buildNegociacion("FACTORING", "FAC-001", new BigDecimal("1000"),
                Collections.emptyList());

        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));
        when(negociacionPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(neg));
        when(userPort.findByUsername("actor"))
                .thenReturn(Optional.of(User.builder().ideUser(1L).build()));
        when(companyPort.findByNit("800111222"))
                .thenReturn(Optional.of(Company.builder().ideCompany(2L).build()));

        service.revertirPorFactura("FAC-001", "actor", "sin fondos");

        verify(notificacionService).notificarLiquidacionFallida(neg, "sin fondos");
    }

    @Test
    @DisplayName("Sin fondeadores: no llama a cupoReversionService para fondeadores")
    void sinFondeadores_noRevierte() {
        Factura factura = buildFactura("FAC-001", "800PROV", "900PAG");
        Negociacion neg = buildNegociacion("FACTORING", "FAC-001", new BigDecimal("1000"), null);

        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));
        when(negociacionPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(neg));
        when(userPort.findByUsername("actor"))
                .thenReturn(Optional.of(User.builder().ideUser(1L).build()));
        when(companyPort.findByNit("800111222"))
                .thenReturn(Optional.of(Company.builder().ideCompany(2L).build()));

        service.revertirPorFactura("FAC-001", "actor", "motivo");

        verify(cupoReversionService, never()).revertirCupoFondeadorFactoring(anyLong(), anyLong(), any());
        verify(cupoReversionService, never()).revertirCupoFondeadorConfirming(anyLong(), anyLong(), any());
    }
}