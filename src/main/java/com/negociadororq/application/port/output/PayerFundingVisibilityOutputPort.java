package com.negociadororq.application.port.output;

/**
 * Puerto de solo lectura para el toggle de una empresa pagadora ("mostrar a
 * mis proveedores mis fondeadores de factoring indirecto"). La gestión del
 * toggle vive en ms-registroempresa-neg; aquí solo se consulta para validar
 * durante la negociación.
 */
public interface PayerFundingVisibilityOutputPort {

    /**
     * @param payerId id de la empresa pagadora
     * @return true si la pagadora habilitó que sus proveedores vean sus
     *         fondeadores de factoring indirecto
     */
    boolean estaHabilitado(Long payerId);
}
