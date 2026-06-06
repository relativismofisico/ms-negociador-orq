package com.negociadororq.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FondeadorAsignacion {
    private String rutFondeador;
    private String nombreFondeador;
}

