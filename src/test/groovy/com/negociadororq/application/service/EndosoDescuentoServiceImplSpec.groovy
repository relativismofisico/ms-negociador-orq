package com.negociadororq.application.service

import com.negociadororq.application.dto.EndosoRequestDto
import com.negociadororq.application.exception.PeticionInvalidaException
import com.negociadororq.application.exception.RecursoNoEncontradoException
import com.negociadororq.application.port.output.EndosoOutputPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.FacturaPatchOutputPort
import com.negociadororq.application.port.output.NegociacionOutputPort
import com.negociadororq.application.port.output.NegociacionPatchOutputPort
import com.negociadororq.domain.model.Endoso
import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.FondeadorAsignacion
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import spock.lang.Specification

import java.math.BigDecimal

class EndosoDescuentoServiceImplSpec extends Specification {

    NegociacionOutputPort negociacionPort = Mock()
    EndosoOutputPort endosoPort = Mock()
    FacturaOutputPort facturaPort = Mock()
    NotificacionService notificacionService = Mock()
    FacturaPatchOutputPort facturaPatchPort = Mock()
    DocumentoEndosoService documentoEndosoService = Mock()
    NegociacionPatchOutputPort negociacionPatchPort = Mock()

    EndosoDescuentoServiceImpl service = new EndosoDescuentoServiceImpl(
            negociacionPort, endosoPort, facturaPort, notificacionService,
            facturaPatchPort, documentoEndosoService, negociacionPatchPort
    )

    def buildNegFactoring(String numero, String rutProveedor) {
        FacturaNegociada.builder()
                .numeroFactura(numero)
                .rutProveedor(rutProveedor)
                .rutPagador("PAG-001")
                .fondeadores([FondeadorAsignacion.builder().rutFondeador("FOND-001").nombreFondeador("Fondo").build()])
                .build()
    }

    def buildFactura(String numero, String rutProveedor) {
        def f = new Factura()
        f.id = "id-" + numero
        f.numeroFactura = numero
        f.rutProveedor = rutProveedor
        f.rutPagador = "PAG-001"
        return f
    }

    def buildNegociacion(String tipo, List<FacturaNegociada> facturas) {
        Negociacion.builder()
                .id("neg-001")
                .tipoOperacion(new Operacion(tipo, "DESC"))
                .facturas(facturas)
                .build()
    }

    // ─── flujo exitoso FACTORING ─────────────────────────────────────────────

    def "registrarEndoso FACTORING flujo exitoso crea endoso y retorna PENDIENTE"() {
        given:
        def fn = buildNegFactoring("FECI001", "RUT-PROV")
        def neg = buildNegociacion("FACTORING", [fn])
        def factura = buildFactura("FECI001", "RUT-PROV")

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        facturaPort.findByNumeroFactura("FECI001") >> Optional.of(factura)
        endosoPort.existsByNegociacionIdAndNumeroFactura("neg-001", "FECI001") >> false

        def request = EndosoRequestDto.builder()
                .idNegociacion("neg-001")
                .rutActor("RUT-PROV")
                .numerosFactura(["FECI001"])
                .build()

        when:
        def resultado = service.registrarEndoso(request)

        then:
        resultado.estadoEndoso == "PENDIENTE"
        resultado.facturasEndosadas.contains("FECI001")
        resultado.facturasRechazadas.isEmpty()
        1 * endosoPort.save(_)
        1 * documentoEndosoService.generarDocumentoFormal(_, _, _, _)
        1 * notificacionService.notificarEndoso(_)
        1 * facturaPatchPort.actualizarEstadoPorId("id-FECI001", _)
        1 * negociacionPatchPort.actualizarEstadoEndosoPorId("neg-001", "FECI001", true)
    }

    def "registrarEndoso CONFIRMING flujo exitoso"() {
        given:
        def fn = FacturaNegociada.builder()
                .numeroFactura("FECI002")
                .rutPagador("RUT-PAG")
                .rutProveedor("RUT-PROV")
                .fondeadores([FondeadorAsignacion.builder().rutFondeador("FOND").nombreFondeador("F").build()])
                .build()
        def neg = buildNegociacion("CONFIRMING", [fn])
        def factura = buildFactura("FECI002", "RUT-PROV")
        factura.rutPagador = "RUT-PAG"

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        facturaPort.findByNumeroFactura("FECI002") >> Optional.of(factura)
        endosoPort.existsByNegociacionIdAndNumeroFactura("neg-001", "FECI002") >> false

        def request = EndosoRequestDto.builder()
                .idNegociacion("neg-001")
                .rutActor("RUT-PAG")
                .numerosFactura(["FECI002"])
                .build()

        when:
        def resultado = service.registrarEndoso(request)

        then:
        resultado.estadoEndoso == "PENDIENTE"
        resultado.facturasEndosadas.contains("FECI002")
    }

    // ─── negociacion no encontrada ────────────────────────────────────────────

    def "registrarEndoso lanza RecursoNoEncontradoException si negociacion no existe"() {
        given:
        negociacionPort.findById("NEG_NOPE") >> Optional.empty()

        def request = EndosoRequestDto.builder()
                .idNegociacion("NEG_NOPE")
                .rutActor("RUT")
                .numerosFactura(["F001"])
                .build()

        when:
        service.registrarEndoso(request)

        then:
        def ex = thrown(RecursoNoEncontradoException)
        ex.codigo == "NEGOCIACION_NO_ENCONTRADA"
    }

    // ─── facturas nulas o vacias ──────────────────────────────────────────────

    def "registrarEndoso lanza PeticionInvalidaException si numerosFactura es null"() {
        given:
        def neg = buildNegociacion("FACTORING", [])
        negociacionPort.findById("neg-001") >> Optional.of(neg)

        def request = EndosoRequestDto.builder()
                .idNegociacion("neg-001")
                .rutActor("RUT")
                .numerosFactura(null)
                .build()

        when:
        service.registrarEndoso(request)

        then:
        def ex = thrown(PeticionInvalidaException)
        ex.codigo == "FACTURAS_REQUERIDAS"
    }

    def "registrarEndoso lanza PeticionInvalidaException si numerosFactura es vacia"() {
        given:
        def neg = buildNegociacion("FACTORING", [])
        negociacionPort.findById("neg-001") >> Optional.of(neg)

        def request = EndosoRequestDto.builder()
                .idNegociacion("neg-001")
                .rutActor("RUT")
                .numerosFactura([])
                .build()

        when:
        service.registrarEndoso(request)

        then:
        thrown(PeticionInvalidaException)
    }

    // ─── factura no pertenece al actor ────────────────────────────────────────

    def "factura que no pertenece al proveedor es rechazada en FACTORING"() {
        given:
        def fn = buildNegFactoring("FECI003", "OTRO-PROV")
        def neg = buildNegociacion("FACTORING", [fn])

        negociacionPort.findById("neg-001") >> Optional.of(neg)

        def request = EndosoRequestDto.builder()
                .idNegociacion("neg-001")
                .rutActor("RUT-PROV-INCORRECTO")
                .numerosFactura(["FECI003"])
                .build()

        when:
        def resultado = service.registrarEndoso(request)

        then:
        resultado.estadoEndoso == "RECHAZADO"
        resultado.facturasRechazadas.any { it.contains("FECI003") }
        resultado.facturasEndosadas.isEmpty()
        0 * endosoPort.save(_)
    }

    def "factura que no existe en BD es rechazada"() {
        given:
        def fn = buildNegFactoring("FECI004", "RUT-PROV")
        def neg = buildNegociacion("FACTORING", [fn])

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        facturaPort.findByNumeroFactura("FECI004") >> Optional.empty()

        def request = EndosoRequestDto.builder()
                .idNegociacion("neg-001")
                .rutActor("RUT-PROV")
                .numerosFactura(["FECI004"])
                .build()

        when:
        def resultado = service.registrarEndoso(request)

        then:
        resultado.estadoEndoso == "RECHAZADO"
        resultado.facturasRechazadas.any { it.contains("no existe en BD") }
    }

    def "factura ya endosada es rechazada"() {
        given:
        def fn = buildNegFactoring("FECI005", "RUT-PROV")
        def neg = buildNegociacion("FACTORING", [fn])
        def factura = buildFactura("FECI005", "RUT-PROV")

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        facturaPort.findByNumeroFactura("FECI005") >> Optional.of(factura)
        endosoPort.existsByNegociacionIdAndNumeroFactura("neg-001", "FECI005") >> true

        def request = EndosoRequestDto.builder()
                .idNegociacion("neg-001")
                .rutActor("RUT-PROV")
                .numerosFactura(["FECI005"])
                .build()

        when:
        def resultado = service.registrarEndoso(request)

        then:
        resultado.estadoEndoso == "RECHAZADO"
        resultado.facturasRechazadas.any { it.contains("ya tiene endoso") }
        0 * endosoPort.save(_)
    }

    // ─── estadoGlobal PARCIAL ─────────────────────────────────────────────────

    def "estadoGlobal es PARCIAL cuando hay facturas aceptadas y rechazadas"() {
        given:
        def fn1 = buildNegFactoring("FECI006", "RUT-PROV")
        def fn2 = buildNegFactoring("FECI007", "RUT-PROV")
        def neg = buildNegociacion("FACTORING", [fn1, fn2])

        def factura1 = buildFactura("FECI006", "RUT-PROV")
        def factura2 = buildFactura("FECI007", "RUT-PROV")

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        facturaPort.findByNumeroFactura("FECI006") >> Optional.of(factura1)
        facturaPort.findByNumeroFactura("FECI007") >> Optional.of(factura2)
        endosoPort.existsByNegociacionIdAndNumeroFactura("neg-001", "FECI006") >> false
        endosoPort.existsByNegociacionIdAndNumeroFactura("neg-001", "FECI007") >> true // ya endosada

        def request = EndosoRequestDto.builder()
                .idNegociacion("neg-001")
                .rutActor("RUT-PROV")
                .numerosFactura(["FECI006", "FECI007"])
                .build()

        when:
        def resultado = service.registrarEndoso(request)

        then:
        resultado.estadoEndoso == "PARCIAL"
        resultado.facturasEndosadas.contains("FECI006")
        resultado.facturasRechazadas.any { it.contains("FECI007") }
    }

    // ─── tipo operacion desconocido ───────────────────────────────────────────

    def "tipo operacion desconocido rechaza factura con actor desconocido"() {
        given:
        def fn = buildNegFactoring("FECI008", "RUT-PROV")
        def neg = buildNegociacion("OTRO_TIPO", [fn])

        negociacionPort.findById("neg-001") >> Optional.of(neg)

        def request = EndosoRequestDto.builder()
                .idNegociacion("neg-001")
                .rutActor("RUT-PROV")
                .numerosFactura(["FECI008"])
                .build()

        when:
        def resultado = service.registrarEndoso(request)

        then:
        resultado.estadoEndoso == "RECHAZADO"
    }
}