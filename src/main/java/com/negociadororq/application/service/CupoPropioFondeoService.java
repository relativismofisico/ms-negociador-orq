package com.negociadororq.application.service;

import java.math.BigDecimal;

import com.negociadororq.application.dto.CupoPropioFondeoDto;

/**
 * Cupo de autofondeo que una empresa aprobada para Confirming define para sí misma
 * (funder_id = payer_id en la fila de autofondeo de la tabla "funder"), tope superior
 * por el cupo general que el administrador le asigna en EmpresaQuota.
 */
public interface CupoPropioFondeoService {

    /**
     * @param companyId id de la empresa
     * @return la configuración de autofondeo vigente de la empresa
     */
    CupoPropioFondeoDto obtenerCupoPropio(Long companyId);

    /**
     * @param tipoDescuento FIJO, FINANCIERO o DINAMICO. Obligatorio.
     * @param tipoTasa Obligatorio salvo que tipoDescuento sea FIJO (en ese caso se ignora/limpia).
     * @param tarifaPlena Obligatoria solo si tipoDescuento es DINAMICO (en ese caso coexiste con nuevaTasa);
     *         se ignora/limpia en cualquier otro caso.
     * @param vigenciaDescuento TODO_EL_PLAZO, PRIMEROS_DIAS o DIAS_ANTES_VENCIMIENTO. Obligatorio.
     * @param diasVigencia Obligatorio (entero no negativo) salvo que vigenciaDescuento sea TODO_EL_PLAZO
     *         (en ese caso se ignora/limpia).
     * @param porcentajeReserva Porcentaje que se retiene como reserva ante incertidumbre en el valor real a
     *         pagar por la factura a su vencimiento, o cuando corresponde aplicar un descuento comercial sobre
     *         el valor de la factura. Obligatorio.
     */
    // 9 parámetros: cada uno es un campo independiente de la configuración de autofondeo
    // que se actualiza en conjunto (ver reglas de obligatoriedad condicional arriba); partirlo
    // en varias llamadas rompería la atomicidad de la validación conjunta.
    @SuppressWarnings("checkstyle:ParameterNumber")
    CupoPropioFondeoDto actualizarCupoPropio(Long companyId, BigDecimal nuevoCupo, BigDecimal nuevaTasa,
            String tipoDescuento, String tipoTasa, BigDecimal tarifaPlena,
            String vigenciaDescuento, Integer diasVigencia, BigDecimal porcentajeReserva);

    /**
     * @param companyId    id de la empresa
     * @param endosoGrupal si habilita agrupar varias facturas del mismo fondeador en un solo endoso
     * @return la configuración de autofondeo actualizada
     */
    CupoPropioFondeoDto actualizarEndosoGrupal(Long companyId, boolean endosoGrupal);

    /**
     * Activa o desactiva la rentabilidad mínima esperada del autofondeo. Si
     * {@code activa} es true, {@code valor} (tasa Efectivo Anual) es obligatorio.
     */
    CupoPropioFondeoDto actualizarRentabilidadMinima(Long companyId, boolean activa, BigDecimal valor);

    /**
     * Libera el cupo consumido: vuelve el cupo disponible al valor del cupo
     * propio configurado, sin modificar cupoPropio, tasa ni el resto de la
     * configuración.
     */
    CupoPropioFondeoDto restablecerCupo(Long companyId);
}
