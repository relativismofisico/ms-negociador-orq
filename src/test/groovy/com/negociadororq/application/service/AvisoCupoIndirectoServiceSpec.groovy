package com.negociadororq.application.service

import java.time.LocalDateTime

import com.negociadororq.application.dto.DetalleCupoIndirectoDto
import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.CorreoTransaccionalOutputPort
import com.negociadororq.domain.event.NotificacionEmailEvent
import com.negociadororq.domain.model.Company
import spock.lang.Specification

/**
 * Fase 28d (punto 1.2c): aviso al fondeador por falta de cupo indirecto, como máximo uno por trío al día.
 */
class AvisoCupoIndirectoServiceSpec extends Specification {

    CorreoTransaccionalOutputPort correos = Mock()
    CompanyOutputPort companyPort = Stub() {
        findByNit("PROV-001") >> Optional.of(Company.builder().ideCompany(10L).nameCompany("Proveedora Tres").build())
    }
    LocalDateTime ahora = LocalDateTime.of(2026, 11, 22, 12, 0)
    ClockPort clock = Stub() { now() >> { ahora } }
    AvisoCupoIndirectoService service = new AvisoCupoIndirectoService(correos, companyPort, clock)

    DetalleCupoIndirectoDto detalle = DetalleCupoIndirectoDto.builder()
            .rutFondeador("FOND-001").fondeador("Fondo Dos").rutPagador("PAG-001").pagador("Pagadora Uno")
            .disponible(new BigDecimal("2000.00")).requerido(new BigDecimal("4000.10"))
            .faltante(new BigDecimal("2000.10")).build()

    def "el aviso va SOLO al fondeador, con los montos exactos en pesos y los nombres"() {
        when:
        service.avisar("PROV-001", detalle)

        then:
        1 * correos.registrar({ it.length() == 64 }, { NotificacionEmailEvent e ->
            e.asunto == "CUPO_INDIRECTO_INSUFICIENTE" &&
                    e.destinatarios*.rutActor == ["FOND-001"] &&
                    e.data == [nombreFondeador: "Fondo Dos", proveedor: "Proveedora Tres", pagador: "Pagadora Uno",
                               requerido: "\$4.000,10", disponible: "\$2.000,00", faltante: "\$2.000,10"]
        }) >> true
    }

    def "deduplicación diaria: el mismo trío el mismo día lleva el mismo id (el outbox no escribe otro); otro día u otro trío, otro id"() {
        given:
        def ids = []
        correos.registrar(_, _) >> { String id, NotificacionEmailEvent e -> ids << id; ids.count(id) == 1 }

        when:
        service.avisar("PROV-001", detalle)
        ahora = LocalDateTime.of(2026, 11, 22, 13, 30)
        service.avisar("PROV-001", detalle)
        ahora = LocalDateTime.of(2026, 11, 23, 12, 0)
        service.avisar("PROV-001", detalle)
        service.avisar("PROV-002", detalle)

        then:
        ids[0] == ids[1]
        ids[2] != ids[0]
        ids[3] != ids[2]
        ids.toSet().size() == 3
    }

    def "un fallo al registrar el aviso no rompe la negociación"() {
        given:
        correos.registrar(_, _) >> { throw new IllegalStateException("outbox apagado") }

        when:
        service.avisar("PROV-001", detalle)

        then:
        noExceptionThrown()
    }
}
