package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.domain.model.MessageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {

    Optional<MessageTemplate> findByTemplateName(String templateName);
}
