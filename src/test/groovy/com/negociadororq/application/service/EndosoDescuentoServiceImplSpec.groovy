package com.negociadororq.application.service

import com.negociadororq.application.dto.EndosoRequestDto
import com.negociadororq.application.exception.PeticionInvalidaException
import com.negociadororq.application.exception.RecursoNoEncontradoException
import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.EndosoOutputPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.FacturaPatchOutputPort
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort
import com.negociadororq.application.port.output.NegociacionOutputPort
import com.negociadororq.application.port.output.NegociacionPatchOutputPort
import com.negociadororq.domain.model.Company
import com.negociadororq.domain.model.Endoso
import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.FondeadorAsignacion
import com.negociadororq.domain.model.FondeadorQuota
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
    CompanyOutputPort companyPort = Mock()
    FondeadorQuotaOutputPort fondeadorQuotaPort = Mock()

    EndosoDescuentoServiceImpl service = new EndosoDescuentoServiceImpl(
            negociacionPort, endosoPort, facturaPort, notificacionService,
            facturaPatchPort, documentoEndosoService, negociacionPatchPort,
            companyPort, fondeadorQuotaPort
    )

    // idPorNit vacío por defecto -> companyPort.findByNit devuelve Optional.empty()
    // -> permiteEndosoGrupal siempre da false (comportamiento previo: un Endoso por
    // factura). Los tests de agrupamiento completan idPorNit antes de invocar el
    // servicio. Un único closure dinámico en setup() en vez de un stub distinto por
    // test: con Spock, la primera interacción declarada sobre un mock gana, no la
    // última, así que un stub fijo acá bloquearía cualquier valor más específico
    // declarado luego en el "given:" de cada test.
    private final Map<String, Long> idPorNit = [:]

    def setup() {
        companyPort.findByNit(_ as String) >> { String nit ->
            idPorNit.containsKey(nit) ? Optional.of(Company.builder().ideCompany(idPorNit[nit]).build()) : Optional.empty()
        }
    }

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
                .rutProveedor(facturas ? facturas[0].rutProveedor : null)
                .rutPagador(facturas ? facturas[0].rutPagador : null)
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
        1 * documentoEndosoService.generarDocumentoFormal(_, _, _, _, _)
        1 * notificacionService.notificarEndosos({ it.size() == 1 })
        1 * facturaPatchPort.actualizarEstadoPorId("id-FECI001", _, _)
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

    // ─── endoso grupal ────────────────────────────────────────────────────────

    def "sin endosoGrupal habilitado, crea un Endoso por factura del mismo fondeador"() {
        given:
        def fn1 = buildNegFactoring("FECI010", "RUT-PROV")
        def fn2 = buildNegFactoring("FECI011", "RUT-PROV")
        def neg = buildNegociacion("FACTORING", [fn1, fn2])

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        facturaPort.findByNumeroFactura("FECI010") >> Optional.of(buildFactura("FECI010", "RUT-PROV"))
        facturaPort.findByNumeroFactura("FECI011") >> Optional.of(buildFactura("FECI011", "RUT-PROV"))
        endosoPort.existsByNegociacionIdAndNumeroFactura(_, _) >> false
        idPorNit["FOND-001"] = 1L
        idPorNit["RUT-PROV"] = 2L
        fondeadorQuotaPort.findByFunderIdAndSupplierId(1L, 2L) >>
                Optional.of(FondeadorQuota.builder().endosoGrupal(false).build())

        def request = EndosoRequestDto.builder()
                .idNegociacion("neg-001")
                .rutActor("RUT-PROV")
                .numerosFactura(["FECI010", "FECI011"])
                .build()

        when:
        def resultado = service.registrarEndoso(request)

        then:
        resultado.facturasEndosadas.size() == 2
        2 * endosoPort.save({ it.numerosFactura.size() == 1 })
    }

    def "con endosoGrupal habilitado, agrupa las facturas del mismo fondeador en un solo Endoso"() {
        given:
        def fn1 = buildNegFactoring("FECI012", "RUT-PROV")
        def fn2 = buildNegFactoring("FECI013", "RUT-PROV")
        def neg = buildNegociacion("FACTORING", [fn1, fn2])

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        facturaPort.findByNumeroFactura("FECI012") >> Optional.of(buildFactura("FECI012", "RUT-PROV"))
        facturaPort.findByNumeroFactura("FECI013") >> Optional.of(buildFactura("FECI013", "RUT-PROV"))
        endosoPort.existsByNegociacionIdAndNumeroFactura(_, _) >> false
        idPorNit["FOND-001"] = 1L
        idPorNit["RUT-PROV"] = 2L
        fondeadorQuotaPort.findByFunderIdAndSupplierId(1L, 2L) >>
                Optional.of(FondeadorQuota.builder().endosoGrupal(true).build())

        def request = EndosoRequestDto.builder()
                .idNegociacion("neg-001")
                .rutActor("RUT-PROV")
                .numerosFactura(["FECI012", "FECI013"])
                .build()

        when:
        def resultado = service.registrarEndoso(request)

        then:
        resultado.facturasEndosadas.size() == 2
        1 * endosoPort.save({ (it.numerosFactura as Set) == (["FECI012", "FECI013"] as Set) })
        1 * notificacionService.notificarEndosos({ it.size() == 1 })
    }

    def "endosoGrupal en Confirming se consulta por funderId+payerId"() {
        given:
        def fn1 = FacturaNegociada.builder()
                .numeroFactura("FECI014").rutProveedor("RUT-PROV").rutPagador("RUT-PAG")
                .fondeadores([FondeadorAsignacion.builder().rutFondeador("FOND-001").nombreFondeador("Fondo").build()])
                .build()
        def fn2 = FacturaNegociada.builder()
                .numeroFactura("FECI015").rutProveedor("RUT-PROV").rutPagador("RUT-PAG")
                .fondeadores([FondeadorAsignacion.builder().rutFondeador("FOND-001").nombreFondeador("Fondo").build()])
                .build()
        def neg = buildNegociacion("CONFIRMING", [fn1, fn2])

        def factura1 = buildFactura("FECI014", "RUT-PROV")
        factura1.rutPagador = "RUT-PAG"
        def factura2 = buildFactura("FECI015", "RUT-PROV")
        factura2.rutPagador = "RUT-PAG"

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        facturaPort.findByNumeroFactura("FECI014") >> Optional.of(factura1)
        facturaPort.findByNumeroFactura("FECI015") >> Optional.of(factura2)
        endosoPort.existsByNegociacionIdAndNumeroFactura(_, _) >> false
        idPorNit["FOND-001"] = 1L
        idPorNit["RUT-PAG"] = 3L
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 3L) >>
                Optional.of(FondeadorQuota.builder().endosoGrupal(true).build())

        def request = EndosoRequestDto.builder()
                .idNegociacion("neg-001")
                .rutActor("RUT-PAG")
                .numerosFactura(["FECI014", "FECI015"])
                .build()

        when:
        def resultado = service.registrarEndoso(request)

        then:
        resultado.facturasEndosadas.size() == 2
        1 * endosoPort.save({ (it.numerosFactura as Set) == (["FECI014", "FECI015"] as Set) })
    }
}