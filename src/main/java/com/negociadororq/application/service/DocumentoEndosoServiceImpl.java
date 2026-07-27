package com.negociadororq.application.service;

import com.negociadororq.application.port.output.PdfGeneradorOutputPort;
import com.negociadororq.application.port.output.PersonOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.domain.model.DatosPersona;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Log
@Service
@RequiredArgsConstructor
public class DocumentoEndosoServiceImpl implements DocumentoEndosoService {

    private final TemplateRenderService templateRenderService;
    private final PdfGeneradorOutputPort pdfGeneradorPort;
    private final UserOutputPort userPort;
    private final PersonOutputPort personPort;

    @Override
    public void generarDocumentoFormal(
            Endoso endoso,
            Negociacion negociacion,
            List<Factura> facturas,
            List<FacturaNegociada> facturasNegociadas,
            String rutOperadorFirmante
    ) {
        Factura primera = facturas.get(0);
        FacturaNegociada primeraNegociada = facturasNegociadas.get(0);

        List<TemplateRenderService.FacturaEndosoTemplateData> facturasData = facturas.stream()
                .map(f -> new TemplateRenderService.FacturaEndosoTemplateData(f.getNumeroFactura(), f.getValorFactura()))
                .toList();

        TemplateRenderService.DatosFirmante firmante = resolverFirmante(rutOperadorFirmante);

        String html = templateRenderService.renderEndosoFormalTemplate(
                primera.getNombrePagador(),
                primeraNegociada.getRutPagador(),
                primera.getNombreProveedor(),
                primera.getRutProveedor(),
                facturasData,
                negociacion.getTipoOperacion().getTipo(),
                negociacion.getTipoOperacion().getClase(),
                primeraNegociada.getFondeadores().get(0).getNombreFondeador(),
                primeraNegociada.getFondeadores().get(0).getRutFondeador(),
                LocalDateTime.now(),
                firmante
        );

        byte[] pdf = pdfGeneradorPort.generarDesdePdfHtml(html);

        String nombreArchivo = facturas.size() == 1
                ? "ENDOSO_" + primera.getNumeroFactura() + ".pdf"
                : "ENDOSO_" + endoso.getNegociacionId() + "_" + primeraNegociada.getFondeadores().get(0).getRutFondeador() + ".pdf";

        endoso.setDocumentoHtml(html);
        endoso.setDocumentoPdf(pdf);
        endoso.setNombreArchivoPdf(nombreArchivo);
    }

    private TemplateRenderService.DatosFirmante resolverFirmante(String rutOperador) {
        if (rutOperador == null || rutOperador.isBlank()) {
            return null;
        }

        return userPort.findByUsername(rutOperador)
                .map(User::getPersonIde)
                .flatMap(personPort::findById)
                .map(DocumentoEndosoServiceImpl::aDatosFirmante)
                .orElse(null);
    }

    private static TemplateRenderService.DatosFirmante aDatosFirmante(DatosPersona persona) {
        return new TemplateRenderService.DatosFirmante(
                persona.nombreCompleto(), persona.tipoDocumento(), persona.numeroDocumento());
    }
}
