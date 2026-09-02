package com.negociadororq.application.service

import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.PdfGeneradorOutputPort
import com.negociadororq.application.port.output.PersonOutputPort
import com.negociadororq.application.port.output.UserOutputPort
import com.negociadororq.domain.model.DatosPersona
import com.negociadororq.domain.model.Endoso
import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.FondeadorAsignacion
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import com.negociadororq.domain.model.User
import spock.lang.Specification

import java.time.LocalDateTime

class DocumentoEndosoServiceImplSpec extends Specification {

    TemplateRenderService templateRenderService = Mock()
    PdfGeneradorOutputPort pdfGeneradorPort = Mock()
    UserOutputPort userPort = Mock()
    PersonOutputPort personPort = Mock()
    ClockPort clock = Mock() {
        now() >> LocalDateTime.now()
    }
    DocumentoEndosoServiceImpl service =
            new DocumentoEndosoServiceImpl(templateRenderService, pdfGeneradorPort, userPort, personPort, clock)

    private static Factura factura(String numero, String nombrePagador = "Pagador SA", String rutProveedor = "900000001", String nombreProveedor = "Proveedor Ltda") {
        def f = new Factura()
        f.nombrePagador = nombrePagador
        f.rutProveedor = rutProveedor
        f.nombreProveedor = nombreProveedor
        f.numeroFactura = numero
        return f
    }

    private static FacturaNegociada facturaNegociada(String numero, String rutPagador = "800000001", String nombreFondeador = "FondoCapital", String rutFondeador = "700000001") {
        return FacturaNegociada.builder()
                .numeroFactura(numero)
                .rutPagador(rutPagador)
                .fondeadores([FondeadorAsignacion.builder()
                        .nombreFondeador(nombreFondeador)
                        .rutFondeador(rutFondeador)
                        .build()])
                .build()
    }

    def "generarDocumentoFormal invoca render y generacion de PDF y setea campos en endoso"() {
        given:
        def endoso = new Endoso()
        def negociacion = Negociacion.builder()
                .estado("PENDIENTE")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .build()
        def facturas = [factura("FECI001")]
        def facturasNegociadas = [facturaNegociada("FECI001")]

        def htmlEsperado = "<html>Endoso</html>"
        def pdfEsperado = "PDF-BYTES".bytes

        templateRenderService.renderEndosoFormalTemplate(*_) >> htmlEsperado
        pdfGeneradorPort.generarDesdePdfHtml(htmlEsperado) >> pdfEsperado

        when:
        service.generarDocumentoFormal(endoso, negociacion, facturas, facturasNegociadas, null)

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
                .tipoOperacion(new Operacion("CONFIRMING", "TRADICIONAL"))
                .build()
        def facturas = [factura("FACT-XYZ", "EMPRESA PAGADORA", "NIT-PROV", "EMPRESA PROVEEDOR")]
        def facturasNegociadas = [facturaNegociada("FACT-XYZ", "NIT-PAG", "FONDO PRINCIPAL", "NIT-FOND")]

        pdfGeneradorPort.generarDesdePdfHtml(_) >> new byte[0]

        when:
        service.generarDocumentoFormal(endoso, negociacion, facturas, facturasNegociadas, null)

        then:
        1 * templateRenderService.renderEndosoFormalTemplate(
                "EMPRESA PAGADORA",
                "NIT-PAG",
                "EMPRESA PROVEEDOR",
                "NIT-PROV",
                { List<TemplateRenderService.FacturaEndosoTemplateData> lista ->
                    lista.size() == 1 && lista[0].numeroFactura() == "FACT-XYZ"
                },
                "CONFIRMING",
                "TRADICIONAL",
                "FONDO PRINCIPAL",
                "NIT-FOND",
                _,
                null
        ) >> "<html/>"
    }

    def "generarDocumentoFormal nombre archivo incluye numero de factura cuando hay una sola"() {
        given:
        def endoso = new Endoso()
        def neg = Negociacion.builder()
                .estado("PENDIENTE")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .build()
        def facturas = [factura("MI-FACTURA-123", "PAG", "RUT", "PROV")]
        def facturasNegociadas = [facturaNegociada("MI-FACTURA-123", "RUT-PAG", "F", "RF")]

        templateRenderService.renderEndosoFormalTemplate(*_) >> "<html/>"
        pdfGeneradorPort.generarDesdePdfHtml(_) >> new byte[0]

        when:
        service.generarDocumentoFormal(endoso, neg, facturas, facturasNegociadas, null)

        then:
        endoso.nombreArchivoPdf == "ENDOSO_MI-FACTURA-123.pdf"
    }

    def "generarDocumentoFormal nombre archivo agrupa por negociacion y fondeador cuando hay varias facturas"() {
        given:
        def endoso = new Endoso()
        endoso.negociacionId = "NEG-1"
        def neg = Negociacion.builder()
                .estado("PENDIENTE")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .build()
        def facturas = [factura("F1"), factura("F2")]
        def facturasNegociadas = [facturaNegociada("F1", "800000001", "FondoCapital", "700000001"),
                                   facturaNegociada("F2", "800000001", "FondoCapital", "700000001")]

        templateRenderService.renderEndosoFormalTemplate(*_) >> "<html/>"
        pdfGeneradorPort.generarDesdePdfHtml(_) >> new byte[0]

        when:
        service.generarDocumentoFormal(endoso, neg, facturas, facturasNegociadas, null)

        then:
        endoso.nombreArchivoPdf == "ENDOSO_NEG-1_700000001.pdf"
    }

    def "generarDocumentoFormal resuelve el firmante cuando se indica rutOperador y existe el usuario y la persona"() {
        given:
        def endoso = new Endoso()
        def neg = Negociacion.builder()
                .estado("PENDIENTE")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .build()
        def facturas = [factura("F1")]
        def facturasNegociadas = [facturaNegociada("F1")]

        userPort.findByUsername("26028790") >> Optional.of(User.builder().ideUser(1L).personIde(55L).build())
        personPort.findById(55L) >> Optional.of(new DatosPersona("Ana Gomez", "CC", "999888"))
        pdfGeneradorPort.generarDesdePdfHtml(_) >> new byte[0]

        when:
        service.generarDocumentoFormal(endoso, neg, facturas, facturasNegociadas, "26028790")

        then:
        1 * templateRenderService.renderEndosoFormalTemplate(
                _, _, _, _, _, _, _, _, _, _,
                { TemplateRenderService.DatosFirmante f ->
                    f != null && f.nombreCompleto() == "Ana Gomez" && f.numeroDocumento() == "999888"
                }
        ) >> "<html/>"
    }

    def "generarDocumentoFormal no resuelve firmante cuando rutOperador es null"() {
        given:
        def endoso = new Endoso()
        def neg = Negociacion.builder()
                .estado("PENDIENTE")
                .tipoOperacion(new Operacion("CONFIRMING", "DESC"))
                .build()
        def facturas = [factura("F1")]
        def facturasNegociadas = [facturaNegociada("F1")]

        pdfGeneradorPort.generarDesdePdfHtml(_) >> new byte[0]

        when:
        service.generarDocumentoFormal(endoso, neg, facturas, facturasNegociadas, null)

        then:
        0 * userPort.findByUsername(_)
        1 * templateRenderService.renderEndosoFormalTemplate(_, _, _, _, _, _, _, _, _, _, null) >> "<html/>"
    }
}
