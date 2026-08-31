package com.negociadororq.domain.enums;

/**
 * Estado de la firma de un documento de endoso, combinando si tiene cesión/descuento
 * asociado y en qué punto del flujo de aprobación se encuentra.
 */
public enum EstadoFirma {
    SIN_CESION,
    CESION,
    SIN_DESCUENTO,
    DESCUENTO,
    EN_APROBACION,
    RECHAZADA,
    EN_REVISION,
    APROBACION
}
