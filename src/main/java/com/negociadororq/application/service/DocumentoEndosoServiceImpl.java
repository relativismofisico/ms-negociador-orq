package com.negociadororq.application.service;

import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.io.ByteArrayOutputStream;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentoEndosoServiceImpl implements DocumentoEndosoService {

    private final TemplateRenderService templateRenderService;

    @Override
    public void generarDocumentoFormal(
            Endoso endoso,
            Negociacion negociacion,
            Factura factura,
            FacturaNegociada fn
    ) {

        String html = templateRenderService.renderEndosoFormalTemplate(
                negociacion.getEstado(),
                factura.getNombrePagador(),
                //fn.getDireccionPagador(),
                fn.getNumeroFactura(),
                //fn.getCiudadPagador(),
                fn.getRutPagador(),
                factura.getNombreProveedor(),
                factura.getRutProveedor(),
                factura.getNumeroFactura(),
                negociacion.getTipoOperacion().getTipo(),
                fn.getFondeadores().get(0).getNombreFondeador(),
                fn.getFondeadores().get(0).getRutFondeador(),
                LocalDateTime.now()
        );

        byte[] pdf = generarPdfDesdeHtml(html);

        endoso.setDocumentoHtml(html);
        endoso.setDocumentoPdf(pdf);
        endoso.setNombreArchivoPdf("ENDOSO_" + factura.getNumeroFactura() + ".pdf");
    }

    private byte[] generarPdfDesdeHtml(String html) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF de endoso", e);
        }
    }


}
