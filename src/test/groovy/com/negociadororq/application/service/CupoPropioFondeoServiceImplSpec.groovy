package com.negociadororq.application.service

import com.negociadororq.application.exception.RecursoNoEncontradoException
import com.negociadororq.application.exception.ReglaDeNegocioException
import com.negociadororq.application.port.output.EmpresaQuotaOutputPort
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort
import com.negociadororq.domain.model.EmpresaQuota
import com.negociadororq.domain.model.FondeadorQuota
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
        def resultado = service.actualizarCupoPropio(1L, new BigDecimal("500.00"))

        then:
        1 * fondeadorQuotaPort.save(autofondeo) >> autofondeo
        autofondeo.payerQuota == new BigDecimal("500.00")
        autofondeo.payerCurrentQuota == new BigDecimal("400.00")
        resultado.cupoPropio == new BigDecimal("500.00")
        resultado.cupoDisponible == new BigDecimal("400.00")
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
        fondeadorQuotaPort.save(_) >> { FondeadorQuota fq -> fq }

        when:
        service.actualizarCupoPropio(1L, new BigDecimal("100.00"))

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
        service.actualizarCupoPropio(1L, new BigDecimal("1500.00"))

        then:
        thrown(ReglaDeNegocioException)
        0 * fondeadorQuotaPort.save(_)
    }

    def "actualizarCupoPropio lanza RecursoNoEncontradoException si la empresa no es fondeadora de sí misma"() {
        given:
        fondeadorQuotaPort.findByFunderIdAndPayerId(9L, 9L) >> Optional.empty()

        when:
        service.actualizarCupoPropio(9L, new BigDecimal("100.00"))

        then:
        thrown(RecursoNoEncontradoException)
    }

    def "actualizarCupoPropio maneja quota y disponible nulos como cero consumido"() {
        given:
        def autofondeo = FondeadorQuota.builder().funderId(1L).payerId(1L).build()
        def empresaQuota = EmpresaQuota.builder().companyId(1L).quota(new BigDecimal("1000.00")).build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 1L) >> Optional.of(autofondeo)
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(empresaQuota)
        fondeadorQuotaPort.save(_) >> { FondeadorQuota fq -> fq }

        when:
        service.actualizarCupoPropio(1L, new BigDecimal("400.00"))

        then:
        autofondeo.payerQuota == new BigDecimal("400.00")
        autofondeo.payerCurrentQuota == new BigDecimal("400.00")
    }
}