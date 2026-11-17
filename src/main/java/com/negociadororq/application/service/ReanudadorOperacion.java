package com.negociadororq.application.service;

import java.util.List;

import com.negociadororq.domain.model.OperacionPendiente;
import com.negociadororq.domain.model.TipoOperacionPendiente;

/**
 * Fase 28d, opción A: cómo reanuda el reconciliador las operaciones de un tipo que quedaron a medias. Lo implementa cada
 * flujo (crear negociación, rechazo, endoso rechazado, liquidación fallida, vencimiento). Cada paso es idempotente:
 * ids deterministas y actualizaciones condicionales.
 */
public interface ReanudadorOperacion {

    /**
     * @return el tipo que reanuda
     */
    TipoOperacionPendiente tipo();

    /**
     * @return los estados intermedios desde los que se puede reanudar
     */
    List<String> estadosReanudables();

    /**
     * Lleva la operación a su estado final (o compensa). Si falla, lanza: el reconciliador deja el error y la vuelve a
     * tomar en otra vuelta.
     *
     * @param operacion la operación tomada (con su concesión)
     */
    void reanudar(OperacionPendiente operacion);
}
