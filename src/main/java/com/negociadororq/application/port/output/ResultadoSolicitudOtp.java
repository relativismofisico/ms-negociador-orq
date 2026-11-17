package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.PendingOtp;

/**
 * Fase 28d (2.9): resultado del alta atómica de una solicitud de OTP por su clave natural (negociación, empresa,
 * operador).
 *
 * @param tipo           qué pasó
 * @param pendiente      la solicitud tal como quedó guardada
 * @param estadoAnterior estado reemplazado (solo con {@link Tipo#REEMPLAZADA})
 */
public record ResultadoSolicitudOtp(Tipo tipo, PendingOtp pendiente, String estadoAnterior) {

    /** Qué pasó con la solicitud. */
    public enum Tipo {
        /** No había solicitud para la clave: se creó. */
        NUEVA,
        /** Había una esperando código: se refrescó (fecha y datos), sin crear otra. */
        REFRESCADA,
        /** Había una terminada sin firma (FALLO_FIRMA o ROL_NO_PERMITIDO): se reemplazó por una nueva. */
        REEMPLAZADA,
        /** Hay un OTP ya validado con la firma en curso: no se toca. */
        FIRMA_EN_CURSO,
        /** El actor ya firmó: no se toca. */
        YA_FIRMADA
    }
}
