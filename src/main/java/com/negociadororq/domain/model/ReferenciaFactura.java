package com.negociadororq.domain.model;

/**
 * Fase 28d (C): identidad exacta de una factura de una negociación. El CUFE es único; los RUT de proveedor y pagador
 * (los de la base, guardados en la negociación) se comprueban además en cada escritura.
 *
 * @param cufe         CUFE
 * @param rutProveedor RUT del proveedor
 * @param rutPagador   RUT del pagador
 */
public record ReferenciaFactura(String cufe, String rutProveedor, String rutPagador) {
}
