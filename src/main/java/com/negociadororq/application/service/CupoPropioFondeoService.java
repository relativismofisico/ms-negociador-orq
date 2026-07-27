package com.negociadororq.application.service;

import com.negociadororq.application.dto.CupoPropioFondeoDto;
import java.math.BigDecimal;

/**
 * Cupo de autofondeo que una empresa aprobada para Confirming define para sí misma
 * (funder_id = payer_id en la fila de autofondeo de la tabla "funder"), tope superior
 * por el cupo general que el administrador le asigna en EmpresaQuota.
 */
public interface CupoPropioFondeoService {

    CupoPropioFondeoDto obtenerCupoPropio(Long companyId);

    CupoPropioFondeoDto actualizarCupoPropio(Long companyId, BigDecimal nuevoCupo, BigDecimal nuevaTasa);

    CupoPropioFondeoDto actualizarEndosoGrupal(Long companyId, boolean endosoGrupal);
}
