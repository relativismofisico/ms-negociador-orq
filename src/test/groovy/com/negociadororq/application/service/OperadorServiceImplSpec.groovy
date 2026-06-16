package com.negociadororq.application.service

import com.negociadororq.application.port.output.EmpresaQuotaOutputPort
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort
import com.negociadororq.application.port.output.UserQuotaOutputPort
import com.negociadororq.domain.model.EmpresaQuota
import com.negociadororq.domain.model.FondeadorQuota
import com.negociadororq.domain.model.UserQuota
import spock.lang.Specification

import java.math.BigDecimal

class OperadorServiceImplSpec extends Specification {

    UserQuotaOutputPort userQuotaPort = Mock()
    EmpresaQuotaOutputPort empresaQuotaPort = Mock()
    FondeadorQuotaOutputPort fondeadorQuotaPort = Mock()

    OperadorServiceImpl service = new OperadorServiceImpl(userQuotaPort, empresaQuotaPort, fondeadorQuotaPort)

    // ─── obtenerCupoDisponible ────────────────────────────────────────────────

    def "obtenerCupoDisponible retorna currentQuota del usuario"() {
        given:
        def quota = UserQuota.builder().userId(1L).currentQuota(new BigDecimal("5000.00")).build()
        userQuotaPort.findByUserId(1L) >> Optional.of(quota)

        when:
        def resultado = service.obtenerCupoDisponible(1L)

        then:
        resultado == new BigDecimal("5000.00")
    }

    def "obtenerCupoDisponible lanza RuntimeException si no existe cuota"() {
        given:
        userQuotaPort.findByUserId(99L) >> Optional.empty()

        when:
        service.obtenerCupoDisponible(99L)

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("99")
    }

    // ─── descontarCupo ────────────────────────────────────────────────────────

    def "descontarCupo descuenta correctamente cuando hay cupo suficiente"() {
        given:
        def quota = UserQuota.builder().userId(1L).currentQuota(new BigDecimal("1000.00")).build()
        userQuotaPort.findByUserId(1L) >> Optional.of(quota)

        when:
        service.descontarCupo(1L, new BigDecimal("300.00"))

        then:
        quota.currentQuota == new BigDecimal("700.00")
        1 * userQuotaPort.save(quota)
    }

    def "descontarCupo lanza RuntimeException si no existe cuota"() {
        given:
        userQuotaPort.findByUserId(5L) >> Optional.empty()

        when:
        service.descontarCupo(5L, new BigDecimal("100"))

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("5")
    }

    def "descontarCupo lanza RuntimeException si cupo es insuficiente"() {
        given:
        def quota = UserQuota.builder().userId(1L).currentQuota(new BigDecimal("50.00")).build()
        userQuotaPort.findByUserId(1L) >> Optional.of(quota)

        when:
        service.descontarCupo(1L, new BigDecimal("100.00"))

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("insuficiente")
    }

    def "descontarCupo con cupo exactamente igual al monto descuenta a cero"() {
        given:
        def quota = UserQuota.builder().userId(2L).currentQuota(new BigDecimal("100.00")).build()
        userQuotaPort.findByUserId(2L) >> Optional.of(quota)

        when:
        service.descontarCupo(2L, new BigDecimal("100.00"))

        then:
        quota.currentQuota == BigDecimal.ZERO
        1 * userQuotaPort.save(quota)
    }

    // ─── revertirCupo ─────────────────────────────────────────────────────────

    def "revertirCupo suma valor correctamente"() {
        given:
        def quota = UserQuota.builder().userId(1L).currentQuota(new BigDecimal("200.00")).build()
        userQuotaPort.findByUserId(1L) >> Optional.of(quota)

        when:
        service.revertirCupo(1L, new BigDecimal("150.00"))

        then:
        quota.currentQuota == new BigDecimal("350.00")
        1 * userQuotaPort.save(quota)
    }

    def "revertirCupo lanza RuntimeException si no existe cuota"() {
        given:
        userQuotaPort.findByUserId(10L) >> Optional.empty()

        when:
        service.revertirCupo(10L, new BigDecimal("100"))

        then:
        thrown(RuntimeException)
    }

    // ─── revertirCupoUsuario ──────────────────────────────────────────────────

    def "revertirCupoUsuario suma valor si existe cuota"() {
        given:
        def quota = UserQuota.builder().userId(1L).currentQuota(new BigDecimal("100.00")).build()
        userQuotaPort.findByUserId(1L) >> Optional.of(quota)

        when:
        service.revertirCupoUsuario(1L, new BigDecimal("50.00"))

        then:
        quota.currentQuota == new BigDecimal("150.00")
        1 * userQuotaPort.save(quota)
    }

    def "revertirCupoUsuario no hace nada si no existe cuota"() {
        given:
        userQuotaPort.findByUserId(99L) >> Optional.empty()

        when:
        service.revertirCupoUsuario(99L, new BigDecimal("50.00"))

        then:
        0 * userQuotaPort.save(_)
    }

    def "revertirCupoUsuario limita a cero si valor resultante es negativo"() {
        given:
        // currentQuota=10, suma -20 -> max(10+(-20), 0) = max(-10, 0) = 0
        def quota = UserQuota.builder().userId(1L).currentQuota(new BigDecimal("10.00")).build()
        userQuotaPort.findByUserId(1L) >> Optional.of(quota)

        when:
        service.revertirCupoUsuario(1L, new BigDecimal("-20.00"))

        then:
        quota.currentQuota == BigDecimal.ZERO
        1 * userQuotaPort.save(quota)
    }

    // ─── revertirCupoEmpresa ──────────────────────────────────────────────────

    def "revertirCupoEmpresa suma valor absoluto sin exceder quota maxima"() {
        given:
        def eq = EmpresaQuota.builder()
                .companyId(1L)
                .quota(new BigDecimal("1000.00"))
                .currentQuota(new BigDecimal("500.00"))
                .build()
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(eq)

        when:
        service.revertirCupoEmpresa(1L, new BigDecimal("300.00"))

        then:
        eq.currentQuota == new BigDecimal("800.00")
        1 * empresaQuotaPort.saveAndFlush(eq)
    }

    def "revertirCupoEmpresa limita al maximo cuando suma supera quota"() {
        given:
        def eq = EmpresaQuota.builder()
                .companyId(1L)
                .quota(new BigDecimal("1000.00"))
                .currentQuota(new BigDecimal("800.00"))
                .build()
        empresaQuotaPort.findByCompanyId(1L) >> Optional.of(eq)

        when:
        service.revertirCupoEmpresa(1L, new BigDecimal("500.00"))

        then:
        eq.currentQuota == new BigDecimal("1000.00")
        1 * empresaQuotaPort.saveAndFlush(eq)
    }

    def "revertirCupoEmpresa convierte valor negativo a absoluto"() {
        given:
        def eq = EmpresaQuota.builder()
                .companyId(2L)
                .quota(new BigDecimal("500.00"))
                .currentQuota(new BigDecimal("100.00"))
                .build()
        empresaQuotaPort.findByCompanyId(2L) >> Optional.of(eq)

        when:
        service.revertirCupoEmpresa(2L, new BigDecimal("-200.00"))

        then:
        eq.currentQuota == new BigDecimal("300.00")
    }

    // ─── revertirCupoFondeadorConfirming ─────────────────────────────────────

    def "revertirCupoFondeadorConfirming suma payerCurrentQuota sin exceder limite"() {
        given:
        def fq = FondeadorQuota.builder()
                .funderId(1L).payerId(2L)
                .payerQuota(new BigDecimal("1000.00"))
                .payerCurrentQuota(new BigDecimal("300.00"))
                .build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 2L) >> Optional.of(fq)

        when:
        service.revertirCupoFondeadorConfirming(1L, 2L, new BigDecimal("200.00"))

        then:
        fq.payerCurrentQuota == new BigDecimal("500.00")
        1 * fondeadorQuotaPort.save(fq)
    }

    def "revertirCupoFondeadorConfirming limita al maximo payerQuota"() {
        given:
        def fq = FondeadorQuota.builder()
                .funderId(1L).payerId(2L)
                .payerQuota(new BigDecimal("500.00"))
                .payerCurrentQuota(new BigDecimal("400.00"))
                .build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 2L) >> Optional.of(fq)

        when:
        service.revertirCupoFondeadorConfirming(1L, 2L, new BigDecimal("300.00"))

        then:
        fq.payerCurrentQuota == new BigDecimal("500.00")
    }

    def "revertirCupoFondeadorConfirming funciona cuando payerCurrentQuota es null"() {
        given:
        def fq = FondeadorQuota.builder()
                .funderId(1L).payerId(2L)
                .payerQuota(new BigDecimal("1000.00"))
                .payerCurrentQuota(null)
                .build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 2L) >> Optional.of(fq)

        when:
        service.revertirCupoFondeadorConfirming(1L, 2L, new BigDecimal("100.00"))

        then:
        fq.payerCurrentQuota == new BigDecimal("100.00")
    }

    def "revertirCupoFondeadorConfirming lanza RuntimeException si no existe"() {
        given:
        fondeadorQuotaPort.findByFunderIdAndPayerId(9L, 9L) >> Optional.empty()

        when:
        service.revertirCupoFondeadorConfirming(9L, 9L, new BigDecimal("100"))

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("9")
    }

    def "revertirCupoFondeadorConfirming sin payerQuota no limita"() {
        given:
        def fq = FondeadorQuota.builder()
                .funderId(1L).payerId(2L)
                .payerQuota(null)
                .payerCurrentQuota(new BigDecimal("200.00"))
                .build()
        fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 2L) >> Optional.of(fq)

        when:
        service.revertirCupoFondeadorConfirming(1L, 2L, new BigDecimal("500.00"))

        then:
        fq.payerCurrentQuota == new BigDecimal("700.00")
    }

    // ─── revertirCupoFondeadorFactoring ──────────────────────────────────────

    def "revertirCupoFondeadorFactoring suma supplierCurrentQuota correctamente"() {
        given:
        def fq = FondeadorQuota.builder()
                .funderId(1L).supplierId(3L)
                .supplierQuota(new BigDecimal("2000.00"))
                .supplierCurrentQuota(new BigDecimal("500.00"))
                .build()
        fondeadorQuotaPort.findByFunderIdAndSupplierId(1L, 3L) >> Optional.of(fq)

        when:
        service.revertirCupoFondeadorFactoring(1L, 3L, new BigDecimal("400.00"))

        then:
        fq.supplierCurrentQuota == new BigDecimal("900.00")
        1 * fondeadorQuotaPort.save(fq)
    }

    def "revertirCupoFondeadorFactoring limita al maximo supplierQuota"() {
        given:
        def fq = FondeadorQuota.builder()
                .funderId(1L).supplierId(3L)
                .supplierQuota(new BigDecimal("1000.00"))
                .supplierCurrentQuota(new BigDecimal("900.00"))
                .build()
        fondeadorQuotaPort.findByFunderIdAndSupplierId(1L, 3L) >> Optional.of(fq)

        when:
        service.revertirCupoFondeadorFactoring(1L, 3L, new BigDecimal("500.00"))

        then:
        fq.supplierCurrentQuota == new BigDecimal("1000.00")
    }

    def "revertirCupoFondeadorFactoring cuando supplierCurrentQuota es null inicia en cero"() {
        given:
        def fq = FondeadorQuota.builder()
                .funderId(1L).supplierId(3L)
                .supplierQuota(new BigDecimal("1000.00"))
                .supplierCurrentQuota(null)
                .build()
        fondeadorQuotaPort.findByFunderIdAndSupplierId(1L, 3L) >> Optional.of(fq)

        when:
        service.revertirCupoFondeadorFactoring(1L, 3L, new BigDecimal("300.00"))

        then:
        fq.supplierCurrentQuota == new BigDecimal("300.00")
    }

    def "revertirCupoFondeadorFactoring lanza RuntimeException si no existe"() {
        given:
        fondeadorQuotaPort.findByFunderIdAndSupplierId(8L, 8L) >> Optional.empty()

        when:
        service.revertirCupoFondeadorFactoring(8L, 8L, new BigDecimal("100"))

        then:
        thrown(RuntimeException)
    }

    def "revertirCupoFondeadorFactoring sin supplierQuota no limita"() {
        given:
        def fq = FondeadorQuota.builder()
                .funderId(1L).supplierId(3L)
                .supplierQuota(null)
                .supplierCurrentQuota(new BigDecimal("100.00"))
                .build()
        fondeadorQuotaPort.findByFunderIdAndSupplierId(1L, 3L) >> Optional.of(fq)

        when:
        service.revertirCupoFondeadorFactoring(1L, 3L, new BigDecimal("999.00"))

        then:
        fq.supplierCurrentQuota == new BigDecimal("1099.00")
    }
}