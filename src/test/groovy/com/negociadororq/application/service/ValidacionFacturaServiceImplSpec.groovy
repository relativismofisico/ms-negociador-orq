package com.negociadororq.application.service

import com.negociadororq.application.exception.RecursoNoEncontradoException
import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.domain.model.Factura
import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime

class ValidacionFacturaServiceImplSpec extends Specification {

    FacturaOutputPort facturaPort = Mock()
    ClockPort clock = Mock() {
        now() >> LocalDateTime.now()
    }
    // 10L: mismo default que tenía @Value("${negociacion.dias-minimos-vencimiento:10}")
    ValidacionFacturaServiceImpl service = new ValidacionFacturaServiceImpl(facturaPort, clock, 10L)

    def "factura vigente con dias suficientes retorna valida=true"() {
        given:
        def fechaProceso = LocalDate.of(2024, 1, 1)
        def factura = new Factura()
        factura.fechavencimientoFactura = LocalDate.of(2024, 2, 1) // 31 días después
        facturaPort.findByNumeroFactura("FECI001") >> Optional.of(factura)

        when:
        def resultado = service.validarFacturaVencida("FECI001", fechaProceso)

        then:
        resultado.valida == true
        resultado.vencida == false
        resultado.codigo == "FACTURA_VIGENTE"
        resultado.diasHastaVencimiento == 31
        resultado.mensaje.contains("Factura vigente")
    }

    def "factura vencida retorna valida=false"() {
        given:
        def fechaProceso = LocalDate.of(2024, 2, 1)
        def factura = new Factura()
        factura.fechavencimientoFactura = LocalDate.of(2024, 2, 5) // solo 4 días
        facturaPort.findByNumeroFactura("FECI002") >> Optional.of(factura)

        when:
        def resultado = service.validarFacturaVencida("FECI002", fechaProceso)

        then:
        resultado.valida == false
        resultado.vencida == true
        resultado.codigo == "FACTURA_VENCIDA"
        resultado.diasHastaVencimiento == 4
        resultado.mensaje.contains("no negociable")
    }

    def "factura sin fecha de vencimiento retorna SIN_FECHA_VENCIMIENTO"() {
        given:
        def factura = new Factura()
        factura.fechavencimientoFactura = null
        facturaPort.findByNumeroFactura("FECI003") >> Optional.of(factura)

        when:
        def resultado = service.validarFacturaVencida("FECI003", LocalDate.now())

        then:
        resultado.valida == false
        resultado.vencida == true
        resultado.codigo == "SIN_FECHA_VENCIMIENTO"
        resultado.mensaje == "Factura sin fecha de vencimiento"
    }

    def "factura no encontrada lanza RecursoNoEncontradoException"() {
        given:
        facturaPort.findByNumeroFactura("INEXISTENTE") >> Optional.empty()

        when:
        service.validarFacturaVencida("INEXISTENTE", LocalDate.now())

        then:
        def ex = thrown(RecursoNoEncontradoException)
        ex.message.contains("INEXISTENTE")
        ex.codigo == "FACTURA_NO_ENCONTRADA"
    }

    def "cuando fechaProceso es null usa LocalDate.now()"() {
        given:
        def factura = new Factura()
        factura.fechavencimientoFactura = LocalDate.now().plusDays(20)
        facturaPort.findByNumeroFactura("FECI004") >> Optional.of(factura)

        when:
        def resultado = service.validarFacturaVencida("FECI004", null)

        then:
        resultado.valida == true
        resultado.codigo == "FACTURA_VIGENTE"
    }

    def "factura exactamente en el limite de dias minimos retorna vencida"() {
        given:
        def fechaProceso = LocalDate.of(2024, 1, 1)
        def factura = new Factura()
        factura.fechavencimientoFactura = LocalDate.of(2024, 1, 11) // exactamente 10 días - NO mayor que 10
        facturaPort.findByNumeroFactura("FECI005") >> Optional.of(factura)

        when:
        def resultado = service.validarFacturaVencida("FECI005", fechaProceso)

        then:
        resultado.valida == false // días == 10 no es > 10
        resultado.vencida == true
        resultado.codigo == "FACTURA_VENCIDA"
    }

    def "factura con diasMinimosVencimiento en 11 dias queda vigente"() {
        given:
        def fechaProceso = LocalDate.of(2024, 1, 1)
        def factura = new Factura()
        factura.fechavencimientoFactura = LocalDate.of(2024, 1, 12) // 11 días -> > 10 -> válida
        facturaPort.findByNumeroFactura("FECI006") >> Optional.of(factura)

        when:
        def resultado = service.validarFacturaVencida("FECI006", fechaProceso)

        then:
        resultado.valida == true
        resultado.codigo == "FACTURA_VIGENTE"
    }

    def "mensaje de vencida incluye dias restantes"() {
        given:
        def fechaProceso = LocalDate.of(2024, 1, 1)
        def factura = new Factura()
        factura.fechavencimientoFactura = LocalDate.of(2024, 1, 6) // 5 días
        facturaPort.findByNumeroFactura("FECI007") >> Optional.of(factura)

        when:
        def resultado = service.validarFacturaVencida("FECI007", fechaProceso)

        then:
        resultado.mensaje.contains("5")
    }
}