package com.negociadororq.application.service;

import com.negociadororq.application.port.output.EndosoOutputPort;
import com.negociadororq.application.port.output.PdfFirmaOutputPort;
import com.negociadororq.domain.enums.RolActor;
import com.negociadororq.domain.model.AprobacionActorDto;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.PendingOtp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
@ExtendWith(MockitoExtension.class)
@DisplayName("FirmaEndosoServiceImpl — firma digital de PDF del endoso")
class FirmaEndosoServiceImplTest {

    @Mock
    private EndosoOutputPort endosoPort;

    @Mock
    private PdfFirmaOutputPort pdfFirmaPort;

    @InjectMocks
    private FirmaEndosoServiceImpl service;

    @Captor
    private ArgumentCaptor<Endoso> endosoCaptor;

    private FacturaNegociada buildFactura(int numAprobaciones) {
        List<AprobacionActorDto> aprobaciones = new ArrayList<>();
        for (int i = 0; i < numAprobaciones; i++) {
            aprobaciones.add(new AprobacionActorDto());
        }
        return FacturaNegociada.builder()
                .numeroFactura("FAC-001")
                .aprobaciones(aprobaciones)
                .build();
    }

    private PendingOtp buildPending() {
        return PendingOtp.builder()
                .otp("123456")
                .ip("192.168.1.1")
                .build();
    }

    @Test
    @DisplayName("firmarPdfEndoso asigna el PDF firmado y guarda el endoso")
    void firmarPdfEndoso_exitoso() {
        byte[] pdfOriginal = {1, 2, 3};
        byte[] pdfFirmado = {4, 5, 6};

        Endoso endoso = Endoso.builder()
                .id("end-001")
                .documentoPdf(pdfOriginal)
                .build();

        FacturaNegociada factura = buildFactura(0);
        PendingOtp pending = buildPending();

        when(endosoPort.findByNegociacionIdAndNumeroFactura("neg-001", "FAC-001"))
                .thenReturn(Optional.of(endoso));
        when(pdfFirmaPort.agregarFirma(eq(pdfOriginal), any(), eq(0)))
                .thenReturn(pdfFirmado);
        when(endosoPort.save(any())).thenReturn(endoso);

        service.firmarPdfEndoso("neg-001", factura, pending, "800111", "900222",
                RolActor.PAGADOR, true, "OK");

        verify(endosoPort).save(endosoCaptor.capture());
        assertThat(endosoCaptor.getValue().getDocumentoPdf()).isEqualTo(pdfFirmado);
    }

    @Test
    @DisplayName("firmarPdfEndoso usa el tamaño de aprobaciones como índice de firma")
    void firmarPdfEndoso_indexFirmaEsNumAprobaciones() {
        Endoso endoso = Endoso.builder()
                .id("end-001")
                .documentoPdf(new byte[]{1})
                .build();

        FacturaNegociada factura = buildFactura(3);
        PendingOtp pending = buildPending();

        when(endosoPort.findByNegociacionIdAndNumeroFactura("neg-001", "FAC-001"))
                .thenReturn(Optional.of(endoso));
        when(pdfFirmaPort.agregarFirma(any(), any(), eq(3))).thenReturn(new byte[]{9});
        when(endosoPort.save(any())).thenReturn(endoso);

        service.firmarPdfEndoso("neg-001", factura, pending, "800111", "900222",
                RolActor.FONDEADOR, false, "rechazado");

        verify(pdfFirmaPort).agregarFirma(any(), any(), eq(3));
    }

    @Test
    @DisplayName("firmarPdfEndoso lanza RuntimeException cuando el endoso no existe")
    void firmarPdfEndoso_endosoNoExiste() {
        when(endosoPort.findByNegociacionIdAndNumeroFactura("neg-X", "FAC-001"))
                .thenReturn(Optional.empty());

        FacturaNegociada factura = buildFactura(0);
        PendingOtp pending = buildPending();

        assertThatThrownBy(() -> service.firmarPdfEndoso("neg-X", factura, pending,
                "rut1", "rut2", RolActor.PROVEEDOR, true, "ok"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Endoso no encontrado");
    }

    @Test
    @DisplayName("firmarPdfEndoso construye metadatos con todos los campos correctos")
    void firmarPdfEndoso_metadatosCompletos() {
        Endoso endoso = Endoso.builder()
                .id("end-001")
                .documentoPdf(new byte[]{})
                .build();
        FacturaNegociada factura = buildFactura(1);
        PendingOtp pending = PendingOtp.builder().otp("654321").ip("10.0.0.1").build();

        when(endosoPort.findByNegociacionIdAndNumeroFactura("neg-002", "FAC-001"))
                .thenReturn(Optional.of(endoso));
        when(pdfFirmaPort.agregarFirma(any(), any(), eq(1))).thenReturn(new byte[]{});
        when(endosoPort.save(any())).thenReturn(endoso);

        service.firmarPdfEndoso("neg-002", factura, pending, "800-RUT", "900-OP",
                RolActor.FONDEADOR, false, "observacion test");

        verify(pdfFirmaPort).agregarFirma(any(),
                argThat(f -> "800-RUT".equals(f.getRutEmpresa())
                        && "654321".equals(f.getOtp())
                        && "10.0.0.1".equals(f.getIp())
                        && !f.isAprobado()
                        && "observacion test".equals(f.getObservacion())
                        && "FONDEADOR".equals(f.getRolActor())),
                eq(1));
    }

}