package com.negociadororq.application.service

import com.negociadororq.application.port.output.PdfGeneradorOutputPort
import com.negociadororq.domain.model.Endoso
import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.FondeadorAsignacion
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import spock.lang.Specification

class DocumentoEndosoServiceImplSpec extends Specification {

    TemplateRenderService templateRenderService = Mock()
    PdfGeneradorOutputPort pdfGeneradorPort = Mock()
    DocumentoEndosoServiceImpl service = new DocumentoEndosoServiceImpl(templateRenderService, pdfGeneradorPort)

    def "generarDocumentoFormal invoca render y generacion de PDF y setea campos en endoso"() {
        given:
        def endoso = new Endoso()
        def negociacion = Negociacion.builder()
                .estado("PENDIENTE")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .build()
        def factura = new Factura()
        factura.nombrePagador = "Pagador SA"
        factura.rutProveedor = "900000001"
        factura.nombreProveedor = "Proveedor Ltda"
        factura.numeroFactura = "FECI001"
        def fn = FacturaNegociada.builder()
                .numeroFactura("FECI001")
                .rutPagador("800000001")
                .fondeadores([FondeadorAsignacion.builder()
                        .nombreFondeador("FondoCapital")
                        .rutFondeador("700000001")
                        .build()])
                .build()

        def htmlEsperado = "<html>Endoso</html>"
        def pdfEsperado = "PDF-BYTES".bytes

        templateRenderService.renderEndosoFormalTemplate(*_) >> htmlEsperado
        pdfGeneradorPort.generarDesdePdfHtml(htmlEsperado) >> pdfEsperado

        when:
        service.generarDocumentoFormal(endoso, negociacion, factura, fn)

        then:
        endoso.documentoHtml == htmlEsperado
        endoso.documentoPdf == pdfEsperado
        endoso.nombreArchivoPdf == "ENDOSO_FECI001.pdf"
    }

    def "generarDocumentoFormal llama a templateRenderService con parametros correctos"() {
        given:
        def endoso = new Endoso()
        def negociacion = Negociacion.builder()
                .estado("ACTIVO")
                .tipoOperacion(new Operacion("CONFIRMING", "PAGO"))
                .build()
        def factura = new Factura()
        factura.nombrePagador = "EMPRESA PAGADORA"
        factura.rutProveedor = "NIT-PROV"
        factura.nombreProveedor = "EMPRESA PROVEEDOR"
        factura.numeroFactura = "FACT-XYZ"
        def fn = FacturaNegociada.builder()
                .numeroFactura("FACT-XYZ")
                .rutPagador("NIT-PAG")
                .fondeadores([FondeadorAsignacion.builder()
                        .nombreFondeador("FONDO PRINCIPAL")
                        .rutFondeador("NIT-FOND")
                        .build()])
                .build()

        pdfGeneradorPort.generarDesdePdfHtml(_) >> new byte[0]

        when:
        service.generarDocumentoFormal(endoso, negociacion, factura, fn)

        then:
        1 * templateRenderService.renderEndosoFormalTemplate(
                "ACTIVO",
                "EMPRESA PAGADORA",
                "FACT-XYZ",
                "NIT-PAG",
                "EMPRESA PROVEEDOR",
                "NIT-PROV",
                "FACT-XYZ",
                "CONFIRMING",
                "FONDO PRINCIPAL",
                "NIT-FOND",
                _
        ) >> "<html/>"
    }

    def "generarDocumentoFormal nombre archivo incluye numero de factura"() {
        given:
        def endoso = new Endoso()
        def neg = Negociacion.builder()
                .estado("PENDIENTE")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .build()
        def factura = new Factura()
        factura.nombrePagador = "PAG"
        factura.rutProveedor = "RUT"
        factura.nombreProveedor = "PROV"
        factura.numeroFactura = "MI-FACTURA-123"
        def fn = FacturaNegociada.builder()
                .numeroFactura("MI-FACTURA-123")
                .rutPagador("RUT-PAG")
                .fondeadores([FondeadorAsignacion.builder()
                        .nombreFondeador("F")
                        .rutFondeador("RF")
                        .build()])
                .build()

        templateRenderService.renderEndosoFormalTemplate(*_) >> "<html/>"
        pdfGeneradorPort.generarDesdePdfHtml(_) >> new byte[0]

        when:
        service.generarDocumentoFormal(endoso, neg, factura, fn)

        then:
        endoso.nombreArchivoPdf == "ENDOSO_MI-FACTURA-123.pdf"
    }
}