package com.negociadororq.application.service

import com.negociadororq.application.exception.ConflictoException
import com.negociadororq.application.exception.PeticionInvalidaException
import com.negociadororq.application.exception.RecursoNoEncontradoException
import com.negociadororq.application.exception.ReglaDeNegocioException
import com.negociadororq.application.port.output.EmpresaQuotaOutputPort
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort
import com.negociadororq.domain.model.EmpresaQuota
import com.negociadororq.domain.model.FondeadorQuota
import org.springframework.orm.ObjectOptimisticLockingFailureException
import spock.lang.Specification

import java.math.BigDecimal

class CupoPropioFondeoServiceImplSpec extends Specification {

    FondeadorQuotaOutputPort fondeadorQuotaPort = Mock()
    EmpresaQuotaOutputPort empresaQuotaPort = Mock()

    CupoPropioFondeoServiceImpl service = new CupoPropioFondeoServiceImpl(fondeadorQuotaPort, empresaQuotaPort)

    // ─── obtenerCupoPropio ────────────────────────────────────────────────────

    def "obtenerCupoPropio retorna el cupo actual y el tope del administrador"() {
        given:
        def autofondeo = FondeadorQuota.builder()
                .funderId(1L).payerId(1L)
                .payerQuota(new BigDecimal("300.00"))
                .payerCurrentQuota(new BigDecimal("200.00"))
                .payerRate(new BigDecimal("2.5"))
                .build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)

        when:
        def resultado = service.obtenerCupoPropio(1L)

        then:
        resultado.companyId == 1L
        resultado.cupoPropio == new BigDecimal("300.00")
        resultado.cupoDisponible == new BigDecimal("200.00")
        resultado.cupoMaximoAdministrador == new BigDecimal("1000.00")
        resultado.tasa == new BigDecimal("2.5")
    }

    def "obtenerCupoPropio lanza RecursoNoEncontradoException si la empresa no es fondeadora de sí misma"() {
        given:
        fondeadorQuotaPort.findByFunderIdAndPayerId(9L, 9L) >> Optional.empty()

        when:
        service.obtenerCupoPropio(9L)

        then:
        thrown(RecursoNoEncontradoException)
    }

    def "obtenerCupoPropio lanza RecursoNoEncontradoException si el administrador no asignó cupo general"() {
        given:
        def autofondeo = FondeadorQuota.builder().funderId(1L).payerId(1L).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.empty()

        when:
        service.obtenerCupoPropio(1L)

        then:
        thrown(RecursoNoEncontradoException)
    }

    // ─── actualizarCupoPropio ─────────────────────────────────────────────────

    def "actualizarCupoPropio actualiza el cupo preservando el monto ya consumido"() {
        given:
        // quota=300, disponible=200 -> consumido=100
        def autofondeo = FondeadorQuota.builder()
                .funderId(1L).payerId(1L)
                .payerQuota(new BigDecimal("300.00"))
                .payerCurrentQuota(new BigDecimal("200.00"))
                .build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)

        when:
        def resultado = service.actualizarCupoPropio(
                1L, new BigDecimal("500.00"), new BigDecimal("2.5"), "FIJO", null, null, "TODO_EL_PLAZO", null, new BigDecimal("0"))

        then:
        1 * fondeadorQuotaPort.saveAndFlush(autofondeo) >> autofondeo
        autofondeo.payerQuota == new BigDecimal("500.00")
        autofondeo.payerCurrentQuota == new BigDecimal("400.00")
        autofondeo.payerRate == new BigDecimal("2.5")
        autofondeo.tipoDescuento == "FIJO"
        autofondeo.tipoTasa == null
        autofondeo.vigenciaDescuento == "TODO_EL_PLAZO"
        resultado.cupoPropio == new BigDecimal("500.00")
        resultado.cupoDisponible == new BigDecimal("400.00")
        resultado.tipoDescuento == "FIJO"
    }

    def "actualizarCupoPropio no deja el disponible negativo cuando el nuevo cupo es menor a lo ya consumido"() {
        given:
        // quota=300, disponible=50 -> consumido=250
        def autofondeo = FondeadorQuota.builder()
                .funderId(1L).payerId(1L)
                .payerQuota(new BigDecimal("300.00"))
                .payerCurrentQuota(new BigDecimal("50.00"))
                .build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)
        fondeadorQuotaPort.saveAndFlush(_) >> { FondeadorQuota fq -> fq }

        when:
        service.actualizarCupoPropio(
                1L, new BigDecimal("100.00"), new BigDecimal("2.5"), "FIJO", null, null, "TODO_EL_PLAZO", null, new BigDecimal("0"))

        then:
        autofondeo.payerQuota == new BigDecimal("100.00")
        autofondeo.payerCurrentQuota == BigDecimal.ZERO
    }

    def "actualizarCupoPropio lanza ReglaDeNegocioException si excede el cupo del administrador"() {
        given:
        def autofondeo = FondeadorQuota.builder()
                .funderId(1L).payerId(1L)
                .payerQuota(new BigDecimal("300.00"))
                .payerCurrentQuota(new BigDecimal("300.00"))
                .build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)

        when:
        service.actualizarCupoPropio(
                1L, new BigDecimal("1500.00"), new BigDecimal("2.5"), "FIJO", null, null, "TODO_EL_PLAZO", null, new BigDecimal("0"))

        then:
        thrown(ReglaDeNegocioException)
        0 * fondeadorQuotaPort.saveAndFlush(_)
    }

    def "actualizarCupoPropio lanza RecursoNoEncontradoException si la empresa no es fondeadora de sí misma"() {
        given:
        fondeadorQuotaPort.findByFunderIdAndPayerId(9L, 9L) >> Optional.empty()

        when:
        service.actualizarCupoPropio(
                9L, new BigDecimal("100.00"), new BigDecimal("2.5"), "FIJO", null, null, "TODO_EL_PLAZO", null, new BigDecimal("0"))

        then:
        thrown(RecursoNoEncontradoException)
    }

    def "actualizarCupoPropio lanza ConflictoException si otra peticion concurrente ya modifico la misma configuracion"() {
        given: "La configuración cambió entre el read y el saveAndFlush (otra pestaña de administrador la tocó primero)"
        def autofondeo = FondeadorQuota.builder().funderId(1L).payerId(1L).payerQuota(new BigDecimal("300.00")).build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)
        fondeadorQuotaPort.saveAndFlush(_) >> { throw new ObjectOptimisticLockingFailureException(FondeadorQuota, 1L) }

        when:
        service.actualizarCupoPropio(
                1L, new BigDecimal("400.00"), new BigDecimal("2.5"), "FIJO", null, null, "TODO_EL_PLAZO", null, new BigDecimal("0"))

        then: "Se traduce a un 409 (no al 500 genérico), para que el frontend pueda ofrecer reintentar"
        def ex = thrown(ConflictoException)
        ex.codigo == "CUPO_PROPIO_CONCURRENCIA"
    }

    def "actualizarCupoPropio maneja quota y disponible nulos como cero consumido"() {
        given:
        def autofondeo = FondeadorQuota.builder().funderId(1L).payerId(1L).build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)
        fondeadorQuotaPort.saveAndFlush(_) >> { FondeadorQuota fq -> fq }

        when:
        service.actualizarCupoPropio(
                1L, new BigDecimal("400.00"), new BigDecimal("2.5"), "FIJO", null, null, "TODO_EL_PLAZO", null, new BigDecimal("0"))

        then:
        autofondeo.payerQuota == new BigDecimal("400.00")
        autofondeo.payerCurrentQuota == new BigDecimal("400.00")
    }

    def "actualizarCupoPropio con tipoDescuento FINANCIERO guarda el tipoTasa indicado, sin tarifaPlena"() {
        given:
        def autofondeo = FondeadorQuota.builder().funderId(1L).payerId(1L).build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)

        when:
        def resultado = service.actualizarCupoPropio(
                1L, new BigDecimal("400.00"), new BigDecimal("2.5"), "FINANCIERO", "TASA_MES_VENCIDO", null,
                "TODO_EL_PLAZO", null, new BigDecimal("0"))

        then:
        1 * fondeadorQuotaPort.saveAndFlush(autofondeo) >> autofondeo
        autofondeo.tipoDescuento == "FINANCIERO"
        autofondeo.tipoTasa == "TASA_MES_VENCIDO"
        autofondeo.tarifaPlena == null
        resultado.tipoTasa == "TASA_MES_VENCIDO"
    }

    def "actualizarCupoPropio con tipoDescuento DINAMICO exige y guarda el tipoTasa y la tarifaPlena"() {
        given:
        def autofondeo = FondeadorQuota.builder().funderId(1L).payerId(1L).build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)

        when:
        def resultado = service.actualizarCupoPropio(
                1L, new BigDecimal("400.00"), new BigDecimal("2.5"), "DINAMICO", "TASA_EFECTIVA_ANUAL",
                new BigDecimal("3.0"), "PRIMEROS_DIAS", 5, new BigDecimal("0"))

        then:
        1 * fondeadorQuotaPort.saveAndFlush(autofondeo) >> autofondeo
        autofondeo.tipoDescuento == "DINAMICO"
        autofondeo.tipoTasa == "TASA_EFECTIVA_ANUAL"
        autofondeo.tarifaPlena == new BigDecimal("3.0")
        autofondeo.vigenciaDescuento == "PRIMEROS_DIAS"
        autofondeo.diasVigencia == 5
        resultado.tipoDescuento == "DINAMICO"
        resultado.tarifaPlena == new BigDecimal("3.0")
        resultado.vigenciaDescuento == "PRIMEROS_DIAS"
        resultado.diasVigencia == 5
    }

    def "actualizarCupoPropio ignora y limpia el tipoTasa y la tarifaPlena cuando tipoDescuento es FIJO, aunque vengan informados"() {
        given:
        def autofondeo = FondeadorQuota.builder()
                .funderId(1L).payerId(1L)
                .tipoTasa("TASA_MES_VENCIDO")
                .tarifaPlena(new BigDecimal("3.0"))
                .build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)
        fondeadorQuotaPort.saveAndFlush(_) >> { FondeadorQuota fq -> fq }

        when:
        service.actualizarCupoPropio(
                1L, new BigDecimal("400.00"), new BigDecimal("2.5"), "FIJO", "TASA_MES_VENCIDO", new BigDecimal("3.0"),
                "TODO_EL_PLAZO", null, new BigDecimal("0"))

        then:
        autofondeo.tipoTasa == null
        autofondeo.tarifaPlena == null
    }

    def "actualizarCupoPropio ignora y limpia la tarifaPlena cuando tipoDescuento es FINANCIERO, aunque venga informada"() {
        given:
        def autofondeo = FondeadorQuota.builder().funderId(1L).payerId(1L).tarifaPlena(new BigDecimal("3.0")).build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)
        fondeadorQuotaPort.saveAndFlush(_) >> { FondeadorQuota fq -> fq }

        when:
        service.actualizarCupoPropio(1L, new BigDecimal("400.00"), new BigDecimal("2.5"), "FINANCIERO",
                "TASA_MES_VENCIDO", new BigDecimal("3.0"), "TODO_EL_PLAZO", null, new BigDecimal("0"))

        then:
        autofondeo.tarifaPlena == null
    }

    def "actualizarCupoPropio lanza ReglaDeNegocioException si tipoDescuento no es FIJO y falta tipoTasa"() {
        given:
        def autofondeo = FondeadorQuota.builder().funderId(1L).payerId(1L).build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)

        when:
        service.actualizarCupoPropio(
                1L, new BigDecimal("400.00"), new BigDecimal("2.5"), tipo, null, null, "TODO_EL_PLAZO", null, new BigDecimal("0"))

        then:
        thrown(ReglaDeNegocioException)
        0 * fondeadorQuotaPort.saveAndFlush(_)

        where:
        tipo << ["FINANCIERO", "DINAMICO"]
    }

    def "actualizarCupoPropio lanza ReglaDeNegocioException si tipoDescuento es DINAMICO y falta tarifaPlena"() {
        given:
        def autofondeo = FondeadorQuota.builder().funderId(1L).payerId(1L).build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)

        when:
        service.actualizarCupoPropio(
                1L, new BigDecimal("400.00"), new BigDecimal("2.5"), "DINAMICO", "TASA_EFECTIVA_ANUAL", null,
                "TODO_EL_PLAZO", null, new BigDecimal("0"))

        then:
        thrown(ReglaDeNegocioException)
        0 * fondeadorQuotaPort.saveAndFlush(_)
    }

    def "actualizarCupoPropio lanza PeticionInvalidaException si tipoDescuento no es un valor soportado"() {
        given:
        def autofondeo = FondeadorQuota.builder().funderId(1L).payerId(1L).build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)

        when:
        service.actualizarCupoPropio(
                1L, new BigDecimal("400.00"), new BigDecimal("2.5"), "INVALIDO", null, null, "TODO_EL_PLAZO", null, new BigDecimal("0"))

        then:
        thrown(PeticionInvalidaException)
        0 * fondeadorQuotaPort.saveAndFlush(_)
    }

    def "actualizarCupoPropio lanza PeticionInvalidaException si tipoTasa no es un valor soportado"() {
        given:
        def autofondeo = FondeadorQuota.builder().funderId(1L).payerId(1L).build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)

        when:
        service.actualizarCupoPropio(
                1L, new BigDecimal("400.00"), new BigDecimal("2.5"), "FINANCIERO", "INVALIDA", null,
                "TODO_EL_PLAZO", null, new BigDecimal("0"))

        then:
        thrown(PeticionInvalidaException)
        0 * fondeadorQuotaPort.saveAndFlush(_)
    }

    // ─── porcentajeReserva ────────────────────────────────────────────────────

    def "actualizarCupoPropio guarda el porcentajeReserva informado"() {
        given:
        def autofondeo = FondeadorQuota.builder().funderId(1L).payerId(1L).build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)

        when:
        def resultado = service.actualizarCupoPropio(
                1L, new BigDecimal("400.00"), new BigDecimal("2.5"), "FIJO", null, null, "TODO_EL_PLAZO", null,
                new BigDecimal("5"))

        then:
        1 * fondeadorQuotaPort.saveAndFlush(autofondeo) >> autofondeo
        autofondeo.porcentajeReserva == new BigDecimal("5")
        resultado.porcentajeReserva == new BigDecimal("5")
    }

    // ─── vigenciaDescuento ────────────────────────────────────────────────────

    def "actualizarCupoPropio con vigenciaDescuento TODO_EL_PLAZO ignora y limpia diasVigencia, aunque venga informado"() {
        given:
        def autofondeo = FondeadorQuota.builder().funderId(1L).payerId(1L).diasVigencia(5).build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)
        fondeadorQuotaPort.saveAndFlush(_) >> { FondeadorQuota fq -> fq }

        when:
        def resultado = service.actualizarCupoPropio(
                1L, new BigDecimal("400.00"), new BigDecimal("2.5"), "FIJO", null, null, "TODO_EL_PLAZO", 7, new BigDecimal("0"))

        then:
        autofondeo.vigenciaDescuento == "TODO_EL_PLAZO"
        autofondeo.diasVigencia == null
        resultado.diasVigencia == null
    }

    def "actualizarCupoPropio con vigenciaDescuento DIAS_ANTES_VENCIMIENTO exige y guarda diasVigencia"() {
        given:
        def autofondeo = FondeadorQuota.builder().funderId(1L).payerId(1L).build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)
        fondeadorQuotaPort.saveAndFlush(_) >> { FondeadorQuota fq -> fq }

        when:
        def resultado = service.actualizarCupoPropio(
                1L, new BigDecimal("400.00"), new BigDecimal("2.5"), "FIJO", null, null,
                "DIAS_ANTES_VENCIMIENTO", 3, new BigDecimal("0"))

        then:
        autofondeo.vigenciaDescuento == "DIAS_ANTES_VENCIMIENTO"
        autofondeo.diasVigencia == 3
        resultado.vigenciaDescuento == "DIAS_ANTES_VENCIMIENTO"
        resultado.diasVigencia == 3
    }

    def "actualizarCupoPropio lanza ReglaDeNegocioException si vigenciaDescuento no es TODO_EL_PLAZO y falta diasVigencia"() {
        given:
        def autofondeo = FondeadorQuota.builder().funderId(1L).payerId(1L).build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)

        when:
        service.actualizarCupoPropio(
                1L, new BigDecimal("400.00"), new BigDecimal("2.5"), "FIJO", null, null, vigencia, null, new BigDecimal("0"))

        then:
        thrown(ReglaDeNegocioException)
        0 * fondeadorQuotaPort.saveAndFlush(_)

        where:
        vigencia << ["PRIMEROS_DIAS", "DIAS_ANTES_VENCIMIENTO"]
    }

    def "actualizarCupoPropio lanza PeticionInvalidaException si vigenciaDescuento no es un valor soportado"() {
        given:
        def autofondeo = FondeadorQuota.builder().funderId(1L).payerId(1L).build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)

        when:
        service.actualizarCupoPropio(
                1L, new BigDecimal("400.00"), new BigDecimal("2.5"), "FIJO", null, null, "INVALIDA", null, new BigDecimal("0"))

        then:
        thrown(PeticionInvalidaException)
        0 * fondeadorQuotaPort.saveAndFlush(_)
    }

    // ─── actualizarEndosoGrupal ───────────────────────────────────────────────

    def "actualizarEndosoGrupal habilita el endoso agrupado del autofondeo"() {
        given:
        def autofondeo = FondeadorQuota.builder().funderId(1L).payerId(1L).endosoGrupal(false).build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)

        when:
        def resultado = service.actualizarEndosoGrupal(1L, true)

        then:
        1 * fondeadorQuotaPort.saveAndFlush(autofondeo) >> autofondeo
        autofondeo.endosoGrupal
        resultado.endosoGrupal
    }

    def "actualizarEndosoGrupal lanza RecursoNoEncontradoException si la empresa no es fondeadora de sí misma"() {
        given:
        fondeadorQuotaPort.findByFunderIdAndPayerId(9L, 9L) >> Optional.empty()

        when:
        service.actualizarEndosoGrupal(9L, true)

        then:
        thrown(RecursoNoEncontradoException)
    }

    // ─── actualizarRentabilidadMinima ─────────────────────────────────────────

    def "actualizarRentabilidadMinima activa y guarda el valor (Efectivo Anual)"() {
        given:
        def autofondeo = FondeadorQuota.builder()
                .funderId(1L).payerId(1L)
                .rentabilidadMinimaActiva(false)
                .build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)

        when:
        def resultado = service.actualizarRentabilidadMinima(1L, true, new BigDecimal("12.5"))

        then:
        1 * fondeadorQuotaPort.saveAndFlush(autofondeo) >> autofondeo
        autofondeo.rentabilidadMinimaActiva
        autofondeo.rentabilidadMinima == new BigDecimal("12.5")
        resultado.rentabilidadMinimaActiva
        resultado.rentabilidadMinima == new BigDecimal("12.5")
    }

    def "actualizarRentabilidadMinima desactiva y limpia el valor"() {
        given:
        def autofondeo = FondeadorQuota.builder()
                .funderId(1L).payerId(1L)
                .rentabilidadMinimaActiva(true)
                .rentabilidadMinima(new BigDecimal("12.5"))
                .build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)

        when:
        def resultado = service.actualizarRentabilidadMinima(1L, false, null)

        then:
        1 * fondeadorQuotaPort.saveAndFlush(autofondeo) >> autofondeo
        !autofondeo.rentabilidadMinimaActiva
        autofondeo.rentabilidadMinima == null
        !resultado.rentabilidadMinimaActiva
    }

    def "actualizarRentabilidadMinima lanza ReglaDeNegocioException si se activa sin indicar el valor"() {
        given:
        def autofondeo = FondeadorQuota.builder().funderId(1L).payerId(1L).build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)

        when:
        service.actualizarRentabilidadMinima(1L, true, null)

        then:
        thrown(ReglaDeNegocioException)
        0 * fondeadorQuotaPort.saveAndFlush(_)
    }

    def "actualizarRentabilidadMinima lanza RecursoNoEncontradoException si la empresa no es fondeadora de sí misma"() {
        given:
        fondeadorQuotaPort.findByFunderIdAndPayerId(9L, 9L) >> Optional.empty()

        when:
        service.actualizarRentabilidadMinima(9L, true, new BigDecimal("12.5"))

        then:
        thrown(RecursoNoEncontradoException)
    }

    // ─── restablecerCupo ──────────────────────────────────────────────────────

    def "restablecerCupo libera el cupo consumido, sin modificar cupoPropio ni el resto de la configuración"() {
        given:
        def autofondeo = FondeadorQuota.builder()
                .funderId(1L).payerId(1L)
                .payerQuota(new BigDecimal("10000.00"))
                .payerCurrentQuota(new BigDecimal("490.00"))
                .payerRate(new BigDecimal("2.5"))
                .tipoDescuento("FIJO")
                .build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("50000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)

        when:
        def resultado = service.restablecerCupo(1L)

        then:
        1 * fondeadorQuotaPort.saveAndFlush(autofondeo) >> autofondeo
        autofondeo.payerQuota == new BigDecimal("10000.00")
        autofondeo.payerCurrentQuota == new BigDecimal("10000.00")
        autofondeo.payerRate == new BigDecimal("2.5")
        autofondeo.tipoDescuento == "FIJO"
        resultado.cupoPropio == new BigDecimal("10000.00")
        resultado.cupoDisponible == new BigDecimal("10000.00")
    }

    def "restablecerCupo trata un cupoPropio nulo como cero"() {
        given:
        def autofondeo = FondeadorQuota.builder()
                .funderId(1L).payerId(1L)
                .payerCurrentQuota(new BigDecimal("490.00"))
                .build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("50000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)
        fondeadorQuotaPort.saveAndFlush(_) >> { FondeadorQuota fq -> fq }

        when:
        service.restablecerCupo(1L)

        then:
        autofondeo.payerCurrentQuota == BigDecimal.ZERO
    }

    def "restablecerCupo lanza RecursoNoEncontradoException si la empresa no es fondeadora de sí misma"() {
        given:
        fondeadorQuotaPort.findByFunderIdAndPayerId(9L, 9L) >> Optional.empty()

        when:
        service.restablecerCupo(9L)

        then:
        thrown(RecursoNoEncontradoException)
    }
}