package com.negociadororq.application.service.notificacion;

/**
 * Construye el cuerpo de texto de una notificación a partir de un objeto de
 * dominio. Cada implementación se registra automáticamente en
 * {@link CuerpoNotificacionFactory} según el tipo que declara manejar.
 *
 * @param <T> tipo de objeto de dominio que este builder sabe construir
 */
public interface CuerpoNotificacionBuilder<T> {

    /**
     * @return la clase del objeto de dominio que este builder maneja
     */
    Class<T> getTipo();

    /**
     * @param objeto objeto de dominio a partir del cual construir el cuerpo
     * @return el cuerpo de texto de la notificación
     */
    String construir(T objeto);
}

