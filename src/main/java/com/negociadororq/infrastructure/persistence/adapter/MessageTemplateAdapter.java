package com.negociadororq.infrastructure.persistence.adapter;

import com.negociadororq.application.port.output.MessageTemplateOutputPort;
import com.negociadororq.domain.model.MessageTemplate;
import com.negociadororq.infrastructure.persistence.entity.MessageTemplateEntity;
import com.negociadororq.infrastructure.persistence.repository.MessageTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MessageTemplateAdapter implements MessageTemplateOutputPort {

    private final MessageTemplateRepository repository;

    @Override
    public Optional<MessageTemplate> findByTemplateName(String templateName) {
        return repository.findByTemplateName(templateName).map(this::toDomain);
    }

    private MessageTemplate toDomain(MessageTemplateEntity entity) {
        MessageTemplate template = new MessageTemplate();
        template.setId(entity.getId());
        template.setTemplateName(entity.getTemplateName());
        template.setContent(entity.getContent());
        template.setActive(entity.isActive());
        return template;
    }
}
