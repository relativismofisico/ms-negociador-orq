package com.negociadororq.infrastructure.persistence.adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.OperacionPendienteOutputPort;
import com.negociadororq.domain.model.OperacionPendiente;
import com.negociadororq.domain.model.TipoOperacionPendiente;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link OperacionPendienteOutputPort} con JDBC sobre {@code operacion_pendiente} (Fase 28d, opción A). JDBC y no JPA
 * por lo mismo que el reclamo de reversión: el UNIQUE de MySQL decide el alta y las transiciones son UPDATE
 * condicionales; un {@code save} de JPA haría un merge.
 */
@Component
@RequiredArgsConstructor
public class OperacionPendienteJdbcAdapter implements OperacionPendienteOutputPort {

    static final int LARGO_ERROR = 500;

    private static final String COLUMNAS = "id, tipo, clave_natural, estado, datos, lease_hasta, intentos,"
            + " fecha_creacion, fecha_actualizacion, ultimo_error";

    private static final RowMapper<OperacionPendiente> FILA = (rs, numero) -> mapear(rs);

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate jdbcConNombres;
    private final ClockPort clock;

    @Override
    public boolean registrar(final TipoOperacionPendiente tipo, final String claveNatural, final String estadoInicial,
            final String datos) {
        final Timestamp ahora = Timestamp.valueOf(clock.now());
        try {
            jdbc.update("INSERT INTO operacion_pendiente (tipo, clave_natural, estado, datos, intentos, fecha_creacion,"
                    + " fecha_actualizacion) VALUES (?, ?, ?, ?, 0, ?, ?)",
                    tipo.name(), claveNatural, estadoInicial, OperacionPendiente.exigirDatosNoSensibles(datos), ahora,
                    ahora);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public boolean transicionar(final TipoOperacionPendiente tipo, final String claveNatural, final String desde,
            final String hacia) {
        return jdbc.update("UPDATE operacion_pendiente SET estado = ?, lease_hasta = NULL, fecha_actualizacion = ?"
                + " WHERE tipo = ? AND clave_natural = ? AND estado = ?",
                hacia, Timestamp.valueOf(clock.now()), tipo.name(), claveNatural, desde) == 1;
    }

    @Override
    public boolean existeCreacionViva(final String cufe) {
        final String patron = "%" + OperacionPendiente.SEPARADOR + cufe.replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_");
        final Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM operacion_pendiente WHERE tipo = ? AND estado IN"
                + " ('CUPOS_DESCONTADOS', 'COMPLETADA') AND clave_natural LIKE ?", Integer.class,
                TipoOperacionPendiente.CREAR_NEGOCIACION.name(), patron);
        return n != null && n > 0;
    }

    @Override
    public Optional<OperacionPendiente> buscar(final TipoOperacionPendiente tipo, final String claveNatural) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM operacion_pendiente WHERE tipo = ? AND clave_natural = ?",
                FILA, tipo.name(), claveNatural).stream().findFirst();
    }

    @Override
    public List<OperacionPendiente> tomarParaReanudar(final List<String> estados, final LocalDateTime sinCambiosDesde,
            final LocalDateTime ahora, final LocalDateTime leaseHasta, final int limite) {
        if (estados.isEmpty() || limite <= 0) {
            return List.of();
        }
        final MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("estados", estados)
                .addValue("sinCambiosDesde", Timestamp.valueOf(sinCambiosDesde))
                .addValue("ahora", Timestamp.valueOf(ahora))
                .addValue("limite", limite);
        final List<Long> candidatas = jdbcConNombres.queryForList("SELECT id FROM operacion_pendiente"
                + " WHERE estado IN (:estados) AND fecha_actualizacion < :sinCambiosDesde"
                + " AND (lease_hasta IS NULL OR lease_hasta < :ahora) ORDER BY fecha_actualizacion LIMIT :limite",
                p, Long.class);
        final List<OperacionPendiente> tomadas = new ArrayList<>();
        for (final Long id : candidatas) {
            // Reclamo por fila con la misma condición: si otro reconciliador la tomó entre la lectura y aquí, 0 filas.
            final int filas = jdbcConNombres.update("UPDATE operacion_pendiente SET lease_hasta = :leaseHasta,"
                    + " intentos = intentos + 1 WHERE id = :id AND estado IN (:estados)"
                    + " AND (lease_hasta IS NULL OR lease_hasta < :ahora)",
                    new MapSqlParameterSource(p.getValues()).addValue("leaseHasta", Timestamp.valueOf(leaseHasta))
                            .addValue("id", id));
            if (filas == 1) {
                jdbc.query("SELECT " + COLUMNAS + " FROM operacion_pendiente WHERE id = ?",
                        FILA, id).forEach(tomadas::add);
            }
        }
        return tomadas;
    }

    @Override
    public void registrarError(final Long id, final String error) {
        String texto = error;
        if (texto != null && texto.length() > LARGO_ERROR) {
            texto = texto.substring(0, LARGO_ERROR);
        }
        jdbc.update("UPDATE operacion_pendiente SET ultimo_error = ?, lease_hasta = NULL WHERE id = ?", texto, id);
    }

    private static OperacionPendiente mapear(final ResultSet rs) throws SQLException {
        return new OperacionPendiente(rs.getLong("id"), TipoOperacionPendiente.valueOf(rs.getString("tipo")),
                rs.getString("clave_natural"), rs.getString("estado"), rs.getString("datos"),
                fecha(rs.getTimestamp("lease_hasta")), rs.getInt("intentos"), fecha(rs.getTimestamp("fecha_creacion")),
                fecha(rs.getTimestamp("fecha_actualizacion")), rs.getString("ultimo_error"));
    }

    private static LocalDateTime fecha(final Timestamp t) {
        if (t == null) {
            return null;
        }
        return t.toLocalDateTime();
    }
}
