package com.negociadororq.application.service.notificacion

import com.negociadororq.domain.model.Endoso
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import spock.lang.Specification

class DestinatarioResolverSpec extends Specification {

    DestinatarioResolver resolver = new DestinatarioResolver()

    // ─── resolverParaCreacionNegociacion ─────────────────────────────────────

    def "FACTORING con pagador y fondeadores agrega ambos"() {
        given:
        def neg = Negociacion.builder()
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .rutPagador("RUT-PAGADOR")
                .rutFondeadores(["RUT-FONDO1", "RUT-FONDO2"])
                .facturas([])
                .build()

        when:
        def dest = resolver.resolverParaCreacionNegociacion(neg)

        then:
        dest.size() == 3
        dest.any { it.rolActor == "PAGADOR" && it.rutActor == "RUT-PAGADOR" }
        dest.any { it.rolActor == "FONDEADOR" && it.rutActor == "RUT-FONDO1" }
        dest.any { it.rolActor == "FONDEADOR" && it.rutActor == "RUT-FONDO2" }
    }

    def "FACTORING sin pagador solo agrega fondeadores"() {
        given:
        def neg = Negociacion.builder()
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .rutPagador(null)
                .rutFondeadores(["RUT-FONDO"])
                .facturas([])
                .build()

        when:
        def dest = resolver.resolverParaCreacionNegociacion(neg)

        then:
        dest.size() == 1
        dest[0].rolActor == "FONDEADOR"
    }

    def "FACTORING sin fondeadores solo agrega pagador"() {
        given:
        def neg = Negociacion.builder()
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .rutPagador("RUT-PAG")
                .rutFondeadores(null)
                .facturas([])
                .build()

        when:
        def dest = resolver.resolverParaCreacionNegociacion(neg)

        then:
        dest.size() == 1
        dest[0].rolActor == "PAGADOR"
    }

    def "CONFIRMING agrega proveedores unicos de las facturas"() {
        given:
        def f1 = FacturaNegociada.builder().rutProveedor("PROV-1").build()
        def f2 = FacturaNegociada.builder().rutProveedor("PROV-1").build() // duplicado
        def f3 = FacturaNegociada.builder().rutProveedor("PROV-2").build()

        def neg = Negociacion.builder()
                .tipoOperacion(new Operacion("CONFIRMING", "PAGO"))
                .facturas([f1, f2, f3])
                .build()

        when:
        def dest = resolver.resolverParaCreacionNegociacion(neg)

        then:
        dest.size() == 2
        dest.every { it.rolActor == "PROVEEDOR" }
        dest*.rutActor.toSet() == ["PROV-1", "PROV-2"].toSet()
    }

    // ─── resolverParaEndoso ───────────────────────────────────────────────────

    def "FACTORING endoso agrega pagador y fondeador"() {
        given:
        def endoso = Endoso.builder()
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .rutPagador("PAG-001")
                .rutFondeador("FOND-001")
                .build()

        when:
        def dest = resolver.resolverParaEndoso(endoso)

        then:
        dest.size() == 2
        dest.any { it.rolActor == "PAGADOR" }
        dest.any { it.rolActor == "FONDEADOR" }
    }

    def "FACTORING endoso sin pagador solo agrega fondeador"() {
        given:
        def endoso = Endoso.builder()
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .rutPagador(null)
                .rutFondeador("FOND-001")
                .build()

        when:
        def dest = resolver.resolverParaEndoso(endoso)

        then:
        dest.size() == 1
        dest[0].rolActor == "FONDEADOR"
    }

    def "FACTORING endoso sin fondeador solo agrega pagador"() {
        given:
        def endoso = Endoso.builder()
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .rutPagador("PAG-001")
                .rutFondeador(null)
                .build()

        when:
        def dest = resolver.resolverParaEndoso(endoso)

        then:
        dest.size() == 1
        dest[0].rolActor == "PAGADOR"
    }

    def "CONFIRMING endoso agrega proveedor"() {
        given:
        def endoso = Endoso.builder()
                .tipoOperacion(new Operacion("CONFIRMING", "PAGO"))
                .rutProveedor("PROV-001")
                .build()

        when:
        def dest = resolver.resolverParaEndoso(endoso)

        then:
        dest.size() == 1
        dest[0].rolActor == "PROVEEDOR"
        dest[0].rutActor == "PROV-001"
    }

    def "CONFIRMING endoso sin proveedor devuelve lista vacia"() {
        given:
        def endoso = Endoso.builder()
                .tipoOperacion(new Operacion("CONFIRMING", "PAGO"))
                .rutProveedor(null)
                .build()

        when:
        def dest = resolver.resolverParaEndoso(endoso)

        then:
        dest.isEmpty()
    }

    // ─── resolverParaFirmaEndoso ──────────────────────────────────────────────

    def "CONFIRMING firma agrega pagador"() {
        given:
        def neg = Negociacion.builder()
                .tipoOperacion(new Operacion("CONFIRMING", "PAGO"))
                .rutPagador("PAG-CF")
                .facturas([])
                .build()

        when:
        def dest = resolver.resolverParaFirmaEndoso(neg, "FIRMANTE")

        then:
        dest.size() == 1
        dest[0].rolActor == "PAGADOR"
        dest[0].rutActor == "PAG-CF"
    }

    def "CONFIRMING firma sin pagador devuelve lista vacia"() {
        given:
        def neg = Negociacion.builder()
                .tipoOperacion(new Operacion("CONFIRMING", "PAGO"))
                .rutPagador(null)
                .facturas([])
                .build()

        when:
        def dest = resolver.resolverParaFirmaEndoso(neg, "FIRMANTE")

        then:
        dest.isEmpty()
    }

    def "FACTORING firma por pagador agrega fondeadores y proveedores"() {
        given:
        def f1 = FacturaNegociada.builder().rutProveedor("PROV-A").build()
        def f2 = FacturaNegociada.builder().rutProveedor("PROV-B").build()

        def neg = Negociacion.builder()
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .rutPagador("PAG-FT")
                .rutFondeadores(["FOND-1", "FOND-2"])
                .facturas([f1, f2])
                .build()

        when:
        def dest = resolver.resolverParaFirmaEndoso(neg, "PAG-FT")

        then:
        dest.any { it.rolActor == "FONDEADOR" && it.rutActor == "FOND-1" }
        dest.any { it.rolActor == "FONDEADOR" && it.rutActor == "FOND-2" }
        dest.any { it.rolActor == "PROVEEDOR" && it.rutActor == "PROV-A" }
        dest.any { it.rolActor == "PROVEEDOR" && it.rutActor == "PROV-B" }
    }

    def "FACTORING firma por fondeador agrega pagador y proveedores"() {
        given:
        def f1 = FacturaNegociada.builder().rutProveedor("PROV-X").build()

        def neg = Negociacion.builder()
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .rutPagador("PAG-FT")
                .rutFondeadores(["FOND-A"])
                .facturas([f1])
                .build()

        when:
        def dest = resolver.resolverParaFirmaEndoso(neg, "FOND-A")

        then:
        dest.any { it.rolActor == "PAGADOR" && it.rutActor == "PAG-FT" }
        dest.any { it.rolActor == "PROVEEDOR" && it.rutActor == "PROV-X" }
    }

    def "FACTORING firma por fondeador sin pagador no agrega PAGADOR"() {
        given:
        def f1 = FacturaNegociada.builder().rutProveedor("PROV-X").build()

        def neg = Negociacion.builder()
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .rutPagador(null)
                .rutFondeadores(["FOND-A"])
                .facturas([f1])
                .build()

        when:
        def dest = resolver.resolverParaFirmaEndoso(neg, "FOND-A")

        then:
        !dest.any { it.rolActor == "PAGADOR" }
        dest.any { it.rolActor == "PROVEEDOR" }
    }

    def "FACTORING firma por actor desconocido devuelve lista vacia"() {
        given:
        def neg = Negociacion.builder()
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .rutPagador("PAG-FT")
                .rutFondeadores(["FOND-A"])
                .facturas([])
                .build()

        when:
        def dest = resolver.resolverParaFirmaEndoso(neg, "DESCONOCIDO")

        then:
        dest.isEmpty()
    }

    // ─── resolverParaLiquidacionFallida ──────────────────────────────────────

    def "FACTORING liquidacion fallida agrega pagador, proveedores y fondeadores"() {
        given:
        def f1 = FacturaNegociada.builder().rutProveedor("PROV-LF").build()

        def neg = Negociacion.builder()
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .rutPagador("PAG-LF")
                .rutFondeadores(["FOND-LF"])
                .facturas([f1])
                .build()

        when:
        def dest = resolver.resolverParaLiquidacionFallida(neg)

        then:
        dest.any { it.rolActor == "PAGADOR" }
        dest.any { it.rolActor == "PROVEEDOR" }
        dest.any { it.rolActor == "FONDEADOR" }
    }

    def "FACTORING liquidacion sin pagador no incluye PAGADOR"() {
        given:
        def f1 = FacturaNegociada.builder().rutProveedor("PROV-A").build()

        def neg = Negociacion.builder()
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .rutPagador(null)
                .rutFondeadores(null)
                .facturas([f1])
                .build()

        when:
        def dest = resolver.resolverParaLiquidacionFallida(neg)

        then:
        !dest.any { it.rolActor == "PAGADOR" }
        dest.any { it.rolActor == "PROVEEDOR" }
    }

    def "CONFIRMING liquidacion fallida agrega proveedores y pagador"() {
        given:
        def f1 = FacturaNegociada.builder().rutProveedor("PROV-CF").build()

        def neg = Negociacion.builder()
                .tipoOperacion(new Operacion("CONFIRMING", "PAGO"))
                .rutPagador("PAG-CF")
                .facturas([f1])
                .build()

        when:
        def dest = resolver.resolverParaLiquidacionFallida(neg)

        then:
        dest.any { it.rolActor == "PROVEEDOR" }
        dest.any { it.rolActor == "PAGADOR" }
    }

    def "CONFIRMING liquidacion sin pagador no incluye PAGADOR"() {
        given:
        def f1 = FacturaNegociada.builder().rutProveedor("PROV-CF").build()

        def neg = Negociacion.builder()
                .tipoOperacion(new Operacion("CONFIRMING", "PAGO"))
                .rutPagador(null)
                .facturas([f1])
                .build()

        when:
        def dest = resolver.resolverParaLiquidacionFallida(neg)

        then:
        !dest.any { it.rolActor == "PAGADOR" }
        dest.any { it.rolActor == "PROVEEDOR" }
    }
}