package com.negociadororq.application.port.output;

import com.negociadororq.domain.model.MessageTemplate;

import java.util.Optional;

public interface MessageTemplateOutputPort {
    Optional<MessageTemplate> findByTemplateName(String templateName);
}
