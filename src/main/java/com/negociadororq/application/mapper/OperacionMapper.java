package com.negociadororq.application.mapper;

import com.negociadororq.application.dto.OperacionDto;
import com.negociadororq.domain.model.Operacion;
import org.springframework.stereotype.Component;

@Component
public class OperacionMapper {

    public OperacionDto toDto(Operacion operacion) {

        if (operacion == null) {
            return null;
        }

        OperacionDto dto = new OperacionDto();
        dto.setTipo(operacion.getTipo());

        return dto;
    }
}

