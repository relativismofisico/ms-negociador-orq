package com.negociadororq.infrastructure.persistence.adapter;

import com.negociadororq.application.port.output.ReversionCuposReclamoOutputPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reclamo con un INSERT sobre la clave primaria {@code (numero_factura, negociacion_id)}: el UNIQUE de MySQL
 * decide quién gana, también entre instancias. JDBC y no JPA porque un {@code save} de JPA sobre una clave ya
 * existente haría un merge en vez de fallar.
 */
@Component
@RequiredArgsConstructor
public class ReversionCuposReclamoAdapter implements ReversionCuposReclamoOutputPort {

    private static final String INSERTAR =
            "INSERT INTO reversion_cupos_factura (numero_factura, negociacion_id, fecha_reclamo)"
                    + " VALUES (?, ?, UTC_TIMESTAMP(6))";

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reclamar(String numeroFactura, String negociacionId) {
        try {
            jdbcTemplate.update(INSERTAR, numeroFactura, negociacionId);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}
