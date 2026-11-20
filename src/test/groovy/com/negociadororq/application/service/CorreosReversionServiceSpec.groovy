package com.negociadororq.application.service

import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.CorreoTransaccionalOutputPort
import com.negociadororq.application.port.output.NegociacionOutputPort
import com.negociadororq.domain.event.NotificacionEmailEvent
import com.negociadororq.domain.model.Company
import com.negociadororq.domain.model.DescuentoCupos
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import spock.lang.Specification

import java.time.LocalDateTime

/** Fase 28d (A2): plantillas A y B, a la empresa que inició y a su contraparte, sin CUFE ni datos internos. */
class CorreosReversionServiceSpec extends Specification {

    CorreoTransaccionalOutputPort correos = Mock()
    CompanyOutputPort companyPort = Stub() {
        findByNit("PROV") >> Optional.of(Company.builder().nit("PROV").nameCompany("Proveedor SAS").build())
        findByNit("PAG") >> Optional.of(Company.builder().nit("PAG").nameCompany("Pagador SA").build())
        findByNit(_) >> Optional.empty()
    }
    NegociacionOutputPort negociacionPort = Mock()
    ClockPort clock = Stub() { now() >> LocalDateTime.of(2026, 11, 20, 15, 0) }
    CorreosReversionService servicio = new CorreosReversionService(correos, companyPort, negociacionPort, clock)

    def "plantilla A en FACTORING: destinatarios proveedor (inició) y pagador; datos aprobados, sin CUFE; id determinista"() {
        given:
        NotificacionEmailEvent enviado = null
        String id = null

        when:
        servicio.revertida("neg-1", "CUFE-SECRETO-1", new DescuentoCupos.Contexto("FACTORING", "PROV", "PAG", "F-10"))

        then:
        1 * correos.registrar(_, _) >> { args -> id = args[0]; enviado = args[1]; true }
        id ==~ /[0-9a-f]{64}/
        id == CorreosReversionService.idEvento("neg-1", "CUFE-SECRETO-1", "A")
        enviado.idEvento == id
        enviado.tipoNotificacion == "OPERACION_REVERTIDA"
        enviado.destinatarios*.rutActor == ["PROV", "PAG"]
        enviado.destinatarios*.rolActor == ["PROVEEDOR", "PAGADOR"]
        enviado.data.tipoOperacion == "Factoring"
        enviado.data.numeroFactura == "F-10"
        enviado.data.idNegociacion == "neg-1"
        enviado.data.nombreEmpresa == "Proveedor SAS"
        enviado.data.nombreContraparte == "Pagador SA"
        enviado.data.fechaHora ==~ /\d{2}\/\d{2}\/\d{4} \d{2}:\d{2}/
        !enviado.data.values().any { it?.toString()?.contains("CUFE-SECRETO") }
        0 * negociacionPort._
    }

    def "plantilla B en CONFIRMING con el contexto de la negociación: pagador (inició) y proveedor; otro id que la A"() {
        given:
        negociacionPort.findById("neg-2") >> Optional.of(Negociacion.builder().id("neg-2").rutEmpresa("PAG")
                .tipoOperacion(new Operacion("CONFIRMING", "TRADICIONAL")).facturas([FacturaNegociada.builder()
                .numeroFactura("F-20").cufe("CUFE-2").rutProveedor("PROV").rutPagador("PAG").build()]).build())
        NotificacionEmailEvent enviado = null

        when:
        servicio.enRevision("neg-2", "CUFE-2", null)

        then:
        1 * correos.registrar(CorreosReversionService.idEvento("neg-2", "CUFE-2", "B"), _) >> { args -> enviado = args[1]; true }
        CorreosReversionService.idEvento("neg-2", "CUFE-2", "B") != CorreosReversionService.idEvento("neg-2", "CUFE-2", "A")
        enviado.tipoNotificacion == "OPERACION_EN_REVISION"
        enviado.destinatarios*.rutActor == ["PAG", "PROV"]
        enviado.data.tipoOperacion == "Confirming"
        enviado.data.nombreContraparte == "Proveedor SAS"
    }

    def "un id ya registrado no registra otro y no falla; sin contexto ni negociación no envía (alerta)"() {
        when:
        servicio.revertida("neg-3", "C3", new DescuentoCupos.Contexto("FACTORING", "X", "Y", "F"))
        servicio.revertida("neg-4", "C4", null)

        then:
        1 * correos.registrar(_, _) >> false
        1 * negociacionPort.findById("neg-4") >> Optional.empty()
        noExceptionThrown()
    }
}
