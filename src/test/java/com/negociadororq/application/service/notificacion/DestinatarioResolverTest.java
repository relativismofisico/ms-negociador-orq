package com.negociadororq.application.service.notificacion;

import com.negociadororq.domain.event.DestinatarioEmail;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.Operacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DestinatarioResolver — resolución de destinatarios de notificaciones")
class DestinatarioResolverTest {

    private final DestinatarioResolver resolver = new DestinatarioResolver();

    private Negociacion buildNegociacion(String tipo, String rutPagador, String... rutFondeadores) {
        List<String> fonds = rutFondeadores.length > 0 ? List.of(rutFondeadores) : null;
        return Negociacion.builder()
                .id("NEG-001")
                .tipoOperacion(Operacion.builder().tipo(tipo).clase("NORMAL").build())
                .rutPagador(rutPagador)
                .rutProveedor("800PROV")
                .rutFondeadores(fonds)
                .facturas(List.of(FacturaNegociada.builder()
                        .rutProveedor("800PROV").build()))
                .build();
    }

    // ─── resolverParaCreacionNegociacion ──────────────────────────

    @Test
    @DisplayName("FACTORING: incluye pagador y fondeadores")
    void creacionFactoring_incluyePagadorYFondeadores() {
        Negociacion neg = buildNegociacion("FACTORING", "900PAG", "700FOND1", "700FOND2");

        List<DestinatarioEmail> result = resolver.resolverParaCreacionNegociacion(neg);

        assertThat(result).hasSize(3);
        assertThat(result).anyMatch(d -> "PAGADOR".equals(d.getRolActor()) && "900PAG".equals(d.getRutActor()));
        assertThat(result).anyMatch(d -> "FONDEADOR".equals(d.getRolActor()) && "700FOND1".equals(d.getRutActor()));
        assertThat(result).anyMatch(d -> "FONDEADOR".equals(d.getRolActor()) && "700FOND2".equals(d.getRutActor()));
    }

    @Test
    @DisplayName("FACTORING sin pagador: solo incluye fondeadores")
    void creacionFactoringSinPagador_soloFondeadores() {
        Negociacion neg = buildNegociacion("FACTORING", null, "700FOND");

        List<DestinatarioEmail> result = resolver.resolverParaCreacionNegociacion(neg);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRolActor()).isEqualTo("FONDEADOR");
    }

    @Test
    @DisplayName("FACTORING sin fondeadores: solo incluye pagador (cubre branch fondeadores==null)")
    void creacionFactoringSinFondeadores_soloPagador() {
        Negociacion neg = buildNegociacion("FACTORING", "900PAG");

        List<DestinatarioEmail> result = resolver.resolverParaCreacionNegociacion(neg);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRolActor()).isEqualTo("PAGADOR");
    }

    @Test
    @DisplayName("CONFIRMING: incluye proveedores distintos de todas las facturas")
    void creacionConfirming_incluyeProveedores() {
        Negociacion neg = Negociacion.builder()
                .id("NEG-001")
                .tipoOperacion(Operacion.builder().tipo("CONFIRMING").clase("NORMAL").build())
                .facturas(List.of(
                        FacturaNegociada.builder().rutProveedor("800PROV1").build(),
                        FacturaNegociada.builder().rutProveedor("800PROV2").build(),
                        FacturaNegociada.builder().rutProveedor("800PROV1").build()
                ))
                .build();

        List<DestinatarioEmail> result = resolver.resolverParaCreacionNegociacion(neg);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(d -> "PROVEEDOR".equals(d.getRolActor()));
    }

    // ─── resolverParaEndoso ───────────────────────────────────────

    @Test
    @DisplayName("FACTORING endoso: incluye pagador y fondeador")
    void endosoFactoring_incluyePagadorYFondeador() {
        Endoso endoso = Endoso.builder()
                .tipoOperacion(Operacion.builder().tipo("FACTORING").clase("NORMAL").build())
                .rutPagador("900PAG")
                .rutFondeador("700FOND")
                .build();

        List<DestinatarioEmail> result = resolver.resolverParaEndoso(endoso);

        assertThat(result).hasSize(2);
        assertThat(result).anyMatch(d -> "PAGADOR".equals(d.getRolActor()));
        assertThat(result).anyMatch(d -> "FONDEADOR".equals(d.getRolActor()));
    }

    @Test
    @DisplayName("CONFIRMING endoso: incluye solo proveedor")
    void endosoConfirming_incluyeProveedor() {
        Endoso endoso = Endoso.builder()
                .tipoOperacion(Operacion.builder().tipo("CONFIRMING").clase("NORMAL").build())
                .rutProveedor("800PROV")
                .build();

        List<DestinatarioEmail> result = resolver.resolverParaEndoso(endoso);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRolActor()).isEqualTo("PROVEEDOR");
    }

    // ─── resolverParaFirmaEndoso ──────────────────────────────────

    @Test
    @DisplayName("CONFIRMING firma: incluye pagador")
    void firmaConfirming_incluyePagador() {
        Negociacion neg = buildNegociacion("CONFIRMING", "900PAG");

        List<DestinatarioEmail> result = resolver.resolverParaFirmaEndoso(neg, "cualquier-rut");

        assertThat(result).anyMatch(d -> "PAGADOR".equals(d.getRolActor()) && "900PAG".equals(d.getRutActor()));
    }

    @Test
    @DisplayName("FACTORING firma por pagador: incluye fondeadores y proveedores")
    void firmaFactoringPorPagador_incluyeFondeadoresYProveedores() {
        Negociacion neg = Negociacion.builder()
                .id("NEG-001")
                .tipoOperacion(Operacion.builder().tipo("FACTORING").clase("NORMAL").build())
                .rutPagador("900PAG")
                .rutFondeadores(List.of("700FOND"))
                .facturas(List.of(FacturaNegociada.builder().rutProveedor("800PROV").build()))
                .build();

        List<DestinatarioEmail> result = resolver.resolverParaFirmaEndoso(neg, "900PAG");

        assertThat(result).anyMatch(d -> "FONDEADOR".equals(d.getRolActor()));
        assertThat(result).anyMatch(d -> "PROVEEDOR".equals(d.getRolActor()));
    }

    @Test
    @DisplayName("FACTORING firma por fondeador: incluye pagador y proveedores")
    void firmaFactoringPorFondeador_incluyePagadorYProveedores() {
        Negociacion neg = Negociacion.builder()
                .id("NEG-001")
                .tipoOperacion(Operacion.builder().tipo("FACTORING").clase("NORMAL").build())
                .rutPagador("900PAG")
                .rutFondeadores(List.of("700FOND"))
                .facturas(List.of(FacturaNegociada.builder().rutProveedor("800PROV").build()))
                .build();

        List<DestinatarioEmail> result = resolver.resolverParaFirmaEndoso(neg, "700FOND");

        assertThat(result).anyMatch(d -> "PAGADOR".equals(d.getRolActor()));
        assertThat(result).anyMatch(d -> "PROVEEDOR".equals(d.getRolActor()));
    }

    @Test
    @DisplayName("FACTORING firma: fondeadores null — no agrega fondeadores (cubre branch &&)")
    void firmaFactoringConFondeadoresNull_noCrash() {
        Negociacion neg = Negociacion.builder()
                .id("NEG-001")
                .tipoOperacion(Operacion.builder().tipo("FACTORING").clase("NORMAL").build())
                .rutPagador("900PAG")
                .rutFondeadores(null)
                .facturas(List.of(FacturaNegociada.builder().rutProveedor("800PROV").build()))
                .build();

        List<DestinatarioEmail> result = resolver.resolverParaFirmaEndoso(neg, "DESCONOCIDO");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("FACTORING firma por desconocido: no agrega destinatarios")
    void firmaFactoringPorDesconocido_listaVacia() {
        Negociacion neg = Negociacion.builder()
                .id("NEG-001")
                .tipoOperacion(Operacion.builder().tipo("FACTORING").clase("NORMAL").build())
                .rutPagador("900PAG")
                .rutFondeadores(List.of("700FOND"))
                .facturas(List.of(FacturaNegociada.builder().rutProveedor("800PROV").build()))
                .build();

        List<DestinatarioEmail> result = resolver.resolverParaFirmaEndoso(neg, "DESCONOCIDO");

        assertThat(result).isEmpty();
    }

    // ─── resolverParaLiquidacionFallida ──────────────────────────

    @Test
    @DisplayName("FACTORING liquidacion fallida: incluye pagador, proveedores y fondeadores")
    void liquidacionFallidaFactoring_todos() {
        Negociacion neg = Negociacion.builder()
                .id("NEG-001")
                .tipoOperacion(Operacion.builder().tipo("FACTORING").clase("NORMAL").build())
                .rutPagador("900PAG")
                .rutFondeadores(List.of("700FOND"))
                .facturas(List.of(FacturaNegociada.builder().rutProveedor("800PROV").build()))
                .build();

        List<DestinatarioEmail> result = resolver.resolverParaLiquidacionFallida(neg);

        assertThat(result).anyMatch(d -> "PAGADOR".equals(d.getRolActor()));
        assertThat(result).anyMatch(d -> "PROVEEDOR".equals(d.getRolActor()));
        assertThat(result).anyMatch(d -> "FONDEADOR".equals(d.getRolActor()));
    }

    @Test
    @DisplayName("CONFIRMING liquidacion fallida: incluye proveedores y pagador")
    void liquidacionFallidaConfirming_proveedoresYPagador() {
        Negociacion neg = Negociacion.builder()
                .id("NEG-001")
                .tipoOperacion(Operacion.builder().tipo("CONFIRMING").clase("NORMAL").build())
                .rutPagador("900PAG")
                .facturas(List.of(FacturaNegociada.builder().rutProveedor("800PROV").build()))
                .build();

        List<DestinatarioEmail> result = resolver.resolverParaLiquidacionFallida(neg);

        assertThat(result).anyMatch(d -> "PROVEEDOR".equals(d.getRolActor()));
        assertThat(result).anyMatch(d -> "PAGADOR".equals(d.getRolActor()));
    }

    // ─── resolverParaCreacionNegociacion — tipo desconocido ───────

    @Test
    @DisplayName("Tipo desconocido: no agrega destinatarios")
    void creacionTipoDesconocido_listaVacia() {
        Negociacion neg = buildNegociacion("OTRO", "900PAG", "700FOND");

        List<DestinatarioEmail> result = resolver.resolverParaCreacionNegociacion(neg);

        assertThat(result).isEmpty();
    }

    // ─── resolverParaEndoso — branches null ───────────────────────

    @Test
    @DisplayName("FACTORING endoso sin pagador: solo fondeador")
    void endosoFactoringSinPagador_soloFondeador() {
        Endoso endoso = Endoso.builder()
                .tipoOperacion(Operacion.builder().tipo("FACTORING").clase("NORMAL").build())
                .rutPagador(null)
                .rutFondeador("700FOND")
                .build();

        List<DestinatarioEmail> result = resolver.resolverParaEndoso(endoso);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRolActor()).isEqualTo("FONDEADOR");
    }

    @Test
    @DisplayName("FACTORING endoso sin fondeador: solo pagador")
    void endosoFactoringSinFondeador_soloPagador() {
        Endoso endoso = Endoso.builder()
                .tipoOperacion(Operacion.builder().tipo("FACTORING").clase("NORMAL").build())
                .rutPagador("900PAG")
                .rutFondeador(null)
                .build();

        List<DestinatarioEmail> result = resolver.resolverParaEndoso(endoso);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRolActor()).isEqualTo("PAGADOR");
    }

    @Test
    @DisplayName("CONFIRMING endoso sin proveedor: lista vacía")
    void endosoConfirmingSinProveedor_listaVacia() {
        Endoso endoso = Endoso.builder()
                .tipoOperacion(Operacion.builder().tipo("CONFIRMING").clase("NORMAL").build())
                .rutProveedor(null)
                .build();

        List<DestinatarioEmail> result = resolver.resolverParaEndoso(endoso);

        assertThat(result).isEmpty();
    }

    // ─── resolverParaFirmaEndoso — branches null ──────────────────

    @Test
    @DisplayName("CONFIRMING firma sin pagador: lista vacía")
    void firmaConfirmingSinPagador_listaVacia() {
        Negociacion neg = buildNegociacion("CONFIRMING", null);

        List<DestinatarioEmail> result = resolver.resolverParaFirmaEndoso(neg, "RUT-X");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("FACTORING firma por pagador sin fondeadores: solo proveedores")
    void firmaFactoringPorPagadorSinFondeadores_soloProveedores() {
        Negociacion neg = Negociacion.builder()
                .id("NEG-001")
                .tipoOperacion(Operacion.builder().tipo("FACTORING").clase("NORMAL").build())
                .rutPagador("900PAG")
                .rutFondeadores(null)
                .facturas(List.of(FacturaNegociada.builder().rutProveedor("800PROV").build()))
                .build();

        List<DestinatarioEmail> result = resolver.resolverParaFirmaEndoso(neg, "900PAG");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRolActor()).isEqualTo("PROVEEDOR");
    }

    @Test
    @DisplayName("FACTORING firma por fondeador sin pagador: solo proveedores")
    void firmaFactoringPorFondeadorSinPagador_soloProveedores() {
        Negociacion neg = Negociacion.builder()
                .id("NEG-001")
                .tipoOperacion(Operacion.builder().tipo("FACTORING").clase("NORMAL").build())
                .rutPagador(null)
                .rutFondeadores(List.of("700FOND"))
                .facturas(List.of(FacturaNegociada.builder().rutProveedor("800PROV").build()))
                .build();

        List<DestinatarioEmail> result = resolver.resolverParaFirmaEndoso(neg, "700FOND");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRolActor()).isEqualTo("PROVEEDOR");
    }

    // ─── resolverParaLiquidacionFallida — branches null ──────────

    @Test
    @DisplayName("FACTORING liquidacion fallida sin pagador: solo proveedores y fondeadores")
    void liquidacionFallidaFactoringSinPagador_sinPagador() {
        Negociacion neg = Negociacion.builder()
                .id("NEG-001")
                .tipoOperacion(Operacion.builder().tipo("FACTORING").clase("NORMAL").build())
                .rutPagador(null)
                .rutFondeadores(List.of("700FOND"))
                .facturas(List.of(FacturaNegociada.builder().rutProveedor("800PROV").build()))
                .build();

        List<DestinatarioEmail> result = resolver.resolverParaLiquidacionFallida(neg);

        assertThat(result).noneMatch(d -> "PAGADOR".equals(d.getRolActor()));
        assertThat(result).anyMatch(d -> "PROVEEDOR".equals(d.getRolActor()));
        assertThat(result).anyMatch(d -> "FONDEADOR".equals(d.getRolActor()));
    }

    @Test
    @DisplayName("FACTORING liquidacion fallida sin fondeadores: solo pagador y proveedores")
    void liquidacionFallidaFactoringSinFondeadores_sinFondeadores() {
        Negociacion neg = Negociacion.builder()
                .id("NEG-001")
                .tipoOperacion(Operacion.builder().tipo("FACTORING").clase("NORMAL").build())
                .rutPagador("900PAG")
                .rutFondeadores(null)
                .facturas(List.of(FacturaNegociada.builder().rutProveedor("800PROV").build()))
                .build();

        List<DestinatarioEmail> result = resolver.resolverParaLiquidacionFallida(neg);

        assertThat(result).noneMatch(d -> "FONDEADOR".equals(d.getRolActor()));
        assertThat(result).anyMatch(d -> "PAGADOR".equals(d.getRolActor()));
    }

    @Test
    @DisplayName("CONFIRMING liquidacion fallida sin pagador: solo proveedores")
    void liquidacionFallidaConfirmingSinPagador_soloProveedores() {
        Negociacion neg = Negociacion.builder()
                .id("NEG-001")
                .tipoOperacion(Operacion.builder().tipo("CONFIRMING").clase("NORMAL").build())
                .rutPagador(null)
                .facturas(List.of(FacturaNegociada.builder().rutProveedor("800PROV").build()))
                .build();

        List<DestinatarioEmail> result = resolver.resolverParaLiquidacionFallida(neg);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRolActor()).isEqualTo("PROVEEDOR");
    }
}