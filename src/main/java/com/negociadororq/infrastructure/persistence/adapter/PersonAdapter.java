package com.negociadororq.infrastructure.persistence.adapter;

import com.negociadororq.application.port.output.PersonOutputPort;
import com.negociadororq.domain.model.DatosPersona;
import com.negociadororq.infrastructure.persistence.entity.DocumentTypeEntity;
import com.negociadororq.infrastructure.persistence.entity.PersonEntity;
import com.negociadororq.infrastructure.persistence.repository.DocumentTypeRepository;
import com.negociadororq.infrastructure.persistence.repository.PersonRepository;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PersonAdapter implements PersonOutputPort {

    private final PersonRepository personRepository;
    private final DocumentTypeRepository documentTypeRepository;

    @Override
    public Optional<DatosPersona> findById(Long ide) {
        return personRepository.findById(ide).map(this::toDomain);
    }

    private DatosPersona toDomain(PersonEntity entity) {
        String nombreCompleto = Stream.of(
                        entity.getFirtsName(), entity.getSecondName(),
                        entity.getFirtsLastname(), entity.getSecondLastname())
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("");

        String tipoDocumento = Optional.ofNullable(entity.getDocumentTypeIde())
                .flatMap(documentTypeRepository::findById)
                .map(DocumentTypeEntity::getDocumentTypeName)
                .orElse("");

        return new DatosPersona(nombreCompleto, tipoDocumento, entity.getDocumentNumber());
    }
}
