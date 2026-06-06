package com.negociadororq.application.service;

import com.negociadororq.application.dto.NegociacionRequestDto;
import com.negociadororq.application.dto.NegociacionResponseDto;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.domain.enums.EstadoNegociacion;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NegociacionFactoringComfirmingServiceImpl — creación de negociaciones")
class NegociacionFactoringComfirmingServiceImplTest {

    @Mock
    private FacturaOutputPort facturaPort;

    @Mock
    private NegociacionOutputPort negociacionPort;

    @Mock
    private UserOutputPort userPort;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private ProcesarFacturaNegociacionService procesarFacturaNegociacionService;

    @InjectMocks
    private NegociacionFactoringComfirmingServiceImpl service;

    private NegociacionRequestDto buildRequest(String tipoOp, String rolEmpresa) {
        NegociacionRequestDto req = new NegociacionRequestDto();
        req.setRutEmpresa("800111222");
        req.setRolEmpresa(rolEmpresa);
        req.setRutOperador("operador-rut");
        req.setTipoOperacion(Operacion.builder().tipo(tipoOp).clase("NORMAL").build());
        FacturaNegociada fn = FacturaNegociada.builder()
                .numeroFactura("FAC-001")
                .valorFactura(new BigDecimal("5000"))
                .fondeadores(List.of(FondeadorAsignacion.builder()
                        .rutFondeador("700555666").nombreFondeador("Fond").build()))
                .build();
        req.setFacturas(List.of(fn));
        return req;
    }

    private Factura buildFactura() {
        Factura f = new Factura();
        f.setNumeroFactura("FAC-001");
        f.setRutPagador("900333444");
        f.setRutProveedor("800111222");
        return f;
    }

    // ─── Validaciones iniciales ───────────────────────────────────

    @Test
    @DisplayName("Lanza RecursoNoEncontradoException cuando el operador no existe")
    void operadorNoExiste_lanzaExcepcion() {
        when(userPort.findByUsername("operador-rut")).thenReturn(Optional.empty());

        NegociacionRequestDto req = buildRequest("FACTORING", "PROVEEDOR");

        assertThatThrownBy(() -> service.crearNegociacion(req))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("operador-rut");
    }

    @Test
    @DisplayName("Lanza RecursoNoEncontradoException cuando la factura no existe")
    void facturaNoExiste_lanzaExcepcion() {
        when(userPort.findByUsername(anyString()))
                .thenReturn(Optional.of(User.builder().ideUser(1L).username("operador-rut").build()));
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.empty());

        NegociacionRequestDto req = buildRequest("FACTORING", "PROVEEDOR");

        assertThatThrownBy(() -> service.crearNegociacion(req))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("FAC-001");
    }

    // ─── Flujos exitosos ──────────────────────────────────────────

    @Test
    @DisplayName("FACTORING exitoso: crea negociación con estado PENDIENTE")
    void factoring_exitoso_estadoPendiente() {
        when(userPort.findByUsername(anyString()))
                .thenReturn(Optional.of(User.builder().ideUser(1L).build()));
        when(facturaPort.findByNumeroFactura("FAC-001"))
                .thenReturn(Optional.of(buildFactura()));

        FacturaNegociada procesada = FacturaNegociada.builder()
                .numeroFactura("FAC-001")
                .valorFactura(new BigDecimal("5000"))
                .rutProveedor("800111222")
                .rutPagador("900333444")
                .fondeadores(List.of(FondeadorAsignacion.builder()
                        .rutFondeador("700555666").nombreFondeador("F").build()))
                .build();
        when(procesarFacturaNegociacionService.procesarFactura(any(), any())).thenReturn(procesada);
        when(negociacionPort.save(any())).thenAnswer(inv -> {
            Negociacion n = inv.getArgument(0);
            n.setId("NEG-SAVED-001");
            return n;
        });

        NegociacionRequestDto req = buildRequest("FACTORING", "PROVEEDOR");
        NegociacionResponseDto result = service.crearNegociacion(req);

        assertThat(result.getEstado()).isEqualTo(EstadoNegociacion.PENDIENTE.name());
        assertThat(result.getFacturas()).hasSize(1);
        assertThat(result.getTotalNegociado()).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("Todas las facturas rechazadas: estado RECHAZADA")
    void todasRechazadas_estadoRechazada() {
        when(userPort.findByUsername(anyString()))
                .thenReturn(Optional.of(User.builder().ideUser(1L).build()));
        when(facturaPort.findByNumeroFactura("FAC-001"))
                .thenReturn(Optional.of(buildFactura()));
        when(procesarFacturaNegociacionService.procesarFactura(any(), any()))
                .thenThrow(new RuntimeException("Sin cupo"));
        when(negociacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NegociacionRequestDto req = buildRequest("FACTORING", "PROVEEDOR");
        NegociacionResponseDto result = service.crearNegociacion(req);

        assertThat(result.getEstado()).isEqualTo(EstadoNegociacion.RECHAZADA.name());
        assertThat(result.getErrores()).contains("Sin cupo");
        assertThat(result.getTotalNegociado()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("rolEmpresa PROVEEDOR → rutProveedor = rutEmpresa")
    void rolProveedor_rutProveedorEsRutEmpresa() {
        when(userPort.findByUsername(anyString()))
                .thenReturn(Optional.of(User.builder().ideUser(1L).build()));
        when(facturaPort.findByNumeroFactura("FAC-001"))
                .thenReturn(Optional.of(buildFactura()));

        FacturaNegociada procesada = FacturaNegociada.builder()
                .numeroFactura("FAC-001")
                .valorFactura(new BigDecimal("1000"))
                .rutProveedor("800111222")
                .rutPagador("900333444")
                .fondeadores(List.of(FondeadorAsignacion.builder()
                        .rutFondeador("700555666").nombreFondeador("F").build()))
                .build();
        when(procesarFacturaNegociacionService.procesarFactura(any(), any())).thenReturn(procesada);
        when(negociacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NegociacionRequestDto req = buildRequest("FACTORING", "PROVEEDOR");
        service.crearNegociacion(req);

        verify(negociacionPort).save(argThat(n -> "800111222".equals(n.getRutProveedor())));
    }

    @Test
    @DisplayName("Un pagador: rutPagador se asigna directamente (no lista)")
    void unPagador_rutPagadorDirecto() {
        when(userPort.findByUsername(anyString()))
                .thenReturn(Optional.of(User.builder().ideUser(1L).build()));
        when(facturaPort.findByNumeroFactura("FAC-001"))
                .thenReturn(Optional.of(buildFactura()));

        FacturaNegociada procesada = FacturaNegociada.builder()
                .numeroFactura("FAC-001")
                .valorFactura(new BigDecimal("1000"))
                .rutProveedor("800111222")
                .rutPagador("900333444")
                .fondeadores(List.of(FondeadorAsignacion.builder()
                        .rutFondeador("700555666").nombreFondeador("F").build()))
                .build();
        when(procesarFacturaNegociacionService.procesarFactura(any(), any())).thenReturn(procesada);
        when(negociacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NegociacionRequestDto req = buildRequest("FACTORING", "PROVEEDOR");
        service.crearNegociacion(req);

        verify(negociacionPort).save(argThat(n ->
                "900333444".equals(n.getRutPagador()) && n.getRutPagadores() == null));
    }

    @Test
    @DisplayName("Múltiples pagadores: setRutPagadores en lugar de setRutPagador")
    void multipesPagadores_asignaListaEnLugarDeUno() {
        when(userPort.findByUsername(anyString()))
                .thenReturn(Optional.of(User.builder().ideUser(1L).build()));

        // FAC-001 con pagador A, FAC-002 con pagador B
        Factura factura1 = new Factura(); factura1.setNumeroFactura("FAC-001");
        factura1.setRutPagador("900PAG1"); factura1.setRutProveedor("800111222");
        Factura factura2 = new Factura(); factura2.setNumeroFactura("FAC-002");
        factura2.setRutPagador("900PAG2"); factura2.setRutProveedor("800111222");

        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura1));
        when(facturaPort.findByNumeroFactura("FAC-002")).thenReturn(Optional.of(factura2));

        when(procesarFacturaNegociacionService.procesarFactura(any(), any()))
                .thenThrow(new RuntimeException("sin cupo"));
        when(negociacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NegociacionRequestDto req = new NegociacionRequestDto();
        req.setRutEmpresa("800111222");
        req.setRolEmpresa("PROVEEDOR");
        req.setRutOperador("operador-rut");
        req.setTipoOperacion(Operacion.builder().tipo("CONFIRMING").clase("NORMAL").build());
        FacturaNegociada fn1 = FacturaNegociada.builder().numeroFactura("FAC-001")
                .valorFactura(new BigDecimal("1000"))
                .fondeadores(List.of(FondeadorAsignacion.builder().rutFondeador("700X").nombreFondeador("F").build()))
                .build();
        FacturaNegociada fn2 = FacturaNegociada.builder().numeroFactura("FAC-002")
                .valorFactura(new BigDecimal("2000"))
                .fondeadores(List.of(FondeadorAsignacion.builder().rutFondeador("700X").nombreFondeador("F").build()))
                .build();
        req.setFacturas(List.of(fn1, fn2));

        NegociacionResponseDto result = service.crearNegociacion(req);

        assertThat(result.getEstado()).isEqualTo(EstadoNegociacion.RECHAZADA.name());
        verify(negociacionPort).save(argThat(n -> n.getRutPagadores() != null && n.getRutPagador() == null));
    }

    @Test
    @DisplayName("rolEmpresa PAGADOR: rutProveedor toma el valor de la factura aceptada")
    void rolPagador_rutProveedorDesdeFactura() {
        when(userPort.findByUsername(anyString()))
                .thenReturn(Optional.of(User.builder().ideUser(1L).build()));
        when(facturaPort.findByNumeroFactura("FAC-001"))
                .thenReturn(Optional.of(buildFactura()));

        FacturaNegociada procesada = FacturaNegociada.builder()
                .numeroFactura("FAC-001")
                .valorFactura(new BigDecimal("1000"))
                .rutProveedor("800PROV-REAL")
                .rutPagador("900333444")
                .fondeadores(List.of(FondeadorAsignacion.builder()
                        .rutFondeador("700555666").nombreFondeador("F").build()))
                .build();
        when(procesarFacturaNegociacionService.procesarFactura(any(), any())).thenReturn(procesada);
        when(negociacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NegociacionRequestDto req = buildRequest("CONFIRMING", "PAGADOR");
        service.crearNegociacion(req);

        verify(negociacionPort).save(argThat(n -> "800PROV-REAL".equals(n.getRutProveedor())));
    }

    @Test
    @DisplayName("Notifica la creación de la negociación")
    void notificaCreacionNegociacion() {
        when(userPort.findByUsername(anyString()))
                .thenReturn(Optional.of(User.builder().ideUser(1L).build()));
        when(facturaPort.findByNumeroFactura("FAC-001"))
                .thenReturn(Optional.of(buildFactura()));
        when(procesarFacturaNegociacionService.procesarFactura(any(), any()))
                .thenThrow(new RuntimeException("error"));
        when(negociacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.crearNegociacion(buildRequest("FACTORING", "PROVEEDOR"));

        verify(notificacionService).notificarCreacionNegociacion(any());
    }

}