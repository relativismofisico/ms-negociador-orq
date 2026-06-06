package com.negociadororq.application.service;

import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.domain.model.Factura;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FacturaVencimientoService — marcado nocturno de facturas vencidas")
class FacturaVencimientoServiceTest {

    @Mock
    private FacturaOutputPort facturaPort;

    @InjectMocks
    private FacturaVencimientoService service;

    @Test
    @DisplayName("No actualiza cuando no hay facturas pendientes")
    void sinFacturas_noActualiza() {
        when(facturaPort.findFacturasPendientesVencimiento()).thenReturn(Collections.emptyList());

        service.actualizarFacturasVencidas();

        verify(facturaPort, never()).save(any());
    }

    @Test
    @DisplayName("Salta facturas sin fecha de vencimiento (continue)")
    void facturasSinFechaVencimiento_omite() {
        Factura sinFecha = new Factura();
        sinFecha.setNumeroFactura("FAC-001");
        sinFecha.setFechavencimientoFactura(null);
        when(facturaPort.findFacturasPendientesVencimiento()).thenReturn(List.of(sinFecha));

        service.actualizarFacturasVencidas();

        verify(facturaPort, never()).save(any());
    }

    @Test
    @DisplayName("Marca como VENCIDA cuando quedan exactamente 10 días (límite)")
    void facturaDias10_marcaVencida() {
        Factura factura = new Factura();
        factura.setNumeroFactura("FAC-001");
        factura.setFechavencimientoFactura(LocalDate.now().plusDays(10));
        when(facturaPort.findFacturasPendientesVencimiento()).thenReturn(List.of(factura));
        when(facturaPort.save(any())).thenReturn(factura);

        service.actualizarFacturasVencidas();

        verify(facturaPort).save(factura);
        assert factura.getEstadoNegociacion().equals("VENCIDA");
    }

    @Test
    @DisplayName("Marca como VENCIDA cuando ya venció (días negativos)")
    void facturaVencida_marcaVencida() {
        Factura factura = new Factura();
        factura.setNumeroFactura("FAC-002");
        factura.setFechavencimientoFactura(LocalDate.now().minusDays(1));
        when(facturaPort.findFacturasPendientesVencimiento()).thenReturn(List.of(factura));
        when(facturaPort.save(any())).thenReturn(factura);

        service.actualizarFacturasVencidas();

        verify(facturaPort).save(factura);
    }

    @Test
    @DisplayName("No marca cuando quedan más de 10 días")
    void facturaDias11_noMarca() {
        Factura factura = new Factura();
        factura.setNumeroFactura("FAC-001");
        factura.setFechavencimientoFactura(LocalDate.now().plusDays(11));
        when(facturaPort.findFacturasPendientesVencimiento()).thenReturn(List.of(factura));

        service.actualizarFacturasVencidas();

        verify(facturaPort, never()).save(any());
    }

    @Test
    @DisplayName("Procesa múltiples facturas correctamente: algunas vencen otras no")
    void multiplasFacturas_procesaCorrectamente() {
        Factura vence = new Factura();
        vence.setNumeroFactura("FAC-001");
        vence.setFechavencimientoFactura(LocalDate.now().plusDays(5));

        Factura vigente = new Factura();
        vigente.setNumeroFactura("FAC-002");
        vigente.setFechavencimientoFactura(LocalDate.now().plusDays(30));

        Factura sinFecha = new Factura();
        sinFecha.setNumeroFactura("FAC-003");
        sinFecha.setFechavencimientoFactura(null);

        when(facturaPort.findFacturasPendientesVencimiento())
                .thenReturn(List.of(vence, vigente, sinFecha));
        when(facturaPort.save(any())).thenReturn(vence);

        service.actualizarFacturasVencidas();

        verify(facturaPort, times(1)).save(vence);
    }

    @Test
    @DisplayName("Continúa procesando otras facturas si una lanza excepción")
    void excepcionEnProcesamiento_continuaConOtras() {
        Factura mala = new Factura();
        mala.setNumeroFactura("FAC-BAD");
        mala.setFechavencimientoFactura(LocalDate.now().plusDays(5));

        Factura buena = new Factura();
        buena.setNumeroFactura("FAC-OK");
        buena.setFechavencimientoFactura(LocalDate.now().plusDays(3));

        when(facturaPort.findFacturasPendientesVencimiento())
                .thenReturn(List.of(mala, buena));
        when(facturaPort.save(mala)).thenThrow(new RuntimeException("Error BD simulado"));
        when(facturaPort.save(buena)).thenReturn(buena);

        service.actualizarFacturasVencidas();

        verify(facturaPort).save(mala);
        verify(facturaPort).save(buena);
    }
}