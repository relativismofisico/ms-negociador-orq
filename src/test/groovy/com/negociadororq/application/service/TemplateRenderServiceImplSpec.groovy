package com.negociadororq.application.service

import com.negociadororq.application.port.output.MessageTemplateOutputPort
import com.negociadororq.domain.model.MessageTemplate
import com.negociadororq.infrastructure.template.MustacheTemplateEngineAdapter
import spock.lang.Specification

import java.math.BigDecimal
import java.time.LocalDateTime

class TemplateRenderServiceImplSpec extends Specification {

    private static final String TEMPLATE_CONTENT = '''
        <!DOCTYPE html>
        <html>
        <head><title>{{tituloDocumento}}</title></head>
        <body>
        <p>{{nombrePagador}} - {{rutPagador}}</p>
        <p>{{nombreProveedor}} - {{rutProveedor}}</p>
        <p>{{tipoOperacion}} {{claseOperacion}} - {{verboOperacion}} a favor de {{nombreFondeador}} - {{rutFondeador}}</p>
        <p>{{fechaOperacion}}</p>
        <table>
        {{#facturas}}
        <tr><td>{{numeroFactura}}</td><td>{{valorFactura}}</td></tr>
        {{/facturas}}
        </table>
        <p>Cantidad: {{cantidadFacturas}}</p>
        <p>Cuenta fondeador: {{tipoCuentaFondeador}} {{numeroCuentaFondeador}}</p>
        <p>Firma Autorizada</p>
        <p>{{nombreFirmante}} - {{tipoDocumentoFirmante}} {{numeroDocumentoFirmante}}</p>
        </body>
        </html>
    '''

    MessageTemplateOutputPort messageTemplatePort = Mock()
    TemplateRenderServiceImpl service = new TemplateRenderServiceImpl(messageTemplatePort, new MustacheTemplateEngineAdapter())

    Optional<MessageTemplate> plantillaDevuelta = Optional.of(
            new MessageTemplate(id: 1L, templateName: "ENDOSO_DOCUMENTO", content: TEMPLATE_CONTENT, active: true))

    def setup() {
        messageTemplatePort.findByTemplateName("ENDOSO_DOCUMENTO") >> { plantillaDevuelta }
    }

    def factura(String numero, String valor) {
        new TemplateRenderService.FacturaEndosoTemplateData(numero, valor == null ? null : new BigDecimal(valor))
    }

    def firmante(String nombre = "Juan Perez", String tipoDoc = "CC", String numeroDoc = "123456") {
        new TemplateRenderService.DatosFirmante(nombre, tipoDoc, numeroDoc)
    }

    def "renderEndosoFormalTemplate devuelve HTML valido con todos los parametros"() {
        given:
        def fecha = LocalDateTime.of(2024, 6, 15, 10, 30)

        when:
        def html = service.renderEndosoFormalTemplate(
                "EMPRESA PAGADORA SA",
                "900.341.104-5",
                "PROVEEDOR SAS",
                "901.000.000-1",
                [factura("FECI3566", "1000000")],
                "FACTORING",
                "TRADICIONAL",
                "FONDEADOR SA",
                "800.000.001-2",
                fecha,
                firmante()
        )

        then:
        html != null
        html.contains("<!DOCTYPE html>")
        html.contains("EMPRESA PAGADORA SA")
        html.contains("PROVEEDOR SAS")
        html.contains("FECI3566")
        html.contains("FACTORING")
        html.contains("TRADICIONAL")
        html.contains("FONDEADOR SA")
        html.contains("800.000.001-2")
    }

    def "renderEndosoFormalTemplate incluye tags HTML correctos"() {
        given:
        def fecha = LocalDateTime.now()

        when:
        def html = service.renderEndosoFormalTemplate(
                "Pagador Corp", "901.123.456-7", "Prov Ltda", "902.000.000-1",
                [factura("FACT-001", "500000")], "CONFIRMING", "TRADICIONAL",
                "Fondo Capital", "802.000.100-3", fecha, firmante()
        )

        then:
        html.contains("<html")
        html.contains("</html>")
        html.contains("<body")
        html.contains("</body>")
        html.contains("<head>")
        html.contains("</head>")
    }

    def "renderEndosoFormalTemplate lista todas las facturas cuando el endoso esta agrupado"() {
        given:
        def fecha = LocalDateTime.of(2025, 1, 1, 0, 0)

        when:
        def html = service.renderEndosoFormalTemplate(
                "PagadorX", "NIT-PAG", "ProveedorZ", "NIT-001",
                [factura("FACTURA-XYZ", "100"), factura("FACTURA-ABC", "200")], "FACTORING", "TRADICIONAL",
                "FondeadorW", "NIT-002", fecha, firmante()
        )

        then:
        html.contains("FACTURA-XYZ")
        html.contains("FACTURA-ABC")
        html.contains("Cantidad: 2")
    }

    def "renderEndosoFormalTemplate con una sola factura reporta cantidad 1"() {
        given:
        def fecha = LocalDateTime.now()

        when:
        def html = service.renderEndosoFormalTemplate(
                "PagB", "NIT-PAGB", "ProvB", "NIT-B",
                [factura("FACT-B", "300")], "CONFIRMING", "TRADICIONAL",
                "FondB", "NIT-FB", fecha, firmante()
        )

        then:
        html.contains("Cantidad: 1")
        html.contains("Firma Autorizada")
    }

    def "renderEndosoFormalTemplate arma el titulo y verbo segun tipoOperacion"() {
        when:
        def htmlFactoring = service.renderEndosoFormalTemplate(
                "Pag", "NIT-P", "Prov", "NIT-Pr",
                [factura("F1", "1")], "FACTORING", "TRADICIONAL",
                "Fond", "NIT-F", LocalDateTime.now(), firmante())
        def htmlConfirming = service.renderEndosoFormalTemplate(
                "Pag", "NIT-P", "Prov", "NIT-Pr",
                [factura("F1", "1")], "CONFIRMING", "TRADICIONAL",
                "Fond", "NIT-F", LocalDateTime.now(), firmante())

        then:
        htmlFactoring.contains("<title>Endoso</title>")
        htmlFactoring.contains("ENDOSAR")
        htmlConfirming.contains("<title>Descuento</title>")
        htmlConfirming.contains("DESCONTAR")
    }

    def "renderEndosoFormalTemplate incluye los datos del firmante cuando se proveen"() {
        when:
        def html = service.renderEndosoFormalTemplate(
                "Pag", "NIT-P", "Prov", "NIT-Pr",
                [factura("F1", "1")], "FACTORING", "TRADICIONAL",
                "Fond", "NIT-F", LocalDateTime.now(), firmante("Ana Gomez", "CC", "999888"))

        then:
        html.contains("Ana Gomez")
        html.contains("999888")
    }

    def "renderEndosoFormalTemplate deja vacios los datos del firmante cuando es null (Confirming sin resolver aun)"() {
        when:
        def html = service.renderEndosoFormalTemplate(
                "Pag", "NIT-P", "Prov", "NIT-Pr",
                [factura("F1", "1")], "CONFIRMING", "TRADICIONAL",
                "Fond", "NIT-F", LocalDateTime.now(), null)

        then:
        html != null
        !html.contains("null")
    }

    def "renderEndosoFormalTemplate lanza excepcion si no hay plantilla activa"() {
        given:
        plantillaDevuelta = Optional.empty()

        when:
        service.renderEndosoFormalTemplate(
                "P", "1", "Q", "2", [factura("F1", "1")], "FACTORING", "TRADICIONAL", "R", "3",
                LocalDateTime.now(), firmante())

        then:
        thrown(IllegalStateException)
    }

    def "renderEndosoFormalTemplate lanza excepcion si la plantilla esta inactiva"() {
        given:
        plantillaDevuelta = Optional.of(
                new MessageTemplate(id: 1L, templateName: "ENDOSO_DOCUMENTO", content: TEMPLATE_CONTENT, active: false))

        when:
        service.renderEndosoFormalTemplate(
                "P", "1", "Q", "2", [factura("F1", "1")], "FACTORING", "TRADICIONAL", "R", "3",
                LocalDateTime.now(), firmante())

        then:
        thrown(IllegalStateException)
    }
}
