package com.negociadororq.application.service

import com.negociadororq.application.dto.NegociacionRequestDto
import com.negociadororq.application.exception.AccionNoPermitidaException
import com.negociadororq.application.exception.PeticionInvalidaException
import com.negociadororq.application.exception.RecursoNoEncontradoException
import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.CompanyOperationOutputPort
import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.CompanySectionVerificationOutputPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.FacturaPatchOutputPort
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
import java.time.LocalDateTime

class NegociacionFactoringComfirmingServiceImplSpec extends Specification {

    FacturaOutputPort facturaPort = Mock()
    NegociacionOutputPort negociacionPort = Mock()
    UserOutputPort userPort = Mock()
    CompanyOutputPort companyPort = Mock()
    CompanyOperationOutputPort companyOperationPort = Mock()
    CompanySectionVerificationOutputPort companySectionVerificationPort = Mock()
    NotificacionService notificacionService = Mock()
    ProcesarFacturaNegociacionService procesarFacturaNegociacionService = Mock()
    FacturaPatchOutputPort facturaPatchPort = Mock()
    ClockPort clock = Mock() {
        now() >> LocalDateTime.now()
    }

    AuditoriaNegociacionService auditoriaNegociacion = Mock()

    NegociacionFactoringComfirmingServiceImpl service = new NegociacionFactoringComfirmingServiceImpl(
            facturaPort, negociacionPort, userPort, companyPort, companyOperationPort, companySectionVerificationPort,
            notificacionService, procesarFacturaNegociacionService, facturaPatchPort, clock, auditoriaNegociacion)

    // Controlan el comportamiento por defecto de companyPort/companyOperationPort: los tests
    // existentes (que no se centran en esta validación) no necesitan re-declarar estos stubs,
    // y los tests dedicados a la validación mutan estas variables antes del "when:" para
    // simular los casos borde (empresa inexistente / operación no habilitada), en vez de
    // declarar una segunda interacción (con Spock, la primera declarada gana, no la última).
    Boolean companyExists = true
    Boolean operationEnabled = true
    // Permite simular que UNA empresa puntual (por NIT) no tiene la operación habilitada,
    // manteniendo `operationEnabled` como comportamiento por defecto para el resto.
    Set<String> nitsSinOperacionHabilitada = [] as Set
    // Por defecto el mock genérico de companyOperationPort.isEnabled(_, _) dice que sí a
    // cualquier tipo pedido (incluido FONDEADOR/CONFIRMING) para cualquier NIT no excluido
    // arriba; este set permite marcar NITs puntuales que NO califican para tesorería
    // (no son fondeadoras ni autofondeadoras), sin afectar el resto del stub genérico.
    Set<String> nitsSinFondeoOConfirming = [] as Set
    // Controla companySectionVerificationPort.tesoreriaYCuentasVerificadas(_): por defecto
    // "sí verificada", para que los tests que no se centran en esto no se vean afectados.
    Boolean tesoreriaVerificada = true
    private final Map<String, Long> idPorNit = [:]
    // Permite simular el id que el adapter real asignaría al guardar (ver test
    // "retorna idNegociacion..."); por defecto conserva el id ya presente en la negociación.
    String idNegociacionAAsignar = null

    def setup() {
        companyPort.findByNit(_ as String) >> { String nit ->
            if (!companyExists) {
                return Optional.empty()
            }
            Long id = idPorNit.computeIfAbsent(nit) { idPorNit.size() + 1L }
            Optional.of(Company.builder().ideCompany(id).nit(nit).build())
        }
        companyOperationPort.isEnabled(_, _) >> { Long id, String type ->
            String nit = idPorNit.find { entry -> entry.value == id }?.key
            if (nit in nitsSinOperacionHabilitada) {
                return false
            }
            if ((type == "FONDEADOR" || type == "CONFIRMING") && nit in nitsSinFondeoOConfirming) {
                return false
            }
            operationEnabled
        }
        companySectionVerificationPort.tesoreriaYCuentasVerificadas(_) >> { tesoreriaVerificada }
        // Simula que el adapter real devuelve la negociación persistida (con id asignado):
        // el servicio reasigna `negociacion = negociacionPort.save(negociacion)`, así que sin
        // este stub el valor por defecto (null) rompería el uso posterior de negociacion.getId().
        negociacionPort.save(_) >> { args ->
            Negociacion n = args[0]
            if (idNegociacionAAsignar) {
                n.id = idNegociacionAAsignar
            }
            n
        }
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
        1 * negociacionPort.save(_) >> { args -> args[0] }
        1 * notificacionService.notificarCreacionNegociacion(_)
    }

    // ─── flujo con facturas rechazadas ────────────────────────────────────────

    def "crearNegociacion con todas las facturas rechazadas -> RECHAZADA"() {
        given:
        def fn1 = buildFn("FECI003", "PROV-001", "PAG-001")

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findByNumeroFactura("FECI003") >> Optional.of(buildFactura("FECI003", "PROV-001", "PAG-001"))

        procesarFacturaNegociacionService.procesarFactura(_, fn1) >> {
            throw new com.negociadororq.application.exception.ReglaDeNegocioException("Factura sin cupo")
        }

        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn1])

        when:
        def resultado = service.crearNegociacion(request)

        then:
        resultado.estado == EstadoNegociacion.RECHAZADA.name()
        resultado.errores.any { it.contains("Factura sin cupo") }
        1 * negociacionPort.save(_) >> { args -> args[0] }
    }

    def "una factura rechazada por un error que no es del dominio informa un motivo fijo, sin el texto de la excepción"() {
        given:
        def fn1 = buildFn("FECI009", "PROV-001", "PAG-001")

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findByNumeroFactura("FECI009") >> Optional.of(buildFactura("FECI009", "PROV-001", "PAG-001"))

        procesarFacturaNegociacionService.procesarFactura(_, fn1) >> {
            throw new IllegalStateException("could not execute statement; SQL [update cupo set saldo=? where id=?]")
        }

        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn1])

        when:
        def resultado = service.crearNegociacion(request)

        then:
        resultado.errores == [NegociacionFactoringComfirmingServiceImpl.MENSAJE_ERROR_FACTURA]
        resultado.errores.every { !it.contains("SQL") && !it.contains("update cupo") }
        1 * negociacionPort.save({ it.errores == [NegociacionFactoringComfirmingServiceImpl.MENSAJE_ERROR_FACTURA] }) >> { args -> args[0] }
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
            throw new com.negociadororq.application.exception.ReglaDeNegocioException("FECI005 sin cupo")
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
        1 * negociacionPort.save({ it.rutProveedor == "PROV-RAIZ" }) >> { args -> args[0] }
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
        1 * negociacionPort.save({ it.rutProveedor == "PROV-FACTURA" }) >> { args -> args[0] }
    }

    // ─── un solo pagador (múltiples pagadores en una misma solicitud está
    // prohibido por validarUnicaContraparte, ver tests de MULTIPLES_CONTRAPARTES) ──

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
        }) >> { args -> args[0] }
    }

    def "crearNegociacion lanza PeticionInvalidaException si las facturas tienen mas de un pagador (FACTORING)"() {
        given:
        def fn1 = buildFn("FECI009", "PROV-001", "PAG-A")
        def fn2 = buildFn("FECI010", "PROV-001", "PAG-B")

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())

        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn1, fn2])

        when:
        service.crearNegociacion(request)

        then:
        def ex = thrown(PeticionInvalidaException)
        ex.codigo == "MULTIPLES_CONTRAPARTES"
        0 * procesarFacturaNegociacionService.procesarFactura(_, _)
    }

    def "crearNegociacion lanza PeticionInvalidaException si las facturas tienen mas de un proveedor (CONFIRMING)"() {
        given:
        def fn1 = buildFn("FECI009B", "PROV-A", "PAG-001")
        def fn2 = buildFn("FECI010B", "PROV-B", "PAG-001")

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())

        def request = buildRequest("CONFIRMING", "PAG-001", "OP-001", "PAGADOR", [fn1, fn2])

        when:
        service.crearNegociacion(request)

        then:
        def ex = thrown(PeticionInvalidaException)
        ex.codigo == "MULTIPLES_CONTRAPARTES"
        0 * procesarFacturaNegociacionService.procesarFactura(_, _)
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
        idNegociacionAAsignar = "NEG-SAVED"

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

    def "crearNegociacion lanza AccionNoPermitidaException si el pagador (contraparte FACTORING) no tiene la operación habilitada"() {
        given:
        nitsSinOperacionHabilitada = ["PAG-SIN-FACTORING"] as Set
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())

        def fn = buildFn("FECI018", "PROV-001", "PAG-SIN-FACTORING")
        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn])

        when:
        service.crearNegociacion(request)

        then:
        def ex = thrown(AccionNoPermitidaException)
        ex.codigo == "OPERACION_NO_HABILITADA"
        ex.message.contains("PAG-SIN-FACTORING")
        0 * procesarFacturaNegociacionService.procesarFactura(_, _)
    }

    def "crearNegociacion lanza AccionNoPermitidaException si el proveedor (contraparte CONFIRMING) no tiene la operación habilitada"() {
        given:
        nitsSinOperacionHabilitada = ["PROV-SIN-CONFIRMING"] as Set
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())

        def fn = buildFn("FECI019", "PROV-SIN-CONFIRMING", "PAG-001")
        def request = buildRequest("CONFIRMING", "PAG-001", "OP-001", "PAGADOR", [fn])

        when:
        service.crearNegociacion(request)

        then:
        def ex = thrown(AccionNoPermitidaException)
        ex.codigo == "OPERACION_NO_HABILITADA"
        ex.message.contains("PROV-SIN-CONFIRMING")
        0 * procesarFacturaNegociacionService.procesarFactura(_, _)
    }

    def "crearNegociacion continua normalmente cuando la empresa y la contraparte tienen la operación habilitada"() {
        given:
        def fn1 = buildFn("FECI020", "PROV-001", "PAG-A")
        def fn2 = buildFn("FECI021", "PROV-001", "PAG-A")
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findByNumeroFactura("FECI020") >> Optional.of(buildFactura("FECI020", "PROV-001", "PAG-A"))
        facturaPort.findByNumeroFactura("FECI021") >> Optional.of(buildFactura("FECI021", "PROV-001", "PAG-A"))
        procesarFacturaNegociacionService.procesarFactura(_, fn1) >> fn1
        procesarFacturaNegociacionService.procesarFactura(_, fn2) >> fn2

        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn1, fn2])

        when:
        def resultado = service.crearNegociacion(request)

        then:
        resultado.estado == EstadoNegociacion.PENDIENTE.name()
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

    // ─── tesorería/cuentas bancarias verificadas (empresas fondeadoras/autofondeadoras) ──

    def "crearNegociacion lanza AccionNoPermitidaException si la empresa solicitante califica para tesorería y no la tiene verificada"() {
        given:
        tesoreriaVerificada = false
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())

        def fn = buildFn("FECI022", "PROV-001", "PAG-001")
        def request = buildRequest("CONFIRMING", "PAG-001", "OP-001", "PAGADOR", [fn])

        when:
        service.crearNegociacion(request)

        then:
        def ex = thrown(AccionNoPermitidaException)
        ex.codigo == "TESORERIA_NO_VERIFICADA"
        ex.message.contains("PAG-001")
        0 * procesarFacturaNegociacionService.procesarFactura(_, _)
    }

    def "crearNegociacion continua normalmente si la empresa NO califica para tesorería (no es fondeadora ni autofondeadora), aunque tesorería no esté verificada"() {
        given:
        tesoreriaVerificada = false
        nitsSinFondeoOConfirming = ["PROV-001", "PAG-001"] as Set
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findByNumeroFactura("FECI024") >> Optional.of(buildFactura("FECI024", "PROV-001", "PAG-001"))

        def fn = buildFn("FECI024", "PROV-001", "PAG-001")
        procesarFacturaNegociacionService.procesarFactura(_, fn) >> fn

        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn])

        when:
        def resultado = service.crearNegociacion(request)

        then:
        resultado.estado == EstadoNegociacion.PENDIENTE.name()
    }

    def "crearNegociacion continua normalmente cuando la empresa califica para tesorería pero ya la tiene verificada"() {
        given:
        tesoreriaVerificada = true
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findByNumeroFactura("FECI025") >> Optional.of(buildFactura("FECI025", "PROV-001", "PAG-001"))

        def fn = buildFn("FECI025", "PROV-001", "PAG-001")
        procesarFacturaNegociacionService.procesarFactura(_, fn) >> fn

        def request = buildRequest("CONFIRMING", "PAG-001", "OP-001", "PAGADOR", [fn])

        when:
        def resultado = service.crearNegociacion(request)

        then:
        resultado.estado == EstadoNegociacion.PENDIENTE.name()
    }
}