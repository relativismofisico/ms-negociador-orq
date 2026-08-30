package com.negociadororq.application.mapper;

import com.negociadororq.application.dto.OperacionDto;
import com.negociadororq.domain.model.Operacion;
import org.springframework.stereotype.Component;

/**
 * Traduce el modelo de dominio {@link Operacion} a su DTO de exposición REST.
 */
@Component
public class OperacionMapper {

    /**
     * @param operacion operación de dominio a traducir, o {@code null}
     * @return el DTO equivalente, o {@code null} si {@code operacion} es {@code null}
     */
    public OperacionDto toDto(Operacion operacion) {

        if (operacion == null) {
            return null;
        }

        OperacionDto dto = new OperacionDto();
        dto.setTipo(operacion.getTipo());

        return dto;
    }
}

