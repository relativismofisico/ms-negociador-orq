package com.negociadororq.enums;

public enum EstadoEndoso {
    PENDIENTE, //Al menos falta uno por aprobar
    EN_REVISION, //Faltan todos los actores por aprobar
    ACEPTADO, //Todos los actores han aceptado la negociacion
    RECHAZADO, //Al menos uno rechazo la negociacion
    PENDIENTE_OTP //Se solicito otp al ms-security-neg
}
