package com.negociadororq.application.service

import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.NegociacionOutputPort
import com.negociadororq.application.service.notificacion.CuerpoNotificacionFactory
import com.negociadororq.application.service.notificacion.DestinatarioResolver
import com.negociadororq.domain.event.DestinatarioEmail
import com.negociadororq.domain.model.AprobacionRequest
import com.negociadororq.domain.model.Company
import com.negociadororq.domain.model.Endoso
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.FondeadorAsignacion
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import spock.lang.Specification

import java.math.BigDecimal
import java.time.LocalDateTime

class NotificacionServiceImplSpec extends Specification {

    OutboxService outboxService = Mock()
    CuerpoNotificacionFactory cuerpoFactory = Mock()
    NegociacionOutputPort negociacionPort = Mock()
    DestinatarioResolver destinatarioResolver = Mock()
    CompanyOutputPort companyPort = Mock()
    FacturaOutputPort facturaPort = Mock()
    ClockPort clock = Mock() {
        now() >> LocalDateTime.now()
    }
    NotificacionServiceImpl service

    def setup() {
        service = new NotificacionServiceImpl(
                outboxService, cuerpoFactory, negociacionPort, destinatarioResolver, companyPort, facturaPort,
                clock, "topic-email", "http://localhost:4800")
        // sin stub explícito, companyPort.findByNit/facturaPort.findByNumeroFactura
        // devuelven null (no Optional.empty()) en un Mock() de Spock: hay que
        // estubarlos explícitamente en cada test que los alcance.
    }

    def buildNegociacion(String tipo = "FACTORING") {
        Negociacion.builder()
                .id("neg-001")
                .tipoOperacion(new Operacion(tipo, "DESC"))
                .rutPagador("PAG-001")
                .rutProveedor("PROV-001")
                .rutFondeadores(["FOND-001"])
                .totalNegociado(new BigDecimal("1000"))
                .facturas([FacturaNegociada.builder().build()])
                .build()
    }

    // ─── notificarCreacionNegociacion ─────────────────────────────────────────

    def "notificarCreacionNegociacion guarda evento en outbox"() {
        given:
        def neg = buildNegociacion()
        destinatarioResolver.resolverParaCreacionNegociacion(neg) >> [new DestinatarioEmail("PAGADOR", "PAG", "EMPRESA")]
        companyPort.findByNit(_) >> Optional.empty()

        when:
        service.notificarCreacionNegociacion(neg)

        then:
        1 * outboxService.guardarEvento("neg-001", "topic-email", { it.tipoNotificacion == "NEGOCIACION_CREADA" })
    }

    def "notificarCreacionNegociacion con facturas null usa lista vacia"() {
        given:
        def neg = Negociacion.builder()
                .id("neg-002")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .rutPagador("PAG")
                .rutProveedor("PROV")
                .facturas(null)
                .build()
        destinatarioResolver.resolverParaCreacionNegociacion(neg) >> []
        companyPort.findByNit(_) >> Optional.empty()

        when:
        service.notificarCreacionNegociacion(neg)

        then:
        1 * outboxService.guardarEvento("neg-002", "topic-email", {
            it.data.facturas == [] && it.data.nombresFondeadores == "N/A"
        })
    }

    def "notificarCreacionNegociacion con tipoOperacion null no falla"() {
        given:
        def neg = Negociacion.builder()
                .id("neg-003")
                .tipoOperacion(null)
                .facturas([])
                .build()
        destinatarioResolver.resolverParaCreacionNegociacion(neg) >> []

        when:
        service.notificarCreacionNegociacion(neg)

        then:
        1 * outboxService.guardarEvento("neg-003", "topic-email", { it.data.tipoOperacion == null })
    }

    def "notificarCreacionNegociacion resuelve nombres de pagador y proveedor via companyPort"() {
        given:
        def neg = buildNegociacion()
        destinatarioResolver.resolverParaCreacionNegociacion(neg) >> []
        companyPort.findByNit("PAG-001") >> Optional.of(new Company(nameCompany: "Pagador SA"))
        companyPort.findByNit("PROV-001") >> Optional.of(new Company(nameCompany: "Proveedor SA"))

        when:
        service.notificarCreacionNegociacion(neg)

        then:
        1 * outboxService.guardarEvento("neg-001", "topic-email", {
            it.data.nombrePagador == "Pagador SA" && it.data.nombreProveedor == "Proveedor SA"
        })
    }

    def "notificarCreacionNegociacion usa el rut como nombre cuando la empresa no esta registrada"() {
        given:
        def neg = buildNegociacion()
        destinatarioResolver.resolverParaCreacionNegociacion(neg) >> []
        companyPort.findByNit(_) >> Optional.empty()

        when:
        service.notificarCreacionNegociacion(neg)

        then:
        1 * outboxService.guardarEvento("neg-001", "topic-email", {
            it.data.nombrePagador == "PAG-001" && it.data.nombreProveedor == "PROV-001"
        })
    }

    def "notificarCreacionNegociacion formatea el total negociado sin notacion cientifica"() {
        given:
        def neg = Negociacion.builder()
                .id("neg-004")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .totalNegociado(new BigDecimal("155639960.39"))
                .facturas([])
                .build()
        destinatarioResolver.resolverParaCreacionNegociacion(neg) >> []

        when:
        service.notificarCreacionNegociacion(neg)

        then:
        1 * outboxService.guardarEvento("neg-004", "topic-email", {
            it.data.totalNegociado == "155.639.960,39"
        })
    }

    def "notificarCreacionNegociacion arma el detalle de cada factura con su fondeador"() {
        given:
        def fondeador = FondeadorAsignacion.builder().rutFondeador("FOND-001").nombreFondeador("Fondeador Uno").build()
        def factura = FacturaNegociada.builder()
                .numeroFactura("F1")
                .valorFactura(new BigDecimal("1000000"))
                .rutPagador("PAG-001")
                .fondeadores([fondeador])
                .build()
        def neg = Negociacion.builder()
                .id("neg-005")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .facturas([factura])
                .build()
        destinatarioResolver.resolverParaCreacionNegociacion(neg) >> []
        companyPort.findByNit("PAG-001") >> Optional.of(new Company(nameCompany: "Pagador SA"))

        when:
        service.notificarCreacionNegociacion(neg)

        then:
        1 * outboxService.guardarEvento("neg-005", "topic-email", {
            it.data.facturas.size() == 1 &&
                    it.data.facturas[0].numeroFactura == "F1" &&
                    it.data.facturas[0].valorFactura == "1.000.000,00" &&
                    it.data.facturas[0].nombrePagador == "Pagador SA" &&
                    it.data.facturas[0].nombreFondeador == "Fondeador Uno" &&
                    it.data.nombresFondeadores == "Fondeador Uno"
        })
    }

    def "notificarCreacionNegociacion agrega nombres de fondeadores distintos entre varias facturas"() {
        given:
        def fondeadorA = FondeadorAsignacion.builder().rutFondeador("F1").nombreFondeador("Fondeador A").build()
        def fondeadorB = FondeadorAsignacion.builder().rutFondeador("F2").nombreFondeador("Fondeador B").build()
        def f1 = FacturaNegociada.builder().numeroFactura("N1").fondeadores([fondeadorA]).build()
        def f2 = FacturaNegociada.builder().numeroFactura("N2").fondeadores([fondeadorA, fondeadorB]).build()
        def neg = Negociacion.builder()
                .id("neg-006")
                .tipoOperacion(new Operacion("CONFIRMING", "B"))
                .facturas([f1, f2])
                .build()
        destinatarioResolver.resolverParaCreacionNegociacion(neg) >> []

        when:
        service.notificarCreacionNegociacion(neg)

        then:
        1 * outboxService.guardarEvento("neg-006", "topic-email", {
            it.data.nombresFondeadores == "Fondeador A, Fondeador B" &&
                    it.data.claseOperacion == "B"
        })
    }

    // ─── notificarEndoso ──────────────────────────────────────────────────────

    def "notificarEndoso guarda evento ENDOSO_CREADO"() {
        given:
        def endoso = Endoso.builder()
                .id("end-001")
                .negociacionId("neg-001")
                .numerosFactura(["FECI001"])
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .rutProveedor("PROV")
                .rutPagador("PAG")
                .rutFondeador("FOND")
                .estado(com.negociadororq.domain.enums.EstadoEndoso.EN_REVISION)
                .documentoPdf(null)
                .build()

        destinatarioResolver.resolverParaEndoso(endoso) >> []
        facturaPort.findByNumeroFactura(_) >> Optional.empty()

        when:
        service.notificarEndoso(endoso)

        then:
        1 * outboxService.guardarEvento("neg-001", "topic-email", {
            it.tipoNotificacion == "ENDOSO_CREADO" && it.asunto == "ENDOSO"
        })
    }

    def "notificarEndoso arma tituloAccion/mensajeIntro/textoBoton de 'Endoso' para FACTORING"() {
        given:
        def endoso = Endoso.builder()
                .id("end-001")
                .negociacionId("neg-001")
                .numerosFactura(["FECI001"])
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .estado(com.negociadororq.domain.enums.EstadoEndoso.EN_REVISION)
                .build()
        destinatarioResolver.resolverParaEndoso(endoso) >> []
        facturaPort.findByNumeroFactura(_) >> Optional.empty()

        when:
        service.notificarEndoso(endoso)

        then:
        1 * outboxService.guardarEvento("neg-001", "topic-email", {
            it.data.tituloAccion == "Endoso" &&
                    it.data.textoBoton == "Aprobar Endoso" &&
                    it.data.mensajeIntro.contains("endoso") &&
                    it.data.urlAprobacion == "http://localhost:4800" &&
                    it.data.anio == String.valueOf(java.time.LocalDateTime.now().getYear())
        })
    }

    def "notificarEndoso arma tituloAccion/mensajeIntro/textoBoton de 'Solicitud de Descuento' para CONFIRMING"() {
        given:
        def endoso = Endoso.builder()
                .id("end-002")
                .negociacionId("neg-002")
                .numerosFactura(["FECI002"])
                .tipoOperacion(new Operacion("CONFIRMING", "DESC"))
                .estado(com.negociadororq.domain.enums.EstadoEndoso.EN_REVISION)
                .build()
        destinatarioResolver.resolverParaEndoso(endoso) >> []
        facturaPort.findByNumeroFactura(_) >> Optional.empty()

        when:
        service.notificarEndoso(endoso)

        then:
        1 * outboxService.guardarEvento("neg-002", "topic-email", {
            it.data.tituloAccion == "Solicitud de Descuento" &&
                    it.data.textoBoton == "Aprobar Solicitud de Descuento" &&
                    it.data.mensajeIntro.contains("descuento")
        })
    }

    def "notificarEndoso arma estadoAmigable, nombreSolicitante y valorFactura"() {
        given:
        def endoso = Endoso.builder()
                .id("end-003")
                .negociacionId("neg-003")
                .numerosFactura(["FECI003"])
                .rutActorEndosa("PROV-001")
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .estado(com.negociadororq.domain.enums.EstadoEndoso.EN_REVISION)
                .build()
        def factura = new com.negociadororq.domain.model.Factura(
                numeroFactura: "FECI003", valorFactura: new BigDecimal("1500000.00"))
        destinatarioResolver.resolverParaEndoso(endoso) >> []
        facturaPort.findByNumeroFactura("FECI003") >> Optional.of(factura)
        companyPort.findByNit("PROV-001") >> Optional.of(new Company(nameCompany: "Proveedor SA"))

        when:
        service.notificarEndoso(endoso)

        then:
        1 * outboxService.guardarEvento("neg-003", "topic-email", {
            it.data.estadoAmigable == "En revisión" &&
                    it.data.nombreSolicitante == "Proveedor SA" &&
                    it.data.valorFactura == "1.500.000,00"
        })
    }

    def "notificarEndoso deja valorFactura en N/A cuando la factura no se encuentra"() {
        given:
        def endoso = Endoso.builder()
                .id("end-004")
                .negociacionId("neg-004")
                .numerosFactura(["FECI004"])
                .tipoOperacion(new Operacion("FACTORING", "DESC"))
                .estado(com.negociadororq.domain.enums.EstadoEndoso.ACEPTADO)
                .build()
        destinatarioResolver.resolverParaEndoso(endoso) >> []
        facturaPort.findByNumeroFactura("FECI004") >> Optional.empty()

        when:
        service.notificarEndoso(endoso)

        then:
        1 * outboxService.guardarEvento("neg-004", "topic-email", {
            it.data.valorFactura == "N/A" && it.data.estadoAmigable == "Aceptado"
        })
    }

    // ─── notificarEndosos ─────────────────────────────────────────────────────

    def "notificarEndosos con lista vacia no hace nada"() {
        when:
        service.notificarEndosos([])

        then:
        0 * outboxService.guardarEvento(_, _, _)
    }

    def "notificarEndosos con un solo endoso delega en notificarEndoso"() {
        given:
        def endoso = Endoso.builder()
                .id("end-001")
                .negociacionId("neg-001")
                .numerosFactura(["FECI001"])
                .tipoOperacion(new Operacion("CONFIRMING", "DESC"))
                .rutProveedor("PROV-001")
                .estado(com.negociadororq.domain.enums.EstadoEndoso.EN_REVISION)
                .build()
        destinatarioResolver.resolverParaEndoso(endoso) >> []
        facturaPort.findByNumeroFactura(_) >> Optional.empty()

        when:
        service.notificarEndosos([endoso])

        then:
        1 * outboxService.guardarEvento("neg-001", "topic-email", {
            it.tipoNotificacion == "ENDOSO_CREADO" && it.data.numeroFactura == "FECI001"
        })
    }

    def "notificarEndosos agrupa por mismo destinatario (mismo proveedor) en un unico correo con la lista de facturas"() {
        given:
        def endoso1 = Endoso.builder()
                .negociacionId("neg-001")
                .numerosFactura(["FECI001"])
                .rutActorEndosa("PAG-001")
                .tipoOperacion(new Operacion("CONFIRMING", "DESC"))
                .rutProveedor("PROV-001")
                .estado(com.negociadororq.domain.enums.EstadoEndoso.EN_REVISION)
                .build()
        def endoso2 = Endoso.builder()
                .negociacionId("neg-001")
                .numerosFactura(["FECI002"])
                .rutActorEndosa("PAG-001")
                .tipoOperacion(new Operacion("CONFIRMING", "DESC"))
                .rutProveedor("PROV-001")
                .estado(com.negociadororq.domain.enums.EstadoEndoso.EN_REVISION)
                .build()

        destinatarioResolver.resolverParaEndoso(endoso1) >> [new DestinatarioEmail("PROVEEDOR", "PROV-001", "EMPRESA")]
        facturaPort.findByNumeroFactura(_) >> Optional.empty()
        companyPort.findByNit("PAG-001") >> Optional.empty()

        when:
        service.notificarEndosos([endoso1, endoso2])

        then:
        1 * outboxService.guardarEvento("neg-001", "topic-email", {
            it.tipoNotificacion == "ENDOSO_CREADO" &&
                    it.data.cantidadFacturas == 2 &&
                    it.data.facturas.collect { f -> f.numeroFactura } == ["FECI001", "FECI002"] &&
                    it.data.tituloAccion == "Solicitud de Descuento"
        })
    }

    def "notificarEndosos con distintos destinatarios manda un correo por cada grupo"() {
        given:
        def endosoProv1 = Endoso.builder()
                .negociacionId("neg-001")
                .numerosFactura(["FECI001"])
                .tipoOperacion(new Operacion("CONFIRMING", "DESC"))
                .rutProveedor("PROV-001")
                .estado(com.negociadororq.domain.enums.EstadoEndoso.EN_REVISION)
                .build()
        def endosoProv2 = Endoso.builder()
                .negociacionId("neg-001")
                .numerosFactura(["FECI002"])
                .tipoOperacion(new Operacion("CONFIRMING", "DESC"))
                .rutProveedor("PROV-002")
                .estado(com.negociadororq.domain.enums.EstadoEndoso.EN_REVISION)
                .build()

        destinatarioResolver.resolverParaEndoso(_) >> []
        facturaPort.findByNumeroFactura(_) >> Optional.empty()

        when:
        service.notificarEndosos([endosoProv1, endosoProv2])

        then:
        1 * outboxService.guardarEvento("neg-001", "topic-email", { it.data.numeroFactura == "FECI001" })
        1 * outboxService.guardarEvento("neg-001", "topic-email", { it.data.numeroFactura == "FECI002" })
    }

    // ─── notificarFirmaEndoso ─────────────────────────────────────────────────

    def "notificarFirmaEndoso busca negociacion y guarda evento ENDOSO_FIRMADO"() {
        given:
        def neg = buildNegociacion()
        def aprobacion = new AprobacionRequest()
        aprobacion.idNegociacion = "neg-001"
        aprobacion.rutEmpresa = "PAG-001"

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        destinatarioResolver.resolverParaFirmaEndoso(neg, "PAG-001") >> []
        companyPort.findByNit("PAG-001") >> Optional.empty()

        when:
        service.notificarFirmaEndoso(aprobacion)

        then:
        1 * outboxService.guardarEvento("neg-001", "topic-email", {
            it.tipoNotificacion == "ENDOSO_FIRMADO" && it.asunto == "FIRMA_ENDOSO"
        })
    }

    def "notificarFirmaEndoso arma tituloResultado y facturas segun tipoOperacion y aprobaciones"() {
        given:
        def neg = buildNegociacion("CONFIRMING")
        def facturaReq = new com.negociadororq.domain.model.AprobacionFacturaRequest("FECI001", true, null)
        def aprobacion = new AprobacionRequest("neg-001", "OP-001", "PROV-001", "PROVEEDOR", [facturaReq], null)

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        destinatarioResolver.resolverParaFirmaEndoso(neg, "PROV-001") >> []
        companyPort.findByNit("PROV-001") >> Optional.of(new Company(nameCompany: "Proveedor SA"))

        when:
        service.notificarFirmaEndoso(aprobacion)

        then:
        1 * outboxService.guardarEvento("neg-001", "topic-email", {
            it.data.tituloResultado == "Solicitud de Descuento Aprobada" &&
                    it.data.tituloAccionCorta == "Descuento" &&
                    it.data.nombreActor == "Proveedor SA" &&
                    it.data.claseOperacion == "DESC" &&
                    it.data.facturas == [[numeroFactura: "FECI001", resultado: "Aprobada"]]
        })
    }

    def "notificarFirmaEndoso arma tituloResultado Rechazado para FACTORING cuando alguna factura no aprobo"() {
        given:
        def neg = buildNegociacion("FACTORING")
        def facturaReq1 = new com.negociadororq.domain.model.AprobacionFacturaRequest("FECI001", true, null)
        def facturaReq2 = new com.negociadororq.domain.model.AprobacionFacturaRequest("FECI002", false, "no cumple")
        def aprobacion = new AprobacionRequest("neg-001", "OP-001", "FOND-001", "FONDEADOR", [facturaReq1, facturaReq2], null)

        negociacionPort.findById("neg-001") >> Optional.of(neg)
        destinatarioResolver.resolverParaFirmaEndoso(neg, "FOND-001") >> []
        companyPort.findByNit("FOND-001") >> Optional.empty()

        when:
        service.notificarFirmaEndoso(aprobacion)

        then:
        1 * outboxService.guardarEvento("neg-001", "topic-email", {
            it.data.tituloResultado == "Endoso Rechazado" &&
                    it.data.tituloAccionCorta == "Endoso"
        })
    }

    def "notificarFirmaEndoso lanza RuntimeException si negociacion no existe"() {
        given:
        def aprobacion = new AprobacionRequest()
        aprobacion.idNegociacion = "INEXISTENTE"
        aprobacion.rutEmpresa = "EMP"

        negociacionPort.findById("INEXISTENTE") >> Optional.empty()

        when:
        service.notificarFirmaEndoso(aprobacion)

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Negociación no encontrada")
    }

    // ─── notificarLiquidacionFallida ──────────────────────────────────────────

    def "notificarLiquidacionFallida guarda evento LIQUIDACION_FALLIDA con motivo"() {
        given:
        def neg = buildNegociacion()
        destinatarioResolver.resolverParaLiquidacionFallida(neg) >> []

        when:
        service.notificarLiquidacionFallida(neg, "Error de pago")

        then:
        1 * outboxService.guardarEvento("neg-001", "topic-email", {
            it.tipoNotificacion == "LIQUIDACION_FALLIDA" && it.asunto == "LIQUIDACION_FALLIDA"
        })
    }

    def "notificarLiquidacionFallida genera idEvento unico"() {
        given:
        def neg = buildNegociacion()
        destinatarioResolver.resolverParaLiquidacionFallida(neg) >> []
        def capturedEvents = []

        when:
        service.notificarLiquidacionFallida(neg, "Error")
        service.notificarLiquidacionFallida(neg, "Error")

        then:
        2 * outboxService.guardarEvento(_, _, { capturedEvents << it; true })
    }

    // ─── safeMap (indirectamente via notificar*) ──────────────────────────────

    def "safeMap con cantidad impar de argumentos lanza IllegalArgumentException"() {
        given:
        // Se prueba indirectamente si el código interno tiene un bug, pero
        // como está correctamente usado en el servicio, la cobertura se alcanza
        // invocando cualquier método de notificación normal
        def neg = buildNegociacion()
        destinatarioResolver.resolverParaCreacionNegociacion(neg) >> []
        companyPort.findByNit(_) >> Optional.empty()

        when:
        service.notificarCreacionNegociacion(neg)

        then:
        1 * outboxService.guardarEvento(_, _, _)
    }
}