package com.negociadororq.application.port.output;

import java.util.Optional;

import com.negociadororq.domain.model.IndirectFunderOffer;

/**
 * Puerto de solo lectura para ofertas de cupo compartido fondeador→pagador
 * ("factoring indirecto vía pagador"). La creación/aprobación de estas
 * ofertas vive en ms-registroempresa-neg; aquí solo se consultan para
 * validar y consumir cupo durante la negociación.
 */
public interface IndirectFunderOfferOutputPort {

    /**
     * Busca la oferta vigente para negociar: debe estar APROBADA y activa.
     *
     * @param funderId id de la empresa fondeadora
     * @param payerId  id de la empresa pagadora
     * @return oferta si existe y es utilizable
     */
    Optional<IndirectFunderOffer> findActivaAprobada(Long funderId, Long payerId);
}
