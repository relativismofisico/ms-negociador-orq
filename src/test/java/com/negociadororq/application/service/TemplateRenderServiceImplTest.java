package com.negociadororq.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TemplateRenderServiceImpl — generación de HTML para endoso formal")
class TemplateRenderServiceImplTest {

    private final TemplateRenderServiceImpl service = new TemplateRenderServiceImpl();

    private final LocalDateTime fecha = LocalDateTime.of(2026, 6, 6, 10, 30);

    @Test
    @DisplayName("renderEndosoFormalTemplate retorna HTML no nulo y no vacío")
    void render_retornaHtmlNoVacio() {
        String html = service.renderEndosoFormalTemplate(
                "Bogotá", "Pag S.A.", "Calle 1", "Bogotá",
                "Prov S.A.", "800123456", "FAC-001", "FACTORING",
                "Fond S.A.", "700999888", fecha);

        assertThat(html).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("HTML contiene el número de factura")
    void render_contieneNumeroFactura() {
        String html = service.renderEndosoFormalTemplate(
                "Medellín", "PagadorX", "Cra 10", "Medellín",
                "ProveedorX", "900111222", "FAC-XYZ-999", "CONFIRMING",
                "FondeadorX", "600555333", fecha);

        assertThat(html).contains("FAC-XYZ-999");
    }

    @Test
    @DisplayName("HTML contiene tipo de operación")
    void render_contieneTipoOperacion() {
        String html = service.renderEndosoFormalTemplate(
                "Cali", "Pag", "Calle 5", "Cali",
                "Prov", "800999111", "FAC-999", "FACTORING",
                "Fond", "700111000", fecha);

        assertThat(html).contains("FACTORING");
    }

    @Test
    @DisplayName("HTML contiene nombre del fondeador")
    void render_contieneFondeador() {
        String html = service.renderEndosoFormalTemplate(
                "Bucaramanga", "PagadorCorp", "Av 8", "Bucaramanga",
                "ProveedorCorp", "811000222", "FAC-000", "CONFIRMING",
                "Financiero ABC", "700500600", fecha);

        assertThat(html).contains("Financiero ABC");
    }

    @Test
    @DisplayName("HTML es válido XHTML (contiene declaración DOCTYPE y html)")
    void render_esHtmlValido() {
        String html = service.renderEndosoFormalTemplate(
                "Cartagena", "Pagador", "Dir 1", "Cartagena",
                "Proveedor", "800100200", "FAC-111", "FACTORING",
                "Fondeador", "700300400", fecha);

        assertThat(html)
                .contains("<!DOCTYPE html>")
                .contains("<html")
                .contains("</html>");
    }

    @Test
    @DisplayName("HTML contiene NIT del proveedor")
    void render_contieneNitProveedor() {
        String html = service.renderEndosoFormalTemplate(
                "Barranquilla", "PagNombre", "Cl 99", "Barranquilla",
                "PrvNombre", "NIT-ESPECIAL-987", "FAC-A01", "FACTORING",
                "FondNombre", "700001002", fecha);

        assertThat(html).contains("NIT-ESPECIAL-987");
    }

    @Test
    @DisplayName("renderEndosoFormalTemplate acepta valores nulos sin lanzar NullPointerException")
    void render_conParametrosNulos() {
        String html = service.renderEndosoFormalTemplate(
                null, null, null, null, null, null, null, null, null, null, null);

        assertThat(html).isNotNull();
    }
}