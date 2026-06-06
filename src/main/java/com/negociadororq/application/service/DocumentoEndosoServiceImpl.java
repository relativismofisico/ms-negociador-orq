package com.negociadororq.application.service;

import com.negociadororq.application.port.output.PdfGeneradorOutputPort;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Log
@Service
@RequiredArgsConstructor
public class DocumentoEndosoServiceImpl implements DocumentoEndosoService {

    private final TemplateRenderService templateRenderService;
    private final PdfGeneradorOutputPort pdfGeneradorPort;

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
                fn.getNumeroFactura(),
                fn.getRutPagador(),
                factura.getNombreProveedor(),
                factura.getRutProveedor(),
                factura.getNumeroFactura(),
                negociacion.getTipoOperacion().getTipo(),
                fn.getFondeadores().get(0).getNombreFondeador(),
                fn.getFondeadores().get(0).getRutFondeador(),
                LocalDateTime.now()
        );

        byte[] pdf = pdfGeneradorPort.generarDesdePdfHtml(html);

        endoso.setDocumentoHtml(html);
        endoso.setDocumentoPdf(pdf);
        endoso.setNombreArchivoPdf("ENDOSO_" + factura.getNumeroFactura() + ".pdf");
    }
}
