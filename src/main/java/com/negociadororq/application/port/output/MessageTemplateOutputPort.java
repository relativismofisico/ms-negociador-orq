package com.negociadororq.application.port.output;

import java.util.Optional;

import com.negociadororq.domain.model.MessageTemplate;

/**
 * Puerto de persistencia de plantillas de mensajes de notificación.
 */
public interface MessageTemplateOutputPort {

    /**
     * @param templateName nombre de la plantilla
     * @return la plantilla, si existe y está activa
     */
    Optional<MessageTemplate> findByTemplateName(String templateName);
}
