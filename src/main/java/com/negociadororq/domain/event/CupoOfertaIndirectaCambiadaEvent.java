package com.negociadororq.domain.event;

import java.math.BigDecimal;

/**
 * Fase 28d (P-19, decisión de John J-09): el fondeador cambió el cupo por proveedor de su oferta indirecta
 * (ms-registroempresa-neg, topic {@code cupoOfertaIndirectaCambiadaTopic}). Los proveedores que ya negociaron esa oferta
 * reciben el ajuste.
 *
 * @param offerId  oferta
 * @param version  versión del cupo de la oferta tras el cambio (cupo_version)
 * @param anterior cupo por proveedor antes del cambio (informativo)
 * @param nuevo    cupo por proveedor después del cambio
 */
public record CupoOfertaIndirectaCambiadaEvent(Long offerId, long version, BigDecimal anterior, BigDecimal nuevo) {
}
