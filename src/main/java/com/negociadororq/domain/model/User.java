package com.negociadororq.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos de un usuario operador, tal como los expone el microservicio de origen
 * (rol, empresa y persona asociados).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    private Long ideUser;
    private String username;
    private String password;
    private String createTime;
    private Long rolUserIde;
    private Long companyIde;
    private Long stateUserId;

    /** Fase 28d (P-09): estado Inactivo (statesusers 2, lo fija ms-security-util al desactivar). */
    public static final long ESTADO_INACTIVO = 2L;

    /** @return si el usuario está desactivado */
    public boolean esInactivo() {
        return stateUserId != null && stateUserId == ESTADO_INACTIVO;
    }
    private Long personIde;
}
