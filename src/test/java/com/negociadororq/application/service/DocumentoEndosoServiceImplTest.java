package com.negociadororq.application.service;

import com.negociadororq.application.port.output.PdfGeneradorOutputPort;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentoEndosoServiceImpl — generación de documento de endoso")
class DocumentoEndosoServiceImplTest {

    @Mock
    private TemplateRenderService templateRenderService;

    @Mock
    private PdfGeneradorOutputPort pdfGeneradorPort;

    @InjectMocks
    private DocumentoEndosoServiceImpl service;

    @Test
    @DisplayName("generarDocumentoFormal genera HTML y PDF y los asigna al endoso")
    void generarDocumentoFormal_exitoso() {
        Endoso endoso = new Endoso();
        Operacion operacion = Operacion.builder().tipo("FACTORING").clase("NORMAL").build();
        Negociacion negociacion = Negociacion.builder()
                .estado("PENDIENTE")
                .tipoOperacion(operacion)
                .build();

        Factura factura = new Factura();
        factura.setNumeroFactura("FAC-001");
        factura.setNombrePagador("Pagador S.A.");
        factura.setNombreProveedor("Proveedor S.A.");
        factura.setRutProveedor("800123456");

        FondeadorAsignacion fondeador = FondeadorAsignacion.builder()
                .nombreFondeador("Fondeador S.A.")
                .rutFondeador("700999888")
                .build();

        FacturaNegociada fn = FacturaNegociada.builder()
                .numeroFactura("FAC-001")
                .rutPagador("900987654")
                .fondeadores(List.of(fondeador))
                .build();

        when(templateRenderService.renderEndosoFormalTemplate(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("<html>endoso</html>");
        when(pdfGeneradorPort.generarDesdePdfHtml("<html>endoso</html>"))
                .thenReturn(new byte[]{1, 2, 3});

        service.generarDocumentoFormal(endoso, negociacion, factura, fn);

        assertThat(endoso.getDocumentoHtml()).isEqualTo("<html>endoso</html>");
        assertThat(endoso.getDocumentoPdf()).containsExactly(1, 2, 3);
        assertThat(endoso.getNombreArchivoPdf()).isEqualTo("ENDOSO_FAC-001.pdf");
    }

    @Test
    @DisplayName("generarDocumentoFormal llama al templateRenderService con los datos correctos")
    void generarDocumentoFormal_llamaTemplate() {
        Endoso endoso = new Endoso();
        Operacion operacion = Operacion.builder().tipo("CONFIRMING").clase("NORMAL").build();
        Negociacion negociacion = Negociacion.builder()
                .estado("EN_APROBACION")
                .tipoOperacion(operacion)
                .build();

        Factura factura = new Factura();
        factura.setNumeroFactura("FAC-002");
        factura.setNombrePagador("Corp Pagador");
        factura.setNombreProveedor("Corp Proveedor");
        factura.setRutProveedor("811222333");

        FondeadorAsignacion fondeador = FondeadorAsignacion.builder()
                .nombreFondeador("Corp Fondeador")
                .rutFondeador("700500600")
                .build();

        FacturaNegociada fn = FacturaNegociada.builder()
                .numeroFactura("FAC-002")
                .rutPagador("901234567")
                .fondeadores(List.of(fondeador))
                .build();

        when(templateRenderService.renderEndosoFormalTemplate(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn("<html/>");
        when(pdfGeneradorPort.generarDesdePdfHtml(any())).thenReturn(new byte[]{});

        service.generarDocumentoFormal(endoso, negociacion, factura, fn);

        verify(templateRenderService).renderEndosoFormalTemplate(
                eq("EN_APROBACION"),
                eq("Corp Pagador"),
                eq("FAC-002"),
                eq("901234567"),
                eq("Corp Proveedor"),
                eq("811222333"),
                eq("FAC-002"),
                eq("CONFIRMING"),
                eq("Corp Fondeador"),
                eq("700500600"),
                any()
        );
    }

    @Test
    @DisplayName("generarDocumentoFormal llama al pdfGeneradorPort con el HTML generado")
    void generarDocumentoFormal_llamaPdfPort() {
        Endoso endoso = new Endoso();
        Operacion operacion = Operacion.builder().tipo("FACTORING").clase("NORMAL").build();
        Negociacion negociacion = Negociacion.builder()
                .estado("PENDIENTE")
                .tipoOperacion(operacion)
                .build();

        Factura factura = new Factura();
        factura.setNumeroFactura("FAC-003");
        factura.setNombrePagador("PagS");
        factura.setNombreProveedor("PrvS");
        factura.setRutProveedor("800000001");

        FacturaNegociada fn = FacturaNegociada.builder()
                .numeroFactura("FAC-003")
                .rutPagador("900000002")
                .fondeadores(List.of(FondeadorAsignacion.builder()
                        .nombreFondeador("FonS").rutFondeador("700000003").build()))
                .build();

        String htmlGenerado = "<html>ENDOSO FORMAL</html>";
        when(templateRenderService.renderEndosoFormalTemplate(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(htmlGenerado);
        when(pdfGeneradorPort.generarDesdePdfHtml(htmlGenerado)).thenReturn(new byte[]{99});

        service.generarDocumentoFormal(endoso, negociacion, factura, fn);

        verify(pdfGeneradorPort).generarDesdePdfHtml(htmlGenerado);
    }
}