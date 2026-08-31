package com.negociadororq.application.service;

import java.util.List;

import com.negociadororq.domain.model.AprobacionRequest;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.Negociacion;

/**
 * Envía las notificaciones (email) asociadas al ciclo de vida de una
 * negociación y sus endosos.
 */
public interface NotificacionService {

    /**
     * @param negociacion negociación recién creada a notificar a sus actores
     */
    void notificarCreacionNegociacion(Negociacion negociacion);

    /**
     * @param endoso endoso creado a notificar a sus actores
     */
    void notificarEndoso(Endoso endoso);

    /**
     * Notifica un grupo de endosos creados en la misma solicitud, agrupándolos
     * internamente por destinatario (mismo proveedor, o mismo pagador+fondeador)
     * para enviar un único correo con la lista de facturas en vez de uno por factura.
     *
     * @param endosos endosos creados en la misma solicitud
     */
    void notificarEndosos(List<Endoso> endosos);

    /**
     * @param aprobacion aprobación o rechazo de firma a notificar
     */
    void notificarFirmaEndoso(AprobacionRequest aprobacion);

    /**
     * @param negociacion negociación cuya liquidación falló
     * @param motivo      motivo de la falla, para incluir en la notificación
     */
    void notificarLiquidacionFallida(Negociacion negociacion, String motivo);
}
