package com.negociadororq.domain.model;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cupo total asignado a un usuario puntual y cuánto de ese cupo lleva consumido.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserQuota {

    private Long id;
    private BigDecimal quota;
    private BigDecimal currentQuota;
    private Long userId;
}
