package com.negociadororq.application.service

import com.negociadororq.application.dto.NegociacionRequestDto
import com.negociadororq.application.exception.AccionNoPermitidaException
import com.negociadororq.application.exception.RecursoNoEncontradoException
import com.negociadororq.application.port.output.CompanyOperationOutputPort
import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.NegociacionOutputPort
import com.negociadororq.application.port.output.UserOutputPort
import com.negociadororq.domain.enums.EstadoNegociacion
import com.negociadororq.domain.model.Company
import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.FondeadorAsignacion
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import com.negociadororq.domain.model.User
import spock.lang.Specification

import java.math.BigDecimal

class NegociacionFactoringComfirmingServiceImplSpec extends Specification {

    FacturaOutputPort facturaPort = Mock()
    NegociacionOutputPort negociacionPort = Mock()
    UserOutputPort userPort = Mock()
    CompanyOutputPort companyPort = Mock()
    CompanyOperationOutputPort companyOperationPort = Mock()
    NotificacionService notificacionService = Mock()
    ProcesarFacturaNegociacionService procesarFacturaNegociacionService = Mock()

    NegociacionFactoringComfirmingServiceImpl service = new NegociacionFactoringComfirmingServiceImpl(
            facturaPort, negociacionPort, userPort, companyPort, companyOperationPort,
            notificacionService, procesarFacturaNegociacionService
    )

    // Controlan el comportamiento por defecto de companyPort/companyOperationPort: los tests
    // existentes (que no se centran en esta validación) no necesitan re-declarar estos stubs,
    // y los tests dedicados a la validación mutan estas variables antes del "when:" para
    // simular los casos borde (empresa inexistente / operación no habilitada), en vez de
    // declarar una segunda interacción (con Spock, la primera declarada gana, no la última).
    Boolean companyExists = true
    Boolean operationEnabled = true

    def setup() {
        companyPort.findByNit(_ as String) >> { String nit ->
            companyExists ? Optional.of(Company.builder().ideCompany(1L).nit(nit).build()) : Optional.empty()
        }
        companyOperationPort.isEnabled(_, _) >> { Long id, String type -> operationEnabled }
    }

    def buildRequest(String tipo, String rutEmpresa, String rutOp, String rolEmpresa,
                     List<FacturaNegociada> facturas) {
        NegociacionRequestDto.builder()
                .rutEmpresa(rutEmpresa)
                .rolEmpresa(rolEmpresa)
                .tipoOperacion(new Operacion(tipo, "DESC"))
                .rutOperador(rutOp)
                .facturas(facturas)
                .build()
    }

    def buildFn(String numero, String rutProv, String rutPag) {
        FacturaNegociada.builder()
                .numeroFactura(numero)
                .rutProveedor(rutProv)
                .rutPagador(rutPag)
                .valorFactura(new BigDecimal("1000.00"))
                .fondeadores([FondeadorAsignacion.builder().rutFondeador("FOND-001").nombreFondeador("F").build()])
                .build()
    }

    def buildFactura(String numero, String rutProv, String rutPag) {
        def f = new Factura()
        f.numeroFactura = numero
        f.rutProveedor = rutProv
        f.rutPagador = rutPag
        return f
    }

    // ─── operador no encontrado ───────────────────────────────────────────────

    def "crearNegociacion lanza RecursoNoEncontradoException si operador no existe"() {
        given:
        userPort.findByUsername("OP-NOPE") >> Optional.empty()

        def request = buildRequest("FACTORING", "PROV-001", "OP-NOPE", "PROVEEDOR", [])

        when:
        service.crearNegociacion(request)

        then:
        def ex = thrown(RecursoNoEncontradoException)
        ex.codigo == "OPERADOR_NO_ENCONTRADO"
    }

    // ─── flujo exitoso todas aceptadas ────────────────────────────────────────

    def "crearNegociacion con todas las facturas aceptadas -> PENDIENTE"() {
        given:
        def fn1 = buildFn("FECI001", "PROV-001", "PAG-001")
        def fn2 = buildFn("FECI002", "PROV-001", "PAG-001")

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())

        facturaPort.findByNumeroFactura("FECI001") >> Optional.of(buildFactura("FECI001", "PROV-001", "PAG-001"))
        facturaPort.findByNumeroFactura("FECI002") >> Optional.of(buildFactura("FECI002", "PROV-001", "PAG-001"))

        def fn1Procesada = buildFn("FECI001", "PROV-001", "PAG-001")
        def fn2Procesada = buildFn("FECI002", "PROV-001", "PAG-001")
        procesarFacturaNegociacionService.procesarFactura(_, fn1) >> fn1Procesada
        procesarFacturaNegociacionService.procesarFactura(_, fn2) >> fn2Procesada

        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn1, fn2])

        when:
        def resultado = service.crearNegociacion(request)

        then:
        resultado.estado == EstadoNegociacion.PENDIENTE.name()
        resultado.facturas.size() == 2
        resultado.errores.isEmpty()
        1 * negociacionPort.save(_)
        1 * notificacionService.notificarCreacionNegociacion(_)
    }

    // ─── flujo con facturas rechazadas ────────────────────────────────────────

    def "crearNegociacion con todas las facturas rechazadas -> RECHAZADA"() {
        given:
        def fn1 = buildFn("FECI003", "PROV-001", "PAG-001")

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findByNumeroFactura("FECI003") >> Optional.of(buildFactura("FECI003", "PROV-001", "PAG-001"))

        procesarFacturaNegociacionService.procesarFactura(_, fn1) >> {
            throw new RuntimeException("Factura sin cupo")
        }

        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn1])

        when:
        def resultado = service.crearNegociacion(request)

        then:
        resultado.estado == EstadoNegociacion.RECHAZADA.name()
        resultado.errores.any { it.contains("Factura sin cupo") }
        1 * negociacionPort.save(_)
    }

    // ─── flujo parcial ────────────────────────────────────────────────────────

    def "crearNegociacion con algunas aceptadas y otras rechazadas -> PENDIENTE"() {
        given:
        def fn1 = buildFn("FECI004", "PROV-001", "PAG-001")
        def fn2 = buildFn("FECI005", "PROV-001", "PAG-001")

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findByNumeroFactura("FECI004") >> Optional.of(buildFactura("FECI004", "PROV-001", "PAG-001"))
        facturaPort.findByNumeroFactura("FECI005") >> Optional.of(buildFactura("FECI005", "PROV-001", "PAG-001"))

        def fn1Procesada = buildFn("FECI004", "PROV-001", "PAG-001")
        procesarFacturaNegociacionService.procesarFactura(_, fn1) >> fn1Procesada
        procesarFacturaNegociacionService.procesarFactura(_, fn2) >> {
            throw new RuntimeException("FECI005 sin cupo")
        }

        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn1, fn2])

        when:
        def resultado = service.crearNegociacion(request)

        then:
        resultado.estado == EstadoNegociacion.PENDIENTE.name()
        resultado.facturas.size() == 1
        resultado.errores.size() == 1
    }

    // ─── rolEmpresa PROVEEDOR fija rutProveedor ────────────────────────────────

    def "crearNegociacion con rolEmpresa=PROVEEDOR fija rutProveedor desde rutEmpresa"() {
        given:
        def fn = buildFn("FECI006", "PROV-001", "PAG-001")

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findByNumeroFactura("FECI006") >> Optional.of(buildFactura("FECI006", "PROV-001", "PAG-001"))

        def procesada = buildFn("FECI006", "PROV-001", "PAG-001")
        procesarFacturaNegociacionService.procesarFactura(_, _) >> procesada

        def request = buildRequest("FACTORING", "PROV-RAIZ", "OP-001", "PROVEEDOR", [fn])

        when:
        def resultado = service.crearNegociacion(request)

        then:
        1 * negociacionPort.save({ it.rutProveedor == "PROV-RAIZ" })
    }

    // ─── rolEmpresa != PROVEEDOR usa proveedores de facturas ─────────────────

    def "crearNegociacion con rolEmpresa=EMPRESA usa rutProveedor de primera factura aceptada"() {
        given:
        def fn = buildFn("FECI007", "PROV-FACTURA", "PAG-001")

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findByNumeroFactura("FECI007") >> Optional.of(buildFactura("FECI007", "PROV-FACTURA", "PAG-001"))

        def procesada = buildFn("FECI007", "PROV-FACTURA", "PAG-001")
        procesarFacturaNegociacionService.procesarFactura(_, _) >> procesada

        def request = buildRequest("FACTORING", "EMP-001", "OP-001", "EMPRESA", [fn])

        when:
        def resultado = service.crearNegociacion(request)

        then:
        1 * negociacionPort.save({ it.rutProveedor == "PROV-FACTURA" })
    }

    // ─── un solo pagador vs multiples pagadores ───────────────────────────────

    def "crearNegociacion con un solo pagador fija rutPagador"() {
        given:
        def fn = buildFn("FECI008", "PROV-001", "PAG-UNICO")

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findByNumeroFactura("FECI008") >> Optional.of(buildFactura("FECI008", "PROV-001", "PAG-UNICO"))

        def procesada = buildFn("FECI008", "PROV-001", "PAG-UNICO")
        procesarFacturaNegociacionService.procesarFactura(_, _) >> procesada

        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn])

        when:
        service.crearNegociacion(request)

        then:
        1 * negociacionPort.save({ neg ->
            neg.rutPagador == "PAG-UNICO" && neg.rutPagadores == null
        })
    }

    def "crearNegociacion con multiples pagadores fija rutPagadores"() {
        given:
        def fn1 = buildFn("FECI009", "PROV-001", "PAG-A")
        def fn2 = buildFn("FECI010", "PROV-001", "PAG-B")

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findByNumeroFactura("FECI009") >> Optional.of(buildFactura("FECI009", "PROV-001", "PAG-A"))
        facturaPort.findByNumeroFactura("FECI010") >> Optional.of(buildFactura("FECI010", "PROV-001", "PAG-B"))

        def proc1 = buildFn("FECI009", "PROV-001", "PAG-A")
        def proc2 = buildFn("FECI010", "PROV-001", "PAG-B")
        procesarFacturaNegociacionService.procesarFactura(_, fn1) >> proc1
        procesarFacturaNegociacionService.procesarFactura(_, fn2) >> proc2

        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn1, fn2])

        when:
        service.crearNegociacion(request)

        then:
        1 * negociacionPort.save({ neg ->
            neg.rutPagador == null && neg.rutPagadores.size() == 2
        })
    }

    // ─── totalNegociado ───────────────────────────────────────────────────────

    def "crearNegociacion calcula totalNegociado correctamente"() {
        given:
        def fn1 = buildFn("FECI011", "PROV-001", "PAG-001")
        fn1.valorFactura = new BigDecimal("1000.00")
        def fn2 = buildFn("FECI012", "PROV-001", "PAG-001")
        fn2.valorFactura = new BigDecimal("2000.00")

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findByNumeroFactura("FECI011") >> Optional.of(buildFactura("FECI011", "PROV-001", "PAG-001"))
        facturaPort.findByNumeroFactura("FECI012") >> Optional.of(buildFactura("FECI012", "PROV-001", "PAG-001"))

        procesarFacturaNegociacionService.procesarFactura(_, fn1) >> fn1
        procesarFacturaNegociacionService.procesarFactura(_, fn2) >> fn2

        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn1, fn2])

        when:
        def resultado = service.crearNegociacion(request)

        then:
        resultado.totalNegociado == new BigDecimal("3000.00")
    }

    def "crearNegociacion con valorFactura null en factura usa ZERO"() {
        given:
        def fn = buildFn("FECI013", "PROV-001", "PAG-001")
        fn.valorFactura = null

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findByNumeroFactura("FECI013") >> Optional.of(buildFactura("FECI013", "PROV-001", "PAG-001"))

        procesarFacturaNegociacionService.procesarFactura(_, fn) >> fn

        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn])

        when:
        def resultado = service.crearNegociacion(request)

        then:
        resultado.totalNegociado == BigDecimal.ZERO
    }

    // ─── fondeadores distintos en respuesta ───────────────────────────────────

    def "crearNegociacion agrupa fondeadores unicos de facturas aceptadas"() {
        given:
        def fn1 = buildFn("FECI014", "PROV-001", "PAG-001")
        fn1.fondeadores = [FondeadorAsignacion.builder().rutFondeador("FOND-A").build()]
        def fn2 = buildFn("FECI015", "PROV-001", "PAG-001")
        fn2.fondeadores = [FondeadorAsignacion.builder().rutFondeador("FOND-A").build(),
                           FondeadorAsignacion.builder().rutFondeador("FOND-B").build()]

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findByNumeroFactura("FECI014") >> Optional.of(buildFactura("FECI014", "PROV-001", "PAG-001"))
        facturaPort.findByNumeroFactura("FECI015") >> Optional.of(buildFactura("FECI015", "PROV-001", "PAG-001"))

        procesarFacturaNegociacionService.procesarFactura(_, fn1) >> fn1
        procesarFacturaNegociacionService.procesarFactura(_, fn2) >> fn2

        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn1, fn2])

        when:
        def resultado = service.crearNegociacion(request)

        then:
        resultado.fondeadoresAsignados.size() == 2
    }

    // ─── respuesta con idNegociacion ─────────────────────────────────────────

    def "crearNegociacion retorna idNegociacion de la negociacion guardada"() {
        given:
        def fn = buildFn("FECI016", "PROV-001", "PAG-001")
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findByNumeroFactura("FECI016") >> Optional.of(buildFactura("FECI016", "PROV-001", "PAG-001"))
        procesarFacturaNegociacionService.procesarFactura(_, _) >> fn

        Negociacion savedNeg = null
        negociacionPort.save(_) >> { args ->
            savedNeg = args[0]
            savedNeg.id = "NEG-SAVED"
            savedNeg
        }

        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn])

        when:
        def resultado = service.crearNegociacion(request)

        then:
        resultado.idNegociacion == "NEG-SAVED"
    }

    // ─── operación habilitada ──────────────────────────────────────────────────

    def "crearNegociacion lanza AccionNoPermitidaException si la empresa no tiene la operación habilitada"() {
        given:
        operationEnabled = false
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())

        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [])

        when:
        service.crearNegociacion(request)

        then:
        def ex = thrown(AccionNoPermitidaException)
        ex.codigo == "OPERACION_NO_HABILITADA"
        0 * procesarFacturaNegociacionService.procesarFactura(_, _)
    }

    def "crearNegociacion lanza RecursoNoEncontradoException si la empresa solicitante no existe"() {
        given:
        companyExists = false
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())

        def request = buildRequest("FACTORING", "NIT-INEXISTENTE", "OP-001", "PROVEEDOR", [])

        when:
        service.crearNegociacion(request)

        then:
        def ex = thrown(RecursoNoEncontradoException)
        ex.codigo == "EMPRESA_NO_ENCONTRADA"
    }

    def "crearNegociacion continua normalmente cuando la operación sí está habilitada"() {
        given:
        def fn = buildFn("FECI017", "PROV-001", "PAG-001")
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findByNumeroFactura("FECI017") >> Optional.of(buildFactura("FECI017", "PROV-001", "PAG-001"))
        procesarFacturaNegociacionService.procesarFactura(_, fn) >> fn

        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn])

        when:
        def resultado = service.crearNegociacion(request)

        then:
        resultado.estado == EstadoNegociacion.PENDIENTE.name()
    }
}