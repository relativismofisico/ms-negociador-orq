package com.negociadororq.domain.model;

/**
 * Datos mínimos de una persona natural necesarios para identificar al
 * firmante de un documento formal (endoso/descuento).
 */
public record DatosPersona(String nombreCompleto, String tipoDocumento, String numeroDocumento) {
}
