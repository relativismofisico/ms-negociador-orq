package com.negociadororq.application.service;

import com.negociadororq.domain.enums.RolActor;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.PendingOtp;

/**
 * Registra la firma (o rechazo) de un actor sobre el PDF de endoso de una
 * factura negociada.
 */
public interface FirmaEndosoService {

    /**
     * Fase 28c: si la factura ya tiene estampada la firma de esta validación (marca {@code otpId:factura} del endoso).
     *
     * @param idNegociacion negociación
     * @param numeroFactura factura
     * @param otpId         referencia del OTP; {@code null} (formato anterior) devuelve false
     * @return true si ya está firmada con ese OTP
     */
    boolean yaFirmada(String idNegociacion, String numeroFactura, String otpId);

    /**
     * Fase 28c: si el documento del endoso de esta factura ya tiene alguna firma estampada (de cualquier actor). Los
     * bloques de firma solo existen dentro del PDF: regenerar el documento en ese caso los borraría.
     *
     * @param idNegociacion negociación
     * @param numeroFactura factura
     * @return true si el endoso tiene marcas de firma o aprobaciones registradas
     */
    boolean tieneFirmas(String idNegociacion, String numeroFactura);

    /**
     * @param idNegociacion id de la negociación
     * @param factura       factura negociada sobre la que se firma
     * @param pending       solicitud OTP pendiente asociada a la firma
     * @param rutEmpresa    RUT de la empresa del actor que firma
     * @param rutOperador   username del operador que firma
     * @param rolActor      rol del actor que firma (proveedor, pagador, fondeador)
     * @param aprobado      si el actor aprobó (true) o rechazó (false) el endoso
     * @param observacion   observación del actor, obligatoria si rechaza
     */
    void firmarPdfEndoso(String idNegociacion, FacturaNegociada factura, PendingOtp pending, String rutEmpresa,
            String rutOperador, RolActor rolActor, boolean aprobado, String observacion);
}

