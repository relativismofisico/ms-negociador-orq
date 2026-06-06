package com.negociadororq.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AprobacionFacturaRequest {
    private String numeroFactura;
    private boolean aprobado;
    private String observacion;
}
