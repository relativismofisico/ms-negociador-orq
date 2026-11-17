package com.negociadororq.application.port.output;

import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.domain.model.AprobacionActorDto;

/**
 * Actualizaciones parciales (patch) sobre un endoso ya persistido.
 */
public interface EndosoPatchOutputPort {

    /**
     * @param idNegociacion id de la negociación
     * @param numeroFactura número de la factura endosada
     * @param estado        nuevo estado del endoso
     */
    void actualizarEstadoEndosoPorNoFactura(String idNegociacion, String numeroFactura, EstadoEndoso estado);

    /**
     * @param idNegociacion id de la negociación
     * @param numeroFactura número de la factura endosada
     * @param aprobacion    aprobación del actor a registrar
     * @param estado        nuevo estado del endoso tras registrar la aprobación
     */
    void registrarAprobacionActor(String idNegociacion, String numeroFactura, AprobacionActorDto aprobacion, EstadoEndoso estado);

    /**
     * Fase 28d (firma única): reclama de forma atómica la firma de la empresa sobre la factura del endoso. Pasa si nadie
     * de la empresa la había reclamado o si la reclamó este mismo OTP (reintento); falla si la reclamó otro OTP.
     *
     * @param idNegociacion negociación
     * @param numeroFactura factura
     * @param rutEmpresa    empresa que firma
     * @param otpId         OTP de esta firma
     * @return {@code true} si la firma es de este OTP
     */
    boolean reclamarFirmaEmpresa(String idNegociacion, String numeroFactura, String rutEmpresa, String otpId);
}
