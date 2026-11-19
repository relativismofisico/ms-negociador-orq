package com.negociadororq.application.service

import com.negociadororq.application.exception.AccionNoPermitidaException
import com.negociadororq.application.exception.ConflictoException
import com.negociadororq.application.exception.NegociadorException
import com.negociadororq.application.exception.PeticionInvalidaException
import com.negociadororq.application.exception.RecursoNoEncontradoException
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.FacturaNegociada
import spock.lang.Specification

/**
 * Fase 28d (C): la factura se identifica por CUFE o por número + RUT de proveedor y pagador; exactamente una, de la
 * empresa que negocia. Dos empresas con la factura "1".
 */
class ResolutorFacturaSpec extends Specification {

    private static final Factura A = new Factura(id: "a", numeroFactura: "1", cufe: "CUFE-A", rutProveedor: "900.111.111-1",
            rutPagador: "PAG-1")
    private static final Factura B = new Factura(id: "b", numeroFactura: "1", cufe: "CUFE-B", rutProveedor: "PROV-B",
            rutPagador: "PAG-1")
    private static final Factura C = new Factura(id: "c", numeroFactura: "1", cufe: "CUFE-C", rutProveedor: "PROV-B",
            rutPagador: "PAG-2")

    FacturaOutputPort facturas = Stub() {
        findAllByNumeroFactura("1") >> [A, B, C]
        findAllByNumeroFactura(_) >> []
        findByCufe("CUFE-A") >> Optional.of(A)
        findByCufe("CUFE-B") >> Optional.of(B)
        findByCufe(_) >> Optional.empty()
    }
    ResolutorFactura resolutor = new ResolutorFactura(facturas)

    static FacturaNegociada sol(String cufe, String prov, String pag) {
        FacturaNegociada.builder().numeroFactura("1").cufe(cufe).rutProveedor(prov).rutPagador(pag).build()
    }

    def "FACTORING: por número y RUT (con o sin el del proveedor, normalizado) resuelve la de la empresa (#caso)"() {
        expect:
        resolutor.resolverSolicitud(sol(null, prov, pag), "FACTORING", empresa).id == esperada

        where:
        caso                          | prov            | pag     | empresa      || esperada
        "ambos RUT"                   | "900111111-1"   | "PAG-1" | "9001111111" || "a"
        "solo el pagador"             | null            | "PAG-1" | "900111111-1"|| "a"
        "sin RUT: el de la empresa"   | null            | null    | "900111111-1"|| "a"
        "B con su pagador"            | "PROV-B"        | "PAG-2" | "PROV-B"     || "c"
    }

    def "CONFIRMING: el pagador que negocia es su propio lado"() {
        expect:
        resolutor.resolverSolicitud(sol(null, "PROV-B", null), "CONFIRMING", "PAG-2").id == "c"
    }

    def "sin poder distinguir => FACTURA_AMBIGUA (409)"() {
        when: "B negocia en CONFIRMING como PAG-1 sin mandar el proveedor: A y B son de PAG-1"
        resolutor.resolverSolicitud(sol(null, null, null), "CONFIRMING", "PAG-1")

        then:
        def ex = thrown(ConflictoException)
        ex.codigo == ResolutorFactura.FACTURA_AMBIGUA
    }

    def "ninguna coincide => FACTURA_NO_ENCONTRADA (404)"() {
        when:
        resolutor.resolverSolicitud(numero == "1" ? sol(null, "PROV-X", "PAG-1") : FacturaNegociada.builder()
                .numeroFactura(numero).build(), "FACTORING", "PROV-X")

        then:
        def ex = thrown(RecursoNoEncontradoException)
        ex.codigo == ResolutorFactura.FACTURA_NO_ENCONTRADA

        where:
        numero << ["1", "99"]
    }

    def "la factura de otra empresa => FACTURA_DE_OTRA_EMPRESA (403): B manda los RUT de A"() {
        when:
        resolutor.resolverSolicitud(sol(null, "900111111-1", "PAG-1"), "FACTORING", "PROV-B")

        then:
        def ex = thrown(AccionNoPermitidaException)
        ex.codigo == ResolutorFactura.FACTURA_DE_OTRA_EMPRESA
    }

    def "con CUFE: la factura del CUFE si el número y los RUT enviados coinciden"() {
        expect:
        resolutor.resolverSolicitud(sol("CUFE-B", "PROV-B", null), "FACTORING", "PROV-B").id == "b"
    }

    def "CUFE que no corresponde al número o a las empresas => CUFE_NO_COINCIDE (400) (#caso)"() {
        when:
        resolutor.resolverSolicitud(solicitud, "FACTORING", "PROV-B")

        then:
        def ex = thrown(PeticionInvalidaException)
        ex.codigo == ResolutorFactura.CUFE_NO_COINCIDE

        where:
        caso             | solicitud
        "RUT de A"       | sol("CUFE-B", "900111111-1", "PAG-1")
        "otro número"    | FacturaNegociada.builder().numeroFactura("2").cufe("CUFE-B").build()
        "otro pagador"   | sol("CUFE-B", null, "PAG-2")
    }

    def "CUFE de otra empresa con su número y sus RUT => FACTURA_DE_OTRA_EMPRESA"() {
        when:
        resolutor.resolverSolicitud(sol("CUFE-A", null, null), "FACTORING", "PROV-B")

        then:
        def ex = thrown(AccionNoPermitidaException)
        ex.codigo == ResolutorFactura.FACTURA_DE_OTRA_EMPRESA
    }

    def "CUFE inexistente => FACTURA_NO_ENCONTRADA"() {
        when:
        resolutor.resolverSolicitud(sol("CUFE-Z", null, null), "FACTORING", "PROV-B")

        then:
        thrown(RecursoNoEncontradoException)
    }

    def "la factura de la negociación: por CUFE y los RUT guardados; si la base ya no coincide, FACTURA_NO_COINCIDE"() {
        expect:
        resolutor.deLaNegociacion(sol("CUFE-A", "900111111-1", "PAG-1")).id == "a"

        when:
        resolutor.deLaNegociacion(enNegociacion)

        then:
        def ex = thrown(NegociadorException)
        ex.codigo == ResolutorFactura.FACTURA_NO_COINCIDE

        where:
        enNegociacion << [
                sol(null, "900111111-1", "PAG-1"),
                sol("CUFE-A", "PROV-B", "PAG-1"),
                sol("CUFE-A", "900111111-1", "PAG-2"),
                FacturaNegociada.builder().numeroFactura("2").cufe("CUFE-A").rutProveedor("900111111-1")
                        .rutPagador("PAG-1").build()]
    }

    def "referencia exacta: CUFE y RUT de la negociación; sin CUFE, FACTURA_NO_COINCIDE"() {
        expect:
        ResolutorFactura.referencia(sol("CUFE-A", "P", "Q")).cufe() == "CUFE-A"

        when:
        ResolutorFactura.referencia(sol(" ", "P", "Q"))

        then:
        def ex = thrown(ConflictoException)
        ex.codigo == ResolutorFactura.FACTURA_NO_COINCIDE
    }
}
