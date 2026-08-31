package com.negociadororq.infrastructure.persistence.repository;

import java.util.Optional;

import com.negociadororq.infrastructure.persistence.entity.MessageTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA de {@link MessageTemplateEntity}.
 */
public interface MessageTemplateRepository extends JpaRepository<MessageTemplateEntity, Long> {

    /**
     * @param templateName nombre de la plantilla
     * @return la plantilla, si existe
     */
    Optional<MessageTemplateEntity> findByTemplateName(String templateName);
}

