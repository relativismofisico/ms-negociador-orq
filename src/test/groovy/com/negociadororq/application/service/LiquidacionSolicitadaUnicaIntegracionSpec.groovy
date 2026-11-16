package com.negociadororq.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.EndosoOutputPort
import com.negociadororq.application.port.output.EndosoPatchOutputPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.FacturaPatchOutputPort
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort
import com.negociadororq.application.port.output.NegociacionOutputPort
import com.negociadororq.application.port.output.PendingOtpOutputPort
import com.negociadororq.application.port.output.UserOutputPort
import com.negociadororq.domain.enums.EstadoFirma
import com.negociadororq.domain.enums.RolActor
import com.negociadororq.domain.model.AprobacionActorDto
import com.negociadororq.domain.model.AprobacionFacturaRequest
import com.negociadororq.domain.model.AprobacionRequest
import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.FondeadorAsignacion
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import com.negociadororq.domain.model.PendingOtp
import com.negociadororq.infrastructure.persistence.adapter.OutboxAdapter
import com.negociadororq.infrastructure.persistence.repository.OutboxEventRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory
import org.testcontainers.containers.MongoDBContainer
import spock.lang.Shared
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDateTime

/**
 * Fase 28d (2.5, hueco de 2b): la solicitud de liquidación va al outbox viejo (Mongo) y la marca de "ya emitido" a la
 * solicitud de OTP en otro guardado. Si el proceso muere entre los dos, el reintento (reconciliador o reentrega)
 * relee la negociación, que todavía no tiene la aprobación (se guarda al final), y vuelve a escribir el evento.
 * Prueba con Mongo 7 real: muerte justo al guardar la marca y 3 reintentos => una sola solicitud de liquidación.
 */
class LiquidacionSolicitadaUnicaIntegracionSpec extends Specification {

    static final String NEG = "neg-liq-1"
    static final String FAC = "FC-LIQ-1"
    static final String TOPIC_LIQUIDACION = "topic-liquidacion"

    @Shared
    MongoDBContainer mongoDb = new MongoDBContainer("mongo:7.0").withCreateContainerCmdModifier {
        it.withName("integrity-e2e-tc-mongo-" + UUID.randomUUID().toString().substring(0, 8))
    }
    @Shared
    MongoClient cliente
    @Shared
    MongoTemplate mongo
    @Shared
    OutboxService outbox

    ClockPort clock = Stub() { now() >> LocalDateTime.of(2026, 11, 16, 10, 0) }
    Negociacion guardada
    boolean morirAlMarcarLiquidacion = true

    /** La negociación como está en Mongo: sin la aprobación del Fondeador hasta que un intento llega al final. */
    NegociacionOutputPort negociacionPort = Stub() {
        findById(NEG) >> { Optional.of(guardada ?: negociacionInicial()) }
        save(_) >> { Negociacion n -> guardada = n; n }
    }
    PendingOtpOutputPort pendingOtpPort = Stub() {
        save(_) >> { PendingOtp p ->
            if (morirAlMarcarLiquidacion && p.efectosEmitidos?.any { it.startsWith("LIQUIDACION:") }) {
                morirAlMarcarLiquidacion = false
                throw new IllegalStateException("el proceso muere entre el evento y la marca")
            }
            p
        }
    }

    def setupSpec() {
        mongoDb.start()
        cliente = MongoClients.create(mongoDb.replicaSetUrl)
        mongo = new MongoTemplate(new SimpleMongoClientDatabaseFactory(cliente, "negociador"))
        outbox = new OutboxService(new OutboxAdapter(new MongoRepositoryFactory(mongo).getRepository(OutboxEventRepository),
                new ObjectMapper().findAndRegisterModules()))
    }

    def cleanupSpec() {
        cliente?.close()
        mongoDb.stop()
    }

    def setup() {
        mongo.dropCollection("outbox_event")
    }

    static Negociacion negociacionInicial() {
        def aprobacionPagador = new AprobacionActorDto(rutActor: "PAG-1", rolActor: RolActor.PAGADOR, aprobado: true,
                observacion: "ok", fecha: LocalDateTime.of(2026, 11, 16, 9, 0), otpId: "50")
        def factura = FacturaNegociada.builder().numeroFactura(FAC).rutPagador("PAG-1").rutProveedor("PROV-1")
                .fondeadores([FondeadorAsignacion.builder().rutFondeador("FOND-1").nombreFondeador("Fondo").build()])
                .aprobaciones([aprobacionPagador]).estadoFirma(EstadoFirma.EN_APROBACION).build()
        Negociacion.builder().id(NEG).rutEmpresa("PROV-1").tipoOperacion(new Operacion("FACTORING", "CON_RESPONSABILIDAD"))
                .facturas([factura]).build()
    }

    /** La solicitud de OTP como está en Mongo: sin marcas (la del intento que murió nunca se guardó). */
    static PendingOtp pendingDelFondeador() {
        def req = new AprobacionRequest(NEG, "op-fond", "FOND-1", "FONDEADOR", [new AprobacionFacturaRequest(FAC, true, null)],
                "ok")
        PendingOtp.builder().id("p-60").idNegociacion(NEG).rutEmpresa("FOND-1").rolActor("FONDEADOR").otp("111111")
                .otpId("60").ip("10.0.0.6").fechaValidacion(Instant.parse("2026-11-16T14:59:00Z")).request(req)
                .requestId("r-60").validadoEnOrigen(true).build()
    }

    AprobacionEndosoDescuentoServiceImpl servicio() {
        FacturaOutputPort facturaPort = Stub() {
            findByNumeroFactura(FAC) >> Optional.of(new Factura(numeroFactura: FAC, rutPagador: "PAG-1", rutProveedor: "PROV-1",
                    valorFactura: 1000000G))
        }
        new AprobacionEndosoDescuentoServiceImpl(
                negociacionPort, Mock(FacturaPatchOutputPort), Mock(EndosoPatchOutputPort), outbox,
                Mock(CupoReversionService), Mock(CompanyOutputPort), Mock(FondeadorQuotaOutputPort), facturaPort,
                Stub(UserOutputPort) { findByUsername(_) >> Optional.empty() }, pendingOtpPort, Mock(FirmaEndosoService),
                Mock(NotificacionService), Stub(EndosoOutputPort) { findByNegociacionIdAndNumeroFactura(_, _) >> Optional.empty() },
                Mock(DocumentoEndosoService), clock,
                "topic-otp-request", "topic-otp-validate", TOPIC_LIQUIDACION, Mock(AuditoriaNegociacionService))
    }

    long solicitudesDeLiquidacion() {
        mongo.count(Query.query(Criteria.where("tipoEvento").is(TOPIC_LIQUIDACION)), "outbox_event")
    }

    def "el proceso muere entre escribir la solicitud de liquidación y marcarla; 3 reintentos dejan una sola solicitud"() {
        given:
        def servicio = servicio()

        when: "primer intento: escribe el evento y muere al guardar la marca"
        servicio.aprobarInterno(pendingDelFondeador())

        then:
        thrown(IllegalStateException)
        solicitudesDeLiquidacion() == 1

        when: "tres reintentos de la misma validación (reconciliador o reentrega)"
        3.times { servicio.aprobarInterno(pendingDelFondeador()) }

        then:
        solicitudesDeLiquidacion() == 1
        guardada.facturas[0].aprobaciones*.rolActor.toSet() == [RolActor.PAGADOR, RolActor.FONDEADOR].toSet()
    }

    def "sin caídas, una aprobación completa deja exactamente una solicitud de liquidación"() {
        given:
        morirAlMarcarLiquidacion = false

        when:
        servicio().aprobarInterno(pendingDelFondeador())

        then:
        solicitudesDeLiquidacion() == 1
    }
}
