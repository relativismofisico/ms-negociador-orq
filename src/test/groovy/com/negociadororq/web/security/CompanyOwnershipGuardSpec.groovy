package com.negociadororq.web.security

import com.negociadororq.application.exception.AccionNoPermitidaException
import com.negociadororq.application.exception.RecursoNoEncontradoException
import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.IndirectFunderOfferOutputPort
import com.negociadororq.application.port.output.UserOutputPort
import com.negociadororq.domain.model.Company
import com.negociadororq.domain.model.IndirectFunderOffer
import com.negociadororq.domain.model.User
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification

/**
 * Ownership horizontal de los endpoints con id de empresa en el path
 * (cupo-propio-fondeo, cupo de oferta indirecta, recarga de cupo). La empresa
 * del llamador se resuelve por user.company_ide; ADMINISTRADOR bypasa.
 */
class CompanyOwnershipGuardSpec extends Specification {

    UserOutputPort userPort = Mock()
    IndirectFunderOfferOutputPort indirectFunderOfferPort = Mock()
    CompanyOutputPort companyPort = Mock()

    CompanyOwnershipGuard guard = new CompanyOwnershipGuard(userPort, indirectFunderOfferPort, companyPort)

    static final long PROPIA = 100L
    static final long AJENA = 555L

    void cleanup() {
        SecurityContextHolder.clearContext()
    }

    private static void autenticar(String username, String rol) {
        def auth = new UsernamePasswordAuthenticationToken(username, null, [new SimpleGrantedAuthority(rol)])
        SecurityContextHolder.getContext().setAuthentication(auth)
    }

    // ── exigirEmpresaPropia ──────────────────────────────────────────────────

    def "exigirEmpresaPropia: EMPRESA sobre su propia empresa pasa"() {
        given:
        autenticar("NIT-A", "EMPRESA")
        userPort.findByUsername("NIT-A") >> Optional.of(User.builder().companyIde(PROPIA).build())

        when:
        guard.exigirEmpresaPropia(PROPIA)

        then:
        noExceptionThrown()
    }

    def "exigirEmpresaPropia: OPERARIO sobre su propia empresa pasa (sub = username, no NIT)"() {
        given:
        autenticar("opshadow-9-100", "OPERARIO")
        userPort.findByUsername("opshadow-9-100") >> Optional.of(User.builder().companyIde(PROPIA).build())

        when:
        guard.exigirEmpresaPropia(PROPIA)

        then:
        noExceptionThrown()
    }

    def "exigirEmpresaPropia: EMPRESA sobre otra empresa recibe 403"() {
        given:
        autenticar("NIT-A", "EMPRESA")
        userPort.findByUsername("NIT-A") >> Optional.of(User.builder().companyIde(PROPIA).build())

        when:
        guard.exigirEmpresaPropia(AJENA)

        then:
        thrown(AccionNoPermitidaException)
    }

    def "exigirEmpresaPropia: ADMINISTRADOR bypasa sin resolver la empresa del llamador"() {
        given:
        autenticar("admin", "ADMINISTRADOR")

        when:
        guard.exigirEmpresaPropia(AJENA)

        then:
        noExceptionThrown()
        0 * userPort.findByUsername(_)
    }

    def "exigirEmpresaPropia: username que no resuelve a ninguna empresa -> 403 (fail-closed)"() {
        given:
        autenticar("fantasma", "EMPRESA")
        userPort.findByUsername("fantasma") >> Optional.empty()

        when:
        guard.exigirEmpresaPropia(PROPIA)

        then:
        thrown(AccionNoPermitidaException)
    }

    def "exigirEmpresaPropia: sin autenticacion en el contexto -> 403"() {
        given:
        SecurityContextHolder.clearContext()

        when:
        guard.exigirEmpresaPropia(PROPIA)

        then:
        thrown(AccionNoPermitidaException)
    }

    // ── exigirParteDeOfertaIndirecta ─────────────────────────────────────────

    def "exigirParteDeOfertaIndirecta: el proveedor / fondeador / pagador pasan"() {
        given:
        autenticar("NIT-A", "EMPRESA")
        userPort.findByUsername("NIT-A") >> Optional.of(User.builder().companyIde(propiaComoParte).build())

        when:
        guard.exigirParteDeOfertaIndirecta(10L, 20L, 30L)

        then:
        noExceptionThrown()

        where:
        propiaComoParte << [10L, 20L, 30L]
    }

    def "exigirParteDeOfertaIndirecta: una empresa ajena a la oferta recibe 403"() {
        given:
        autenticar("NIT-A", "EMPRESA")
        userPort.findByUsername("NIT-A") >> Optional.of(User.builder().companyIde(AJENA).build())

        when:
        guard.exigirParteDeOfertaIndirecta(10L, 20L, 30L)

        then:
        thrown(AccionNoPermitidaException)
    }

    def "exigirParteDeOfertaIndirecta: ADMINISTRADOR bypasa"() {
        given:
        autenticar("admin", "ADMINISTRADOR")

        when:
        guard.exigirParteDeOfertaIndirecta(10L, 20L, 30L)

        then:
        noExceptionThrown()
        0 * userPort.findByUsername(_)
    }

    // ── exigirFondeadorDeOferta ──────────────────────────────────────────────

    def "exigirFondeadorDeOferta: solo el fondeador dueño de la oferta pasa"() {
        given:
        autenticar("NIT-A", "EMPRESA")
        indirectFunderOfferPort.findById(7L) >> Optional.of(IndirectFunderOffer.builder().id(7L).funderId(PROPIA).payerId(AJENA).build())
        userPort.findByUsername("NIT-A") >> Optional.of(User.builder().companyIde(PROPIA).build())

        when:
        guard.exigirFondeadorDeOferta(7L)

        then:
        noExceptionThrown()
    }

    def "exigirFondeadorDeOferta: la pagadora (no el fondeador) de la oferta recibe 403"() {
        given:
        autenticar("NIT-B", "EMPRESA")
        indirectFunderOfferPort.findById(7L) >> Optional.of(IndirectFunderOffer.builder().id(7L).funderId(PROPIA).payerId(AJENA).build())
        userPort.findByUsername("NIT-B") >> Optional.of(User.builder().companyIde(AJENA).build())

        when:
        guard.exigirFondeadorDeOferta(7L)

        then:
        thrown(AccionNoPermitidaException)
    }

    def "exigirFondeadorDeOferta: oferta inexistente -> 404"() {
        given:
        autenticar("NIT-A", "EMPRESA")
        indirectFunderOfferPort.findById(999L) >> Optional.empty()

        when:
        guard.exigirFondeadorDeOferta(999L)

        then:
        thrown(RecursoNoEncontradoException)
    }

    def "exigirFondeadorDeOferta: ADMINISTRADOR bypasa sin cargar la oferta"() {
        given:
        autenticar("admin", "ADMINISTRADOR")

        when:
        guard.exigirFondeadorDeOferta(7L)

        then:
        noExceptionThrown()
        0 * indirectFunderOfferPort.findById(_)
        0 * userPort.findByUsername(_)
    }

    // ── exigirActorEsLlamador (suplantacion en negociar / endoso / OTP) ───────

    def "exigirActorEsLlamador: la empresa y el operador propios pasan"() {
        given:
        autenticar("NIT-A", "EMPRESA")
        userPort.findByUsername("NIT-A") >> Optional.of(User.builder().companyIde(PROPIA).build())
        companyPort.findByNit("NIT-A") >> Optional.of(new Company(ideCompany: PROPIA, nit: "NIT-A"))
        userPort.findByUsername("op-a") >> Optional.of(User.builder().companyIde(PROPIA).build())

        when:
        guard.exigirActorEsLlamador("NIT-A", "op-a")

        then:
        noExceptionThrown()
    }

    def "exigirActorEsLlamador: el asesor operando como sombra (opshadow) pasa"() {
        given:
        autenticar("opshadow-42-100", "OPERARIO")
        // el shadow user tiene company_ide = empresa cliente
        userPort.findByUsername("opshadow-42-100") >> Optional.of(User.builder().companyIde(PROPIA).build())
        companyPort.findByNit("NIT-A") >> Optional.of(new Company(ideCompany: PROPIA, nit: "NIT-A"))

        when:
        guard.exigirActorEsLlamador("NIT-A", "opshadow-42-100")

        then:
        noExceptionThrown()
    }

    def "exigirActorEsLlamador: rutEmpresa de OTRA empresa (suplantacion) -> 403"() {
        given:
        autenticar("NIT-A", "EMPRESA")
        userPort.findByUsername("NIT-A") >> Optional.of(User.builder().companyIde(PROPIA).build())
        companyPort.findByNit("NIT-B") >> Optional.of(new Company(ideCompany: AJENA, nit: "NIT-B"))

        when:
        guard.exigirActorEsLlamador("NIT-B", "op-a")

        then:
        thrown(AccionNoPermitidaException)
    }

    def "exigirActorEsLlamador: rutOperador de otra empresa -> 403 (aunque la empresa actuante sea propia)"() {
        given:
        autenticar("NIT-A", "EMPRESA")
        userPort.findByUsername("NIT-A") >> Optional.of(User.builder().companyIde(PROPIA).build())
        companyPort.findByNit("NIT-A") >> Optional.of(new Company(ideCompany: PROPIA, nit: "NIT-A"))
        userPort.findByUsername("op-b") >> Optional.of(User.builder().companyIde(AJENA).build())

        when:
        guard.exigirActorEsLlamador("NIT-A", "op-b")

        then:
        thrown(AccionNoPermitidaException)
    }

    def "exigirActorEsLlamador: NIT actuante inexistente -> 403"() {
        given:
        autenticar("NIT-A", "EMPRESA")
        userPort.findByUsername("NIT-A") >> Optional.of(User.builder().companyIde(PROPIA).build())
        companyPort.findByNit("NIT-X") >> Optional.empty()

        when:
        guard.exigirActorEsLlamador("NIT-X", "op-a")

        then:
        thrown(AccionNoPermitidaException)
    }

    def "exigirActorEsLlamador: sub que no resuelve a empresa -> 403 (fail-closed)"() {
        given:
        autenticar("fantasma", "EMPRESA")
        userPort.findByUsername("fantasma") >> Optional.empty()

        when:
        guard.exigirActorEsLlamador("NIT-A", "op-a")

        then:
        thrown(AccionNoPermitidaException)
        0 * companyPort.findByNit(_)
    }

    def "exigirActorEsLlamador: ADMINISTRADOR bypasa sin resolver nada"() {
        given:
        autenticar("admin", "ADMINISTRADOR")

        when:
        guard.exigirActorEsLlamador("NIT-B", "op-b")

        then:
        noExceptionThrown()
        0 * companyPort.findByNit(_)
        0 * userPort.findByUsername(_)
    }
}
