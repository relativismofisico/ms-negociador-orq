package com.negociadororq.application.service

import com.negociadororq.application.exception.RecursoNoEncontradoException
import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.domain.model.Factura
import com.negociadororq.application.dto.ValidacionFacturaRequestDto
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
        facturaPort.findAllByNumeroFactura("FECI001") >> [factura]

        when:
        def resultado = service.validarFacturaVencida(new ValidacionFacturaRequestDto("FECI001", null, null, null, fechaProceso))

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
        facturaPort.findAllByNumeroFactura("FECI002") >> [factura]

        when:
        def resultado = service.validarFacturaVencida(new ValidacionFacturaRequestDto("FECI002", null, null, null, fechaProceso))

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
        facturaPort.findAllByNumeroFactura("FECI003") >> [factura]

        when:
        def resultado = service.validarFacturaVencida(new ValidacionFacturaRequestDto("FECI003", null, null, null, LocalDate.now()))

        then:
        resultado.valida == false
        resultado.vencida == true
        resultado.codigo == "SIN_FECHA_VENCIMIENTO"
        resultado.mensaje == "Factura sin fecha de vencimiento"
    }

    def "factura no encontrada lanza RecursoNoEncontradoException"() {
        given:
        facturaPort.findAllByNumeroFactura("INEXISTENTE") >> []

        when:
        service.validarFacturaVencida(new ValidacionFacturaRequestDto("INEXISTENTE", null, null, null, LocalDate.now()))

        then:
        def ex = thrown(RecursoNoEncontradoException)
        ex.message.contains("INEXISTENTE")
        ex.codigo == "FACTURA_NO_ENCONTRADA"
    }

    def "cuando fechaProceso es null usa LocalDate.now()"() {
        given:
        def factura = new Factura()
        factura.fechavencimientoFactura = LocalDate.now().plusDays(20)
        facturaPort.findAllByNumeroFactura("FECI004") >> [factura]

        when:
        def resultado = service.validarFacturaVencida(new ValidacionFacturaRequestDto("FECI004", null, null, null, null))

        then:
        resultado.valida == true
        resultado.codigo == "FACTURA_VIGENTE"
    }

    def "factura exactamente en el limite de dias minimos retorna vencida"() {
        given:
        def fechaProceso = LocalDate.of(2024, 1, 1)
        def factura = new Factura()
        factura.fechavencimientoFactura = LocalDate.of(2024, 1, 11) // exactamente 10 días - NO mayor que 10
        facturaPort.findAllByNumeroFactura("FECI005") >> [factura]

        when:
        def resultado = service.validarFacturaVencida(new ValidacionFacturaRequestDto("FECI005", null, null, null, fechaProceso))

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
        facturaPort.findAllByNumeroFactura("FECI006") >> [factura]

        when:
        def resultado = service.validarFacturaVencida(new ValidacionFacturaRequestDto("FECI006", null, null, null, fechaProceso))

        then:
        resultado.valida == true
        resultado.codigo == "FACTURA_VIGENTE"
    }

    def "mensaje de vencida incluye dias restantes"() {
        given:
        def fechaProceso = LocalDate.of(2024, 1, 1)
        def factura = new Factura()
        factura.fechavencimientoFactura = LocalDate.of(2024, 1, 6) // 5 días
        facturaPort.findAllByNumeroFactura("FECI007") >> [factura]

        when:
        def resultado = service.validarFacturaVencida(new ValidacionFacturaRequestDto("FECI007", null, null, null, fechaProceso))

        then:
        resultado.mensaje.contains("5")
    }

    def "Fase 28d (C): dos facturas con el mismo número sin RUT ni CUFE => FACTURA_AMBIGUA; con el RUT o el CUFE, la correcta"() {
        given:
        def a = new com.negociadororq.domain.model.Factura(numeroFactura: "1", cufe: "CUFE-A", rutProveedor: "PROV-A",
                rutPagador: "PAG", fechavencimientoFactura: LocalDate.of(2030, 1, 1))
        def b = new com.negociadororq.domain.model.Factura(numeroFactura: "1", cufe: "CUFE-B", rutProveedor: "PROV-B",
                rutPagador: "PAG", fechavencimientoFactura: LocalDate.of(2026, 1, 1))
        facturaPort.findAllByNumeroFactura("1") >> [a, b]
        facturaPort.findByCufe("CUFE-B") >> Optional.of(b)

        when:
        service.validarFacturaVencida(new ValidacionFacturaRequestDto("1", null, null, null, LocalDate.of(2026, 6, 1)))

        then:
        def ex = thrown(com.negociadororq.application.exception.ConflictoException)
        ex.codigo == "FACTURA_AMBIGUA"

        and: "con el RUT o el CUFE, la correcta"
        service.validarFacturaVencida(new ValidacionFacturaRequestDto("1", null, "PROV-A", null,
                LocalDate.of(2026, 6, 1))).valida
        !service.validarFacturaVencida(new ValidacionFacturaRequestDto("1", "CUFE-B", null, null,
                LocalDate.of(2026, 6, 1))).valida
    }

    def "Fase 28d (C): CUFE que no corresponde al número => CUFE_NO_COINCIDE"() {
        given:
        facturaPort.findByCufe("CUFE-B") >> Optional.of(new com.negociadororq.domain.model.Factura(numeroFactura: "2",
                cufe: "CUFE-B", rutProveedor: "PROV-B"))

        when:
        service.validarFacturaVencida(new ValidacionFacturaRequestDto("1", "CUFE-B", null, null, null))

        then:
        def ex = thrown(com.negociadororq.application.exception.PeticionInvalidaException)
        ex.codigo == "CUFE_NO_COINCIDE"
    }
}
