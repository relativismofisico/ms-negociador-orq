package com.negociadororq.application.service

import com.negociadororq.application.dto.NegociacionRequestDto
import com.negociadororq.application.exception.AccionNoPermitidaException
import com.negociadororq.application.exception.PeticionInvalidaException
import com.negociadororq.application.exception.RecursoNoEncontradoException
import com.negociadororq.application.exception.ReglaDeNegocioException
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

    /** Fase 28d (A2): la transacción Mongo ejecuta la acción; el marcador CREADA entra. */
    com.negociadororq.application.port.output.TransaccionMongoOutputPort transaccionMongo = Stub() {
        ejecutar(_) >> { args -> args[0].get() }
        ejecutarConReintento(_) >> { args -> args[0].get() }
    }
    com.negociadororq.application.port.output.MarcaNegociacionOutputPort marca = Mock() {
        crear(*_) >> true
    }
    com.negociadororq.application.port.output.OperacionPendienteOutputPort operacionesPendientes = Mock()
    ReversionExactaService reversionExacta = Mock()
    AvisoCupoIndirectoService avisoCupoIndirecto = Mock()

    NegociacionFactoringComfirmingServiceImpl service = new NegociacionFactoringComfirmingServiceImpl(
            facturaPort, negociacionPort, userPort, companyPort, companyOperationPort, companySectionVerificationPort,
            notificacionService, procesarFacturaNegociacionService, facturaPatchPort, clock, auditoriaNegociacion,
            transaccionMongo, marca, operacionesPendientes, reversionExacta, avisoCupoIndirecto)

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
        // Fase 28d (A2): el id de la negociación se genera antes de tocar cupos.
        negociacionPort.nuevoId() >> "neg-nuevo"
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
                .tipoOperacion(new Operacion(tipo, claseDe(tipo)))
                .rutOperador(rutOp)
                .facturas(facturas)
                .build()
    }

    // Una clase válida del tipo: crearNegociacion rechaza las que no le corresponden.
    static String claseDe(String tipo) {
        tipo == "CONFIRMING" ? "TRADICIONAL" : "ANTICIPO"
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

    /** Fase 28d: las facturas de la solicitud, en la base con las mismas contrapartes. */
    void facturasEnBd(List<FacturaNegociada> fns) {
        fns.each { fn ->
            facturaPort.findAllByNumeroFactura(fn.numeroFactura) >> listaDe(buildFactura(fn.numeroFactura, fn.rutProveedor,
                    fn.rutPagador))
        }
    }

    static List<Factura> listaDe(Factura f) {
        [f]
    }

    def buildFactura(String numero, String rutProv, String rutPag) {
        def f = new Factura()
        f.cufe = "CUFE-" + numero
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

        facturaPort.findAllByNumeroFactura("FECI001") >> listaDe(buildFactura("FECI001", "PROV-001", "PAG-001"))
        facturaPort.findAllByNumeroFactura("FECI002") >> listaDe(buildFactura("FECI002", "PROV-001", "PAG-001"))

        def fn1Procesada = buildFn("FECI001", "PROV-001", "PAG-001").tap { cufe = "CUFE-" + numeroFactura }
        def fn2Procesada = buildFn("FECI002", "PROV-001", "PAG-001").tap { cufe = "CUFE-" + numeroFactura }
        procesarFacturaNegociacionService.procesarFactura(_, fn1, _, _) >> fn1Procesada
        procesarFacturaNegociacionService.procesarFactura(_, fn2, _, _) >> fn2Procesada

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
        facturaPort.findAllByNumeroFactura("FECI003") >> listaDe(buildFactura("FECI003", "PROV-001", "PAG-001"))

        procesarFacturaNegociacionService.procesarFactura(_, fn1, _, _) >> {
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

    def "Fase 28d (A5b): choque del bloqueo optimista del cupo y la factura ya tiene negociación viva -> 409 FACTURA_CON_NEGOCIACION_VIVA"() {
        given:
        def fn1 = buildFn("FECI060", "PROV-001", "PAG-001")
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findAllByNumeroFactura("FECI060") >> listaDe(buildFactura("FECI060", "PROV-001", "PAG-001"))
        procesarFacturaNegociacionService.procesarFactura(_, fn1, _, _) >> {
            throw new com.negociadororq.application.exception.ConflictoException(
                    "El cupo de la empresa fue modificado por otra negociación concurrente. Vuelve a intentar.",
                    "CUPO_EMPRESA_CONCURRENCIA")
        }
        operacionesPendientes.existeCreacionViva(_) >> true
        facturaPort.findByCufe(_) >> Optional.empty()
        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn1])

        when:
        service.crearNegociacion(request)

        then: "nunca solo 'cupo modificado': la causa es la otra negociación; sin plantilla A ni negociación en Mongo"
        def ex = thrown(com.negociadororq.application.exception.ConflictoException)
        ex.codigo == "FACTURA_CON_NEGOCIACION_VIVA"
        0 * negociacionPort.save(_)
        0 * reversionExacta.revertir(_, _, _, _)
    }

    def "Fase 28d (A5b): choque del bloqueo optimista sin negociación viva -> 409 CUPO_CONCURRENCIA_REINTENTAR y devuelve lo anterior sin plantilla A"() {
        given:
        def fn1 = buildFn("FECI061", "PROV-001", "PAG-001")
        def fn2 = buildFn("FECI062", "PROV-001", "PAG-001")
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findAllByNumeroFactura("FECI061") >> listaDe(buildFactura("FECI061", "PROV-001", "PAG-001"))
        facturaPort.findAllByNumeroFactura("FECI062") >> listaDe(buildFactura("FECI062", "PROV-001", "PAG-001"))
        def fn1Procesada = buildFn("FECI061", "PROV-001", "PAG-001").tap { cufe = "CUFE-" + numeroFactura }
        procesarFacturaNegociacionService.procesarFactura(_, fn1, _, _) >> fn1Procesada
        procesarFacturaNegociacionService.procesarFactura(_, fn2, _, _) >> {
            throw new com.negociadororq.application.exception.ConflictoException(
                    "El cupo del fondeador X fue modificado por otra negociación concurrente. Vuelve a intentar.",
                    "CUPO_FONDEADOR_CONCURRENCIA")
        }
        operacionesPendientes.existeCreacionViva(_) >> false
        facturaPort.findByCufe(_) >> Optional.empty()
        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn1, fn2])

        when:
        service.crearNegociacion(request)

        then:
        def ex = thrown(com.negociadororq.application.exception.ConflictoException)
        ex.codigo == "CUPO_CONCURRENCIA_REINTENTAR"
        1 * marca.crear(_, com.negociadororq.application.port.output.MarcaNegociacionOutputPort.Tipo.CANCELADA) >> true
        1 * reversionExacta.revertir(com.negociadororq.domain.model.TipoOperacionPendiente.NEGOCIACION_VIVA, _, "CUFE-FECI061", _)
        0 * negociacionPort.save(_)
    }

    def "el motivo de negocio de una factura rechazada llega al cliente con su texto"() {
        given:
        def fn1 = buildFn("FECI010", "PROV-001", "PAG-001")

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findAllByNumeroFactura("FECI010") >> listaDe(buildFactura("FECI010", "PROV-001", "PAG-001"))

        procesarFacturaNegociacionService.procesarFactura(_, fn1, _, _) >> {
            throw new com.negociadororq.application.exception.RecursoNoEncontradoException("Operador no existe")
        }

        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn1])

        when:
        def resultado = service.crearNegociacion(request)

        then:
        resultado.errores == ["Operador no existe"]
        1 * negociacionPort.save(_) >> { args -> args[0] }
    }

    def "Fase 28d: SIN_CUPO_INDIRECTO llega en rechazos con código, motivo y montos exactos; el contexto del trío se prepara una vez y viaja a cada factura"() {
        given:
        def fn1 = buildFn("FECI011", "PROV-001", "PAG-001")
        def fn2 = buildFn("FECI012", "PROV-001", "PAG-001")
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findAllByNumeroFactura("FECI011") >> listaDe(buildFactura("FECI011", "PROV-001", "PAG-001"))
        facturaPort.findAllByNumeroFactura("FECI012") >> listaDe(buildFactura("FECI012", "PROV-001", "PAG-001"))
        def contexto = new ContextoCupoIndirecto.Constructor()
                .sumar("20|30|10", new BigDecimal("4000.00"), new BigDecimal("6000.00"))
                .sumar("20|30|10", new BigDecimal("4000.00"), new BigDecimal("6000.00")).construir()
        def detalle = com.negociadororq.application.dto.DetalleCupoIndirectoDto.builder()
                .disponible(new BigDecimal("2000.00")).requerido(new BigDecimal("4000.00"))
                .faltante(new BigDecimal("2000.00")).sumaPeticion(new BigDecimal("8000.00"))
                .disponibleInicial(new BigDecimal("6000.00")).pagador("Pagadora").fondeador("Fondo").build()
        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn1, fn2])

        when:
        def resultado = service.crearNegociacion(request)

        then:
        1 * procesarFacturaNegociacionService.prepararCupoIndirecto(request, _) >> contexto
        1 * procesarFacturaNegociacionService.procesarFactura(_, fn1, _, { it.is(contexto) }) >> fn1
        1 * procesarFacturaNegociacionService.procesarFactura(_, fn2, _, { it.is(contexto) }) >> {
            throw new com.negociadororq.application.exception.CupoIndirectoInsuficienteException("no cabe", detalle)
        }
        1 * negociacionPort.save(_) >> { args -> args[0] }
        1 * avisoCupoIndirecto.avisar("PROV-001", { it.is(detalle) })
        resultado.errores == ["no cabe"]
        resultado.rechazos.size() == 1
        with(resultado.rechazos[0]) {
            numeroFactura == "FECI012"
            codigo == "SIN_CUPO_INDIRECTO"
            mensaje == "no cabe"
            cupoIndirecto.is(detalle)
        }
    }

    def "Fase 28d: un rechazo de negocio sin datos de cupo también va en rechazos, con su código"() {
        given:
        def fn1 = buildFn("FECI013", "PROV-001", "PAG-001")
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findAllByNumeroFactura("FECI013") >> listaDe(buildFactura("FECI013", "PROV-001", "PAG-001"))
        procesarFacturaNegociacionService.procesarFactura(_, fn1, _, _) >> {
            throw new com.negociadororq.application.exception.ReglaDeNegocioException("Empresa sin cupo")
        }

        when:
        def resultado = service.crearNegociacion(buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn1]))

        then:
        1 * negociacionPort.save(_) >> { args -> args[0] }
        resultado.rechazos*.codigo == ["REGLA_DE_NEGOCIO_VIOLADA"]
        resultado.rechazos[0].cupoIndirecto == null
        0 * avisoCupoIndirecto._
    }

    def "Fase 28d (A2): un error técnico (no de dominio) aborta toda la creación: no se guarda la negociación"() {
        given:
        def fn1 = buildFn("FECI009", "PROV-001", "PAG-001")

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findAllByNumeroFactura("FECI009") >> listaDe(buildFactura("FECI009", "PROV-001", "PAG-001"))

        procesarFacturaNegociacionService.procesarFactura(_, fn1, _, _) >> {
            throw new IllegalStateException("could not execute statement; SQL [update cupo set saldo=? where id=?]")
        }

        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn1])

        when:
        service.crearNegociacion(request)

        then: "la excepción sube (sin facturas previas no hay nada que revertir) y no se crea nada en Mongo"
        thrown(IllegalStateException)
        0 * negociacionPort.save(_)
        0 * reversionExacta._
        0 * marca._
    }

    // ─── flujo parcial ────────────────────────────────────────────────────────

    def "crearNegociacion con algunas aceptadas y otras rechazadas -> PENDIENTE"() {
        given:
        def fn1 = buildFn("FECI004", "PROV-001", "PAG-001")
        def fn2 = buildFn("FECI005", "PROV-001", "PAG-001")

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findAllByNumeroFactura("FECI004") >> listaDe(buildFactura("FECI004", "PROV-001", "PAG-001"))
        facturaPort.findAllByNumeroFactura("FECI005") >> listaDe(buildFactura("FECI005", "PROV-001", "PAG-001"))

        def fn1Procesada = buildFn("FECI004", "PROV-001", "PAG-001").tap { cufe = "CUFE-" + numeroFactura }
        procesarFacturaNegociacionService.procesarFactura(_, fn1, _, _) >> fn1Procesada
        procesarFacturaNegociacionService.procesarFactura(_, fn2, _, _) >> {
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
        def fn = buildFn("FECI006", "PROV-RAIZ", "PAG-001")

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findAllByNumeroFactura("FECI006") >> listaDe(buildFactura("FECI006", "PROV-RAIZ", "PAG-001"))

        def procesada = buildFn("FECI006", "PROV-RAIZ", "PAG-001").tap { cufe = "CUFE-" + numeroFactura }
        procesarFacturaNegociacionService.procesarFactura(_, _, _, _) >> procesada

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
        facturaPort.findAllByNumeroFactura("FECI007") >> listaDe(buildFactura("FECI007", "PROV-FACTURA", "PAG-001"))

        def procesada = buildFn("FECI007", "PROV-FACTURA", "PAG-001").tap { cufe = "CUFE-" + numeroFactura }
        procesarFacturaNegociacionService.procesarFactura(_, _, _, _) >> procesada

        // Fase 28d (C): en FACTORING la empresa que negocia es el proveedor de la factura.
        def request = buildRequest("FACTORING", "PROV-FACTURA", "OP-001", "EMPRESA", [fn])

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
        facturaPort.findAllByNumeroFactura("FECI008") >> listaDe(buildFactura("FECI008", "PROV-001", "PAG-UNICO"))

        def procesada = buildFn("FECI008", "PROV-001", "PAG-UNICO").tap { cufe = "CUFE-" + numeroFactura }
        procesarFacturaNegociacionService.procesarFactura(_, _, _, _) >> procesada

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

        facturasEnBd([fn1, fn2])
        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn1, fn2])

        when:
        service.crearNegociacion(request)

        then:
        def ex = thrown(PeticionInvalidaException)
        ex.codigo == "MULTIPLES_CONTRAPARTES"
        0 * procesarFacturaNegociacionService.procesarFactura(_, _, _, _)
    }

    def "crearNegociacion lanza PeticionInvalidaException si las facturas tienen mas de un proveedor (CONFIRMING)"() {
        given:
        def fn1 = buildFn("FECI009B", "PROV-A", "PAG-001")
        def fn2 = buildFn("FECI010B", "PROV-B", "PAG-001")

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())

        facturasEnBd([fn1, fn2])
        def request = buildRequest("CONFIRMING", "PAG-001", "OP-001", "PAGADOR", [fn1, fn2])

        when:
        service.crearNegociacion(request)

        then:
        def ex = thrown(PeticionInvalidaException)
        ex.codigo == "MULTIPLES_CONTRAPARTES"
        0 * procesarFacturaNegociacionService.procesarFactura(_, _, _, _)
    }

    // ─── totalNegociado ───────────────────────────────────────────────────────

    def "crearNegociacion calcula totalNegociado correctamente"() {
        given:
        def fn1 = buildFn("FECI011", "PROV-001", "PAG-001")
        fn1.valorFactura = new BigDecimal("1000.00")
        def fn2 = buildFn("FECI012", "PROV-001", "PAG-001")
        fn2.valorFactura = new BigDecimal("2000.00")

        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findAllByNumeroFactura("FECI011") >> listaDe(buildFactura("FECI011", "PROV-001", "PAG-001"))
        facturaPort.findAllByNumeroFactura("FECI012") >> listaDe(buildFactura("FECI012", "PROV-001", "PAG-001"))

        procesarFacturaNegociacionService.procesarFactura(_, fn1, _, _) >> fn1
        procesarFacturaNegociacionService.procesarFactura(_, fn2, _, _) >> fn2

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
        facturaPort.findAllByNumeroFactura("FECI013") >> listaDe(buildFactura("FECI013", "PROV-001", "PAG-001"))

        procesarFacturaNegociacionService.procesarFactura(_, fn, _, _) >> fn

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
        facturaPort.findAllByNumeroFactura("FECI014") >> listaDe(buildFactura("FECI014", "PROV-001", "PAG-001"))
        facturaPort.findAllByNumeroFactura("FECI015") >> listaDe(buildFactura("FECI015", "PROV-001", "PAG-001"))

        procesarFacturaNegociacionService.procesarFactura(_, fn1, _, _) >> fn1
        procesarFacturaNegociacionService.procesarFactura(_, fn2, _, _) >> fn2

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
        facturaPort.findAllByNumeroFactura("FECI016") >> listaDe(buildFactura("FECI016", "PROV-001", "PAG-001"))
        procesarFacturaNegociacionService.procesarFactura(_, _, _, _) >> fn
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
        0 * procesarFacturaNegociacionService.procesarFactura(_, _, _, _)
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
        facturasEnBd([fn])
        def request = buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn])

        when:
        service.crearNegociacion(request)

        then:
        def ex = thrown(AccionNoPermitidaException)
        ex.codigo == "OPERACION_NO_HABILITADA"
        ex.message.contains("PAG-SIN-FACTORING")
        0 * procesarFacturaNegociacionService.procesarFactura(_, _, _, _)
    }

    def "crearNegociacion lanza AccionNoPermitidaException si el proveedor (contraparte CONFIRMING) no tiene la operación habilitada"() {
        given:
        nitsSinOperacionHabilitada = ["PROV-SIN-CONFIRMING"] as Set
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())

        def fn = buildFn("FECI019", "PROV-SIN-CONFIRMING", "PAG-001")
        facturasEnBd([fn])
        def request = buildRequest("CONFIRMING", "PAG-001", "OP-001", "PAGADOR", [fn])

        when:
        service.crearNegociacion(request)

        then:
        def ex = thrown(AccionNoPermitidaException)
        ex.codigo == "OPERACION_NO_HABILITADA"
        ex.message.contains("PROV-SIN-CONFIRMING")
        0 * procesarFacturaNegociacionService.procesarFactura(_, _, _, _)
    }

    def "crearNegociacion continua normalmente cuando la empresa y la contraparte tienen la operación habilitada"() {
        given:
        def fn1 = buildFn("FECI020", "PROV-001", "PAG-A")
        def fn2 = buildFn("FECI021", "PROV-001", "PAG-A")
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findAllByNumeroFactura("FECI020") >> listaDe(buildFactura("FECI020", "PROV-001", "PAG-A"))
        facturaPort.findAllByNumeroFactura("FECI021") >> listaDe(buildFactura("FECI021", "PROV-001", "PAG-A"))
        procesarFacturaNegociacionService.procesarFactura(_, fn1, _, _) >> fn1
        procesarFacturaNegociacionService.procesarFactura(_, fn2, _, _) >> fn2

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
        facturaPort.findAllByNumeroFactura("FECI017") >> listaDe(buildFactura("FECI017", "PROV-001", "PAG-001"))
        procesarFacturaNegociacionService.procesarFactura(_, fn, _, _) >> fn

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
        facturasEnBd([fn])
        def request = buildRequest("CONFIRMING", "PAG-001", "OP-001", "PAGADOR", [fn])

        when:
        service.crearNegociacion(request)

        then:
        def ex = thrown(AccionNoPermitidaException)
        ex.codigo == "TESORERIA_NO_VERIFICADA"
        ex.message.contains("PAG-001")
        0 * procesarFacturaNegociacionService.procesarFactura(_, _, _, _)
    }

    def "crearNegociacion continua normalmente si la empresa NO califica para tesorería (no es fondeadora ni autofondeadora), aunque tesorería no esté verificada"() {
        given:
        tesoreriaVerificada = false
        nitsSinFondeoOConfirming = ["PROV-001", "PAG-001"] as Set
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findAllByNumeroFactura("FECI024") >> listaDe(buildFactura("FECI024", "PROV-001", "PAG-001"))

        def fn = buildFn("FECI024", "PROV-001", "PAG-001")
        procesarFacturaNegociacionService.procesarFactura(_, fn, _, _) >> fn

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
        facturaPort.findAllByNumeroFactura("FECI025") >> listaDe(buildFactura("FECI025", "PROV-001", "PAG-001"))

        def fn = buildFn("FECI025", "PROV-001", "PAG-001")
        procesarFacturaNegociacionService.procesarFactura(_, fn, _, _) >> fn

        def request = buildRequest("CONFIRMING", "PAG-001", "OP-001", "PAGADOR", [fn])

        when:
        def resultado = service.crearNegociacion(request)

        then:
        resultado.estado == EstadoNegociacion.PENDIENTE.name()
    }

    // ─── clase de operación según el tipo ─────────────────────────────────────

    def "crearNegociacion rechaza con 422 una clase que no pertenece al tipo (#clase en #tipo)"() {
        given:
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        def request = NegociacionRequestDto.builder()
                .rutEmpresa("PROV-001")
                .rolEmpresa("PROVEEDOR")
                .tipoOperacion(new Operacion(tipo, clase))
                .rutOperador("OP-001")
                .facturas([buildFn("F-1", "PROV-001", "PAG-001")])
                .build()

        when:
        service.crearNegociacion(request)

        then:
        def ex = thrown(ReglaDeNegocioException)
        ex.codigo == "CLASE_OPERACION_INVALIDA"
        ex.message == mensaje
        0 * procesarFacturaNegociacionService.procesarFactura(_, _, _, _)
        0 * negociacionPort.save(_)

        where:
        tipo         | clase         || mensaje
        "FACTORING"  | "TRADICIONAL" || "La clase de operación TRADICIONAL no es válida para FACTORING. Clases válidas: ANTICIPO, CON_RESPONSABILIDAD, SIN_DESCUENTO, SIN_RESPONSABILIDAD."
        "CONFIRMING" | "ANTICIPO"    || "La clase de operación ANTICIPO no es válida para CONFIRMING. Clases válidas: TRADICIONAL, PRONTO_PAGO, SIN_RECURSO, COSTO_COMPARTIDO, CON_GARANTIA."
        "factoring"  | "INVENTADA"   || "La clase de operación INVENTADA no es válida para FACTORING. Clases válidas: ANTICIPO, CON_RESPONSABILIDAD, SIN_DESCUENTO, SIN_RESPONSABILIDAD."
        "CONFIRMING" | null          || "La clase de operación null no es válida para CONFIRMING. Clases válidas: TRADICIONAL, PRONTO_PAGO, SIN_RECURSO, COSTO_COMPARTIDO, CON_GARANTIA."
    }

    def "crearNegociacion acepta toda clase válida de su tipo, sin importar mayúsculas (#clase en #tipo)"() {
        given:
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        def request = NegociacionRequestDto.builder()
                .rutEmpresa("PROV-001")
                .rolEmpresa("PROVEEDOR")
                .tipoOperacion(new Operacion(tipo, clase))
                .rutOperador("OP-001")
                .facturas([])
                .build()

        when:
        service.crearNegociacion(request)

        then:
        notThrown(ReglaDeNegocioException)

        where:
        tipo         | clase
        "FACTORING"  | "ANTICIPO"
        "FACTORING"  | "CON_RESPONSABILIDAD"
        "FACTORING"  | "SIN_DESCUENTO"
        "FACTORING"  | "SIN_RESPONSABILIDAD"
        "FACTORING"  | "anticipo"
        "CONFIRMING" | "TRADICIONAL"
        "CONFIRMING" | "PRONTO_PAGO"
        "CONFIRMING" | "SIN_RECURSO"
        "CONFIRMING" | "COSTO_COMPARTIDO"
        "CONFIRMING" | "CON_GARANTIA"
    }

    // ─── Fase 28d: contraparte única con los RUT de la BASE, antes de reservar cupos ───────────────

    def "Fase 28d: #tipo con RUT de contraparte #caso en la solicitud y facturas de dos contrapartes en la base => #codigo sin cupos"() {
        given:
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        boolean confirming = tipo == "CONFIRMING"
        facturaPort.findAllByNumeroFactura("F28D-1") >> listaDe(confirming ? buildFactura("F28D-1", "PROV-A", "PAG-001")
                : buildFactura("F28D-1", "PROV-001", "PAG-A"))
        facturaPort.findAllByNumeroFactura("F28D-2") >> listaDe(confirming ? buildFactura("F28D-2", "PROV-B", "PAG-001")
                : buildFactura("F28D-2", "PROV-001", "PAG-B"))
        def fn1 = confirming ? buildFn("F28D-1", enviado, "PAG-001") : buildFn("F28D-1", "PROV-001", enviado)
        def fn2 = confirming ? buildFn("F28D-2", enviado, "PAG-001") : buildFn("F28D-2", "PROV-001", enviado)
        def request = confirming ? buildRequest("CONFIRMING", "PAG-001", "OP-001", "PAGADOR", [fn1, fn2])
                : buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", [fn1, fn2])

        when:
        service.crearNegociacion(request)

        then: "con un RUT falso la factura ya no se resuelve (C: número + RUT de la solicitud)"
        def ex = thrown(com.negociadororq.application.exception.NegociadorException)
        ex.codigo == codigo
        0 * procesarFacturaNegociacionService.procesarFactura(_, _, _, _)
        0 * negociacionPort.save(_)

        where:
        tipo         | caso    | enviado  | codigo
        "CONFIRMING" | "vacío" | ""       | "MULTIPLES_CONTRAPARTES"
        "CONFIRMING" | "nulo"  | null     | "MULTIPLES_CONTRAPARTES"
        "CONFIRMING" | "falso" | "PROV-A" | "FACTURA_NO_ENCONTRADA"
        "FACTORING"  | "vacío" | ""       | "MULTIPLES_CONTRAPARTES"
        "FACTORING"  | "nulo"  | null     | "MULTIPLES_CONTRAPARTES"
        "FACTORING"  | "falso" | "PAG-A"  | "FACTURA_NO_ENCONTRADA"
    }

    def "Fase 28d: factura sin proveedor en la base (CONFIRMING) => error, sin cupos"() {
        given:
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findAllByNumeroFactura("F28D-3") >> listaDe(buildFactura("F28D-3", null, "PAG-001"))
        def request = buildRequest("CONFIRMING", "PAG-001", "OP-001", "PAGADOR", [buildFn("F28D-3", "", "PAG-001")])

        when:
        service.crearNegociacion(request)

        then:
        def ex = thrown(PeticionInvalidaException)
        ex.codigo == "FACTURA_SIN_CONTRAPARTE"
        0 * procesarFacturaNegociacionService.procesarFactura(_, _, _, _)
    }

    def "Fase 28d: un solo proveedor en la base pasa aunque el cliente no mande su RUT (CONFIRMING)"() {
        given:
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findAllByNumeroFactura("F28D-4") >> listaDe(buildFactura("F28D-4", "PROV-A", "PAG-001"))
        facturaPort.findAllByNumeroFactura("F28D-5") >> listaDe(buildFactura("F28D-5", "PROV-A", "PAG-001"))
        procesarFacturaNegociacionService.procesarFactura(_, _, _, _) >> { args -> args[1] }
        def request = buildRequest("CONFIRMING", "PAG-001", "OP-001", "PAGADOR",
                [buildFn("F28D-4", "", "PAG-001"), buildFn("F28D-5", null, "PAG-001")])

        when:
        service.crearNegociacion(request)

        then:
        1 * negociacionPort.save({ it.rutProveedor == "PROV-A" }) >> { args -> args[0] }
    }

    def "Fase 28d: la contraparte se valida habilitada con el RUT de la base, no con el del cliente"() {
        given: "el cliente no manda el proveedor; el de la base no tiene CONFIRMING habilitado"
        nitsSinOperacionHabilitada = ["PROV-REAL"] as Set
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findAllByNumeroFactura("F28D-6") >> listaDe(buildFactura("F28D-6", "PROV-REAL", "PAG-001"))
        def request = buildRequest("CONFIRMING", "PAG-001", "OP-001", "PAGADOR", [buildFn("F28D-6", "", "PAG-001")])

        when:
        service.crearNegociacion(request)

        then:
        thrown(com.negociadororq.application.exception.AccionNoPermitidaException)
        0 * procesarFacturaNegociacionService.procesarFactura(_, _, _, _)
    }

    def "Fase 28d (C): dos empresas con la factura 1: A negocia la suya (CUFE de la base) y la de B no se toca"() {
        given:
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        def deA = buildFactura("1", "PROV-A", "PAG-001").tap { cufe = "CUFE-A" }
        def deB = buildFactura("1", "PROV-B", "PAG-001").tap { cufe = "CUFE-B" }
        facturaPort.findAllByNumeroFactura("1") >> [deA, deB]
        def request = buildRequest("FACTORING", "PROV-A", "OP-001", "PROVEEDOR", [buildFn("1", null, "PAG-001")])

        when:
        service.crearNegociacion(request)

        then:
        1 * procesarFacturaNegociacionService.procesarFactura(_, { it.cufe == "CUFE-A" && it.rutProveedor == "PROV-A" }, _, _) >>
                { args -> args[1] }
        1 * facturaPatchPort.actualizarNegociacionIdPorCufe({ it.cufe() == "CUFE-A" && it.rutProveedor() == "PROV-A" }, _)
        0 * facturaPatchPort.actualizarNegociacionIdPorCufe({ it.cufe() == "CUFE-B" }, _)
        1 * negociacionPort.save({ it.facturas*.cufe == ["CUFE-A"] }) >> { args -> args[0] }
    }

    def "Fase 28d (C): B con los RUT de A => FACTURA_DE_OTRA_EMPRESA antes de reservar cupos"() {
        given:
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        facturaPort.findAllByNumeroFactura("1") >> [buildFactura("1", "PROV-A", "PAG-001").tap { cufe = "CUFE-A" },
                                                   buildFactura("1", "PROV-B", "PAG-001").tap { cufe = "CUFE-B" }]
        def request = buildRequest("FACTORING", "PROV-B", "OP-001", "PROVEEDOR", [buildFn("1", "PROV-A", "PAG-001")])

        when:
        service.crearNegociacion(request)

        then:
        def ex = thrown(com.negociadororq.application.exception.AccionNoPermitidaException)
        ex.codigo == ResolutorFactura.FACTURA_DE_OTRA_EMPRESA
        0 * procesarFacturaNegociacionService.procesarFactura(_, _, _, _)
        0 * facturaPatchPort._
        0 * negociacionPort.save(_)
    }
    // ─── Fase 28d (A2): decisión de John sobre fallos en negociaciones de varias facturas ─────────────────────────

    /** Tres facturas; la 2.ª y la 3.ª se comportan según los cierres dados. */
    def tresFacturas(Closure segunda, Closure tercera,
            Closure primera = { buildFn("FECA1", "PROV-001", "PAG-001").tap { cufe = "CUFE-FECA1" } }) {
        def fns = ["FECA1", "FECA2", "FECA3"].collect { buildFn(it, "PROV-001", "PAG-001") }
        userPort.findByUsername("OP-001") >> Optional.of(User.builder().ideUser(1L).build())
        fns.each { f -> facturaPort.findAllByNumeroFactura(f.numeroFactura) >> listaDe(buildFactura(f.numeroFactura, "PROV-001", "PAG-001")) }
        procesarFacturaNegociacionService.procesarFactura(_, fns[0], _, _) >> { primera.call() }
        procesarFacturaNegociacionService.procesarFactura(_, fns[1], _, _) >> { segunda.call() }
        procesarFacturaNegociacionService.procesarFactura(_, fns[2], _, _) >> { tercera.call() }
        buildRequest("FACTORING", "PROV-001", "OP-001", "PROVEEDOR", fns)
    }

    def "a/b) 3 facturas, la 2.ª rechazada por #motivo: se negocian la 1.ª y la 3.ª, la 2.ª con su error, sin reversión"() {
        given:
        def request = tresFacturas({ throw excepcion }, { buildFn("FECA3", "PROV-001", "PAG-001").tap { cufe = "CUFE-FECA3" } })

        when:
        def r = service.crearNegociacion(request)

        then:
        r.estado == EstadoNegociacion.PENDIENTE.name()
        r.facturas*.numeroFactura == ["FECA1", "FECA3"]
        r.errores.size() == 1
        0 * reversionExacta._
        1 * marca.crear("neg-nuevo", com.negociadororq.application.port.output.MarcaNegociacionOutputPort.Tipo.CREADA) >> true
        1 * negociacionPort.save(_) >> { args -> args[0] }
        1 * operacionesPendientes.transicionar(_, "neg-nuevo|CUFE-FECA1", "CUPOS_DESCONTADOS", "COMPLETADA")
        1 * operacionesPendientes.transicionar(_, "neg-nuevo|CUFE-FECA3", "CUPOS_DESCONTADOS", "COMPLETADA")

        where:
        motivo     | excepcion
        "cupo"     | new com.negociadororq.application.exception.ReglaDeNegocioException("Empresa sin cupo")
        "vigencia" | new com.negociadororq.application.exception.ReglaDeNegocioException("La vigencia del descuento ya no permite negociar")
        "rol"      | new com.negociadororq.application.exception.RolNoPermitidoException("FACTORING", "PAGADOR")
    }

    def "c) 3 facturas, error técnico en la 2.ª: no se crea la negociación y se revierte exacto solo la 1.ª"() {
        given:
        def request = tresFacturas({ throw new IllegalStateException("caída de la BD") }, { assert false, "no debe llegar" })

        when:
        service.crearNegociacion(request)

        then:
        thrown(IllegalStateException)
        1 * marca.crear("neg-nuevo", com.negociadororq.application.port.output.MarcaNegociacionOutputPort.Tipo.CANCELADA) >> true
        1 * reversionExacta.revertir(com.negociadororq.domain.model.TipoOperacionPendiente.CREAR_NEGOCIACION, "neg-nuevo", "CUFE-FECA1", _)
        0 * reversionExacta.revertir(_, _, "CUFE-FECA2", _)
        0 * negociacionPort.save(_)
    }

    def "d) la 2.ª rechazada por negocio y error técnico en la 3.ª: se aborta todo y se revierte exacto solo la 1.ª"() {
        given:
        def request = tresFacturas({ throw new com.negociadororq.application.exception.ReglaDeNegocioException("sin cupo") },
                { throw new IllegalStateException("caída") })

        when:
        service.crearNegociacion(request)

        then:
        thrown(IllegalStateException)
        1 * reversionExacta.revertir(_, "neg-nuevo", "CUFE-FECA1", _)
        0 * reversionExacta.revertir(_, _, { it != "CUFE-FECA1" }, _)
        0 * negociacionPort.save(_)
    }

    def "e) todas rechazadas por negocio: nada descontado ni revertido; se conserva lo de hoy (RECHAZADA guardada con sus errores)"() {
        given:
        def sinCupo = { throw new com.negociadororq.application.exception.ReglaDeNegocioException("sin cupo") }
        def request = tresFacturas(sinCupo, sinCupo, sinCupo)

        when:
        def r = service.crearNegociacion(request)

        then:
        r.estado == EstadoNegociacion.RECHAZADA.name()
        r.errores.size() == 3
        0 * reversionExacta._
        0 * operacionesPendientes.transicionar(*_)
        1 * negociacionPort.save({ it.estado == "RECHAZADA" }) >> { args -> args[0] }
    }

    def "petición lenta: el reconciliador ya puso CANCELADA; la creación aborta con 409 NEGOCIACION_CANCELADA, sin guardar"() {
        given:
        def request = tresFacturas({ buildFn("FECA2", "PROV-001", "PAG-001").tap { cufe = "CUFE-FECA2" } },
                { buildFn("FECA3", "PROV-001", "PAG-001").tap { cufe = "CUFE-FECA3" } })

        when:
        service.crearNegociacion(request)

        then:
        1 * marca.crear("neg-nuevo", com.negociadororq.application.port.output.MarcaNegociacionOutputPort.Tipo.CREADA) >> false
        def e = thrown(com.negociadororq.application.exception.ConflictoException)
        e.codigo == "NEGOCIACION_CANCELADA"
        !e.message.toLowerCase().contains("exception")
        0 * negociacionPort.save(_)
        0 * operacionesPendientes.transicionar(*_)
    }

    def "feliz: un solo paso Mongo con la negociación (id previo), las facturas y el correo; luego COMPLETADA"() {
        given:
        def request = tresFacturas({ buildFn("FECA2", "PROV-001", "PAG-001").tap { cufe = "CUFE-FECA2" } },
                { buildFn("FECA3", "PROV-001", "PAG-001").tap { cufe = "CUFE-FECA3" } })

        when:
        def r = service.crearNegociacion(request)

        then:
        r.idNegociacion == "neg-nuevo"
        1 * negociacionPort.save({ it.id == "neg-nuevo" }) >> { args -> args[0] }
        3 * procesarFacturaNegociacionService.marcarComoNegociada(request, _)
        1 * notificacionService.notificarCreacionNegociacion(_)
        3 * operacionesPendientes.transicionar(_, _, "CUPOS_DESCONTADOS", "COMPLETADA")
    }

}
