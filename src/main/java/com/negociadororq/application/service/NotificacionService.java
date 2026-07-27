package com.negociadororq.application.service;

import com.negociadororq.domain.model.AprobacionRequest;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.Negociacion;

import java.util.List;

public interface NotificacionService {
    void notificarCreacionNegociacion(Negociacion negociacion);
    void notificarEndoso(Endoso endoso);

    /**
     * Notifica un grupo de endosos creados en la misma solicitud, agrupándolos
     * internamente por destinatario (mismo proveedor, o mismo pagador+fondeador)
     * para enviar un único correo con la lista de facturas en vez de uno por factura.
     *
     * @param endosos endosos creados en la misma solicitud
     */
    void notificarEndosos(List<Endoso> endosos);

    void notificarFirmaEndoso(AprobacionRequest aprobacion);
    void notificarLiquidacionFallida(Negociacion negociacion, String motivo);
}
