package com.negociadororq.application.service

import com.negociadororq.application.exception.ConflictoException
import com.negociadororq.application.exception.RecursoNoEncontradoException
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.FacturaPatchOutputPort
import com.negociadororq.application.port.output.NegociacionOutputPort
import com.negociadororq.domain.enums.EstadoFirma
import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.FondeadorAsignacion
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import com.negociadororq.domain.model.ReferenciaFactura
import com.negociadororq.domain.model.TipoOperacionPendiente
import spock.lang.Specification

/**
 * Liquidación fallida (Fase 28d, A2): la devolución es la exacta de lo descontado ({@link ReversionExactaService}); este
 * servicio decide cuándo revertir (guarda de estado, CUFE, factura de la negociación) y qué hacer después (firma y
 * aviso solo si esta entrega revirtió). La conservación de cupos al centavo la prueba ConservacionCuposIT (MySQL real).
 */
class ReversionCuposServiceImplSpec extends Specification {

    NegociacionOutputPort negociacionPort = Mock()
    FacturaOutputPort facturaPort = Mock()
    FacturaPatchOutputPort facturaPatchPort = Mock()
    NotificacionService notificacionService = Mock()
    ReversionExactaService reversionExacta = Mock()

    ReversionCuposServiceImpl service = new ReversionCuposServiceImpl(negociacionPort, facturaPort, facturaPatchPort,
            notificacionService, reversionExacta)

    static Negociacion negociacion(String numero = "FECI-1") {
        def fn = FacturaNegociada.builder().numeroFactura(numero).cufe("CUFE-" + numero).rutProveedor("PROV-001")
                .rutPagador("PAG-001").valorFactura(new BigDecimal("1000.00"))
                .fondeadores([FondeadorAsignacion.builder().rutFondeador("FON-001").build()]).build()
        Negociacion.builder().id("neg-001").rutEmpresa("PROV-001").tipoOperacion(new Operacion("FACTORING", "ANTICIPO"))
                .facturas([fn]).build()
    }

    static Factura factura(String numero = "FECI-1", String estado = "NEGOCIADA", String firma = "CESION") {
        new Factura(numeroFactura: numero, cufe: "CUFE-" + numero, rutProveedor: "PROV-001", rutPagador: "PAG-001",
                estadoNegociacion: estado, estadoFirma: firma)
    }

    def "esta entrega revierte: firma RECHAZADA y un aviso"() {
        given:
        negociacionPort.findById("neg-001") >> Optional.of(negociacion())
        facturaPort.findByCufe("CUFE-FECI-1") >> Optional.of(factura())

        when:
        service.revertirPorFactura("neg-001", "CUFE-FECI-1", "FECI-1", "USR-OP", "Motivo")

        then:
        1 * reversionExacta.revertir(TipoOperacionPendiente.LIQUIDACION_FALLIDA, "neg-001", "CUFE-FECI-1", "Motivo") >>
                ReversionExactaService.Resultado.REVERTIDA
        1 * facturaPatchPort.actualizarEstadoFirmaPorCufe(new ReferenciaFactura("CUFE-FECI-1", "PROV-001", "PAG-001"),
                EstadoFirma.RECHAZADA)
        1 * notificacionService.notificarLiquidacionFallida(_, "FECI-1", "Motivo")
    }

    def "ya revertida (otro camino u otra entrega): solo completa la firma, sin otro aviso"() {
        given:
        negociacionPort.findById("neg-001") >> Optional.of(negociacion())
        facturaPort.findByCufe("CUFE-FECI-1") >> Optional.of(factura())
        reversionExacta.revertir(*_) >> ReversionExactaService.Resultado.YA_REVERTIDA

        when:
        service.revertirPorFactura("neg-001", "CUFE-FECI-1", "FECI-1", "USR-OP", "Motivo")

        then:
        1 * facturaPatchPort.actualizarEstadoFirmaPorCufe(_, EstadoFirma.RECHAZADA)
        0 * notificacionService._
    }

    def "sin registro de lo descontado (REVISION_MANUAL): no marca la firma ni avisa"() {
        given:
        negociacionPort.findById("neg-001") >> Optional.of(negociacion())
        facturaPort.findByCufe("CUFE-FECI-1") >> Optional.of(factura())
        reversionExacta.revertir(*_) >> ReversionExactaService.Resultado.SIN_REGISTRO

        when:
        service.revertirPorFactura("neg-001", "CUFE-FECI-1", "FECI-1", "USR-OP", "Motivo")

        then:
        0 * facturaPatchPort._
        0 * notificacionService._
    }

    def "factura #estado / firma #firma: no revierte, no marca ni notifica"() {
        given:
        negociacionPort.findById("neg-001") >> Optional.of(negociacion())
        facturaPort.findByCufe("CUFE-FECI-1") >> Optional.of(factura("FECI-1", estado, firma))

        when:
        service.revertirPorFactura("neg-001", "CUFE-FECI-1", "FECI-1", "USR-OP", "Motivo")

        then:
        0 * reversionExacta._
        0 * facturaPatchPort._
        0 * notificacionService._

        where:
        estado      | firma
        "LIQUIDADA" | "CESION"
        "NEGOCIADA" | "RECHAZADA"
    }

    def "negociación inexistente: RecursoNoEncontrado sin revertir"() {
        given:
        negociacionPort.findById(_) >> Optional.empty()

        when:
        service.revertirPorFactura("neg-001", "CUFE-FECI-1", "FECI-1", "USR-OP", "Motivo")

        then:
        thrown(RecursoNoEncontradoException)
        0 * reversionExacta._
    }

    def "Fase 28d (C): sin CUFE o sin negociación => [EVENTO-SIN-CUFE], contador y nada se toca (#caso)"() {
        given:
        def antes = EventosSinCufe.rechazados()

        when:
        service.revertirPorFactura(neg, cufe, "FECI-X", "USR-OP", "Motivo")

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.startsWith("[EVENTO-SIN-CUFE]")
        EventosSinCufe.rechazados() == antes + 1
        0 * negociacionPort._
        0 * reversionExacta._
        0 * facturaPatchPort._

        where:
        caso              | neg       | cufe
        "sin CUFE"        | "neg-001" | null
        "CUFE vacío"      | "neg-001" | " "
        "sin negociación" | null      | "CUFE-FECI-X"
    }

    def "Fase 28d (C): un CUFE que no está en la negociación => FACTURA_NO_COINCIDE sin revertir"() {
        given:
        negociacionPort.findById("neg-001") >> Optional.of(negociacion())

        when:
        service.revertirPorFactura("neg-001", "CUFE-DE-OTRA-EMPRESA", "FECI-1", "USR-OP", "Motivo")

        then:
        def ex = thrown(ConflictoException)
        ex.codigo == ResolutorFactura.FACTURA_NO_COINCIDE
        0 * reversionExacta._
        0 * facturaPatchPort._
    }
}
