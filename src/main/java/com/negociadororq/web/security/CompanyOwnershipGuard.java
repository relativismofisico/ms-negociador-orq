package com.negociadororq.web.security;

import java.util.logging.Logger;

import com.negociadororq.application.exception.AccionNoPermitidaException;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.IndirectFunderOfferOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.IndirectFunderOffer;
import com.negociadororq.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Guard de ownership horizontal para los endpoints con id de empresa en el path.
 *
 * <p>El {@code @PreAuthorize("@sec.tieneRol(...)")} de los controllers solo valida
 * el grupo de rol; no verifica que el llamador tenga relación con la empresa del
 * path. Sin este guard, cualquier EMPRESA/OPERARIO autenticado puede leer y
 * <em>mutar</em> el cupo de autofondeo, tasa y reserva de otra empresa
 * ({@code /companies/{companyId}/cupo-propio-fondeo}), recargar el cupo consumido
 * de un proveedor cualquiera contra una oferta ajena, o consultar los términos de
 * fondeo de terceros — mutación financiera directa, sin aprobación de por medio.</p>
 *
 * <p>La empresa del llamador se resuelve por {@code user.company_ide} (no por el
 * NIT del {@code sub}): el {@code sub} de un OPERARIO es su username, no el NIT.
 * ADMINISTRADOR pasa siempre (Integrity opera en nombre de cualquiera).</p>
 *
 * <p>{@link #exigirActorEsLlamador(String, String)} cierra además la suplantación
 * en el flujo transaccional (negociar / endoso / OTP): esos endpoints toman la
 * empresa actuante y el operador del <em>body</em> ({@code rutEmpresa}/
 * {@code rutActor}/{@code rutOperador}) sin compararlos con el JWT.</p>
 */
@Component
@RequiredArgsConstructor
public class CompanyOwnershipGuard {

    private static final Logger LOG = Logger.getLogger(CompanyOwnershipGuard.class.getName());

    private static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";

    private final UserOutputPort userPort;
    private final IndirectFunderOfferOutputPort indirectFunderOfferPort;
    private final CompanyOutputPort companyPort;

    /**
     * El llamador solo puede operar sobre su propia empresa
     * ({@code /companies/{companyId}/cupo-propio-fondeo}). ADMINISTRADOR pasa.
     *
     * @throws AccionNoPermitidaException (→ 403) si el llamador no es esa empresa.
     */
    public void exigirEmpresaPropia(final Long companyId) {
        if (esAdministrador()) {
            return;
        }
        final Long propia = empresaDelLlamador();
        if (propia == null || !propia.equals(companyId)) {
            denegar(propia, companyId);
        }
    }

    /**
     * El llamador debe ser una de las tres partes de una oferta de fondeo
     * indirecta (fondeador, pagador o proveedor) para consultar su cupo/tasa.
     * ADMINISTRADOR pasa.
     */
    public void exigirParteDeOfertaIndirecta(final Long funderId, final Long payerId, final Long supplierId) {
        if (esAdministrador()) {
            return;
        }
        final Long propia = empresaDelLlamador();
        final boolean esParte = propia != null
                && (propia.equals(funderId) || propia.equals(payerId) || propia.equals(supplierId));
        if (!esParte) {
            denegar(propia, supplierId);
        }
    }

    /**
     * La recarga de cupo de un proveedor solo la puede disparar la empresa
     * fondeadora dueña de la oferta. ADMINISTRADOR pasa.
     *
     * @throws RecursoNoEncontradoException (→ 404) si la oferta no existe.
     * @throws AccionNoPermitidaException (→ 403) si el llamador no es el fondeador.
     */
    public void exigirFondeadorDeOferta(final Long offerId) {
        if (esAdministrador()) {
            return;
        }
        final IndirectFunderOffer oferta = indirectFunderOfferPort.findById(offerId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la oferta de fondeo indirecta " + offerId, "OFERTA_INDIRECTA_NO_ENCONTRADA"));
        final Long propia = empresaDelLlamador();
        if (propia == null || !propia.equals(oferta.getFunderId())) {
            denegar(propia, oferta.getFunderId());
        }
    }

    /**
     * La empresa actuante y el operador declarados en el body deben pertenecer a
     * la empresa del llamador. Para {@code POST /negociar/factura},
     * {@code POST /negociaciones/{id}/endosos}, {@code solicitar-otp} y
     * {@code confirmar-otp}. ADMINISTRADOR pasa.
     *
     * @param rutActorNit     NIT de la empresa actuante (rutEmpresa / rutActor del body)
     * @param rutOperadorUser username del operador (rutOperador del body)
     * @throws AccionNoPermitidaException (→ 403) si actor u operador no son de la empresa del llamador.
     */
    public void exigirActorEsLlamador(final String rutActorNit, final String rutOperadorUser) {
        if (esAdministrador()) {
            return;
        }
        final Long empresaLlamador = empresaDelLlamador();
        if (empresaLlamador == null) {
            denegar(null, null);
        }

        final Long empresaActor = companyPort.findByNit(rutActorNit)
                .map(Company::getIdeCompany)
                .orElse(null);
        if (empresaActor == null || !empresaActor.equals(empresaLlamador)) {
            denegar(empresaLlamador, empresaActor);
        }

        final Long empresaOperador = userPort.findByUsername(rutOperadorUser)
                .map(User::getCompanyIde)
                .orElse(null);
        if (empresaOperador == null || !empresaOperador.equals(empresaLlamador)) {
            denegar(empresaLlamador, empresaOperador);
        }
    }

    private boolean esAdministrador() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROL_ADMINISTRADOR::equals);
    }

    private Long empresaDelLlamador() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userPort.findByUsername(authentication.getName())
                .map(User::getCompanyIde)
                .orElse(null);
    }

    private void denegar(final Long empresaLlamador, final Long empresaRecurso) {
        LOG.warning(String.format(
                "[CompanyOwnershipGuard] Acceso denegado (empresa del llamador=%s, empresa del recurso=%s)",
                empresaLlamador, empresaRecurso));
        throw new AccionNoPermitidaException("No tiene permisos para operar sobre esta empresa.");
    }
}
