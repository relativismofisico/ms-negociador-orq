package com.negociadororq.application.service;

import java.math.BigDecimal;

/**
 * Devuelve cupo previamente consumido cuando una negociación se rechaza o
 * revierte, a cada uno de los niveles de cupo involucrados (usuario, empresa,
 * fondeador).
 */
public interface CupoReversionService {

    /**
     * @param userId id del usuario operador
     * @param valor  monto a devolver a su cupo individual
     */
    void revertirCupo(Long userId, BigDecimal valor);

    /**
     * @param userId id del usuario operador
     * @param valor  monto a devolver a su cupo individual
     */
    void revertirCupoUsuario(Long userId, BigDecimal valor);

    /**
     * @param companyId id de la empresa
     * @param valor     monto a devolver a su cupo total
     */
    void revertirCupoEmpresa(Long companyId, BigDecimal valor);

    /**
     * Igual que {@link #revertirCupoEmpresa}, pero dentro de la transacción del llamador (que debe existir), para
     * que se confirme o se revierta junto con el resto de una reversión. {@link #revertirCupoEmpresa} sigue
     * confirmando por su cuenta para sus otros llamadores.
     *
     * @param companyId id de la empresa
     * @param valor     monto a devolver a su cupo total
     */
    void revertirCupoEmpresaEnTransaccionActual(Long companyId, BigDecimal valor);

    /**
     * @param funderId id de la empresa fondeadora
     * @param payerId  id de la empresa pagadora
     * @param valor    monto a devolver al cupo fondeador-pagador (Confirming)
     */
    void revertirCupoFondeadorConfirming(Long funderId, Long payerId, BigDecimal valor);

    /**
     * @param funderId   id de la empresa fondeadora
     * @param supplierId id de la empresa proveedora
     * @param valor      monto a devolver al cupo fondeador-proveedor (Factoring)
     */
    void revertirCupoFondeadorFactoring(Long funderId, Long supplierId, BigDecimal valor);
}

