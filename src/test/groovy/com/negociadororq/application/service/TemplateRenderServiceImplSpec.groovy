package com.negociadororq.application.service

import spock.lang.Specification

import java.time.LocalDateTime

class TemplateRenderServiceImplSpec extends Specification {

    TemplateRenderServiceImpl service = new TemplateRenderServiceImpl()

    def "renderEndosoFormalTemplate devuelve HTML valido con todos los parametros"() {
        given:
        def fecha = LocalDateTime.of(2024, 6, 15, 10, 30)

        when:
        def html = service.renderEndosoFormalTemplate(
                "Bogotá",
                "EMPRESA PAGADORA SA",
                "Calle 123",
                "Medellín",
                "PROVEEDOR SAS",
                "900.341.104-5",
                "FECI3566",
                "FACTORING",
                "FONDEADOR SA",
                "800.000.001-2",
                fecha
        )

        then:
        html != null
        html.contains("<!DOCTYPE html>")
        html.contains("EMPRESA PAGADORA SA")
        html.contains("PROVEEDOR SAS")
        html.contains("900.341.104-5")
        html.contains("FECI3566")
        html.contains("FACTORING")
        html.contains("FONDEADOR SA")
        html.contains("800.000.001-2")
        html.contains("Bogotá")
    }

    def "renderEndosoFormalTemplate incluye tags HTML correctos"() {
        given:
        def fecha = LocalDateTime.now()

        when:
        def html = service.renderEndosoFormalTemplate(
                "Cali",
                "Pagador Corp",
                "Carrera 45",
                "Cali",
                "Prov Ltda",
                "901.123.456-7",
                "FACT-001",
                "CONFIRMING",
                "Fondo Capital",
                "802.000.100-3",
                fecha
        )

        then:
        html.contains("<html")
        html.contains("</html>")
        html.contains("<body")
        html.contains("</body>")
        html.contains("<head>")
        html.contains("</head>")
    }

    def "renderEndosoFormalTemplate contiene referencia al numero de factura"() {
        given:
        def fecha = LocalDateTime.of(2025, 1, 1, 0, 0)

        when:
        def html = service.renderEndosoFormalTemplate(
                "Barranquilla", "PagadorX", "Av 10", "Barranquilla",
                "ProveedorZ", "NIT-001", "FACTURA-XYZ", "FACTORING",
                "FondeadorW", "NIT-002", fecha
        )

        then:
        html.contains("FACTURA-XYZ")
        html.contains("NIT-001")
        html.contains("NIT-002")
    }

    def "renderEndosoFormalTemplate con valores null no lanza excepcion"() {
        given:
        def fecha = LocalDateTime.now()

        when:
        def html = service.renderEndosoFormalTemplate(
                null, null, null, null,
                null, null, null, null,
                null, null, fecha
        )

        then:
        html != null
        html.contains("<!DOCTYPE html>")
    }

    def "renderEndosoFormalTemplate contiene firma autorizada"() {
        given:
        def fecha = LocalDateTime.now()

        when:
        def html = service.renderEndosoFormalTemplate(
                "Bucaramanga", "PagB", "Cra 1", "Buca",
                "ProvB", "NIT-B", "FACT-B", "CONFIRMING",
                "FondB", "NIT-FB", fecha
        )

        then:
        html.contains("Firma Autorizada")
    }
}