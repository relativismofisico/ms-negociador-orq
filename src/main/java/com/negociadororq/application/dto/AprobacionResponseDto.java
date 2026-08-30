package com.negociadororq.application.dto;

import java.util.List;

import com.negociadororq.domain.model.FacturaNegociada;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * Resultado de una operación de aprobación/rechazo de descuento sobre una negociación,
 * con el detalle de las facturas involucradas y los errores encontrados si los hubo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AprobacionResponseDto {
    private String idNegociacion;

    /** Estado resultante de la negociación: EN_APROBACION, RECHAZADA o APROBADA. */
    private String estado;

    /** Facturas negociadas involucradas en esta aprobación. */
    private List<FacturaNegociada> facturas;

    /** Errores encontrados durante la aprobación, si aplica. */
    private List<String> errores;
}

