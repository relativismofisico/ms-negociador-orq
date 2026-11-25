package com.negociadororq.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Fase 28d (J-04, decisión de John): V5__cupos_decimal_19_2 del negociador contra MySQL 8 real. Cada caso en su base, con
 * indirect_funder_offer_supplier_quota en su tipo anterior (DECIMAL(38,2)), y la V5 de db/manual (idéntica a la de Flyway).
 */
@Testcontainers
class MigracionCupos192IT {

    private static final List<String> COLUMNAS = List.of("quota", "current_quota", "original_quota");
    private static final BigDecimal MAXIMO = new BigDecimal("99999999999999999.99");
    private static final BigDecimal FUERA = new BigDecimal("100000000000000000.00");
    private static final AtomicInteger CASO = new AtomicInteger();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withCreateContainerCmdModifier(c -> c.withName("integrity-e2e-tc-mysql-"
                    + UUID.randomUUID().toString().substring(0, 8)));

    private HikariDataSource ds;
    private JdbcTemplate jdbc;

    @AfterEach
    void cerrar() {
        if (ds != null) {
            ds.close();
        }
    }

    private void baseAnterior() {
        String nombre = "c192n_" + CASO.incrementAndGet();
        try (HikariDataSource raiz = datos(MYSQL.getJdbcUrl())) {
            new JdbcTemplate(raiz).execute("CREATE DATABASE " + nombre);
        }
        ds = datos(MYSQL.getJdbcUrl().replace("/" + MYSQL.getDatabaseName(), "/" + nombre));
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE indirect_funder_offer_supplier_quota (id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + " offer_id BIGINT, supplier_id BIGINT, rate DECIMAL(38,2) DEFAULT NULL, quota DECIMAL(38,2) DEFAULT NULL,"
                + " current_quota DECIMAL(38,2) DEFAULT NULL, original_quota DECIMAL(38,2) DEFAULT NULL)");
    }

    private static HikariDataSource datos(String url) {
        HikariDataSource d = new HikariDataSource();
        d.setJdbcUrl(url);
        d.setUsername("root");
        d.setPassword(MYSQL.getPassword());
        d.setMaximumPoolSize(2);
        return d;
    }

    private void correrV5(String historial) throws IOException {
        Path dir = Files.createTempDirectory("v5n");
        Files.copy(Path.of("db/manual/V5__cupos_decimal_19_2.sql"), dir.resolve("V1__cupos_decimal_19_2.sql"),
                StandardCopyOption.REPLACE_EXISTING);
        Flyway.configure().dataSource(ds).locations("filesystem:" + dir).table(historial).baselineOnMigrate(true)
                .baselineVersion("0").load().migrate();
    }

    private String tipo(String columna) {
        return jdbc.queryForObject("SELECT column_type FROM information_schema.columns WHERE table_schema = DATABASE()"
                + " AND table_name = 'indirect_funder_offer_supplier_quota' AND column_name = ?", String.class, columna);
    }

    private List<Map<String, Object>> valores() {
        return jdbc.queryForList("SELECT quota, current_quota, original_quota, rate FROM"
                + " indirect_funder_offer_supplier_quota ORDER BY id");
    }

    @Test
    void la_de_db_manual_es_identica_a_la_de_flyway() throws IOException {
        assertThat(Files.readString(Path.of("db/manual/V5__cupos_decimal_19_2.sql")))
                .isEqualTo(Files.readString(Path.of("src/main/resources/db/migration/V5__cupos_decimal_19_2.sql")));
    }

    @Test
    void lleva_las_tres_columnas_a_19_2_sin_cambiar_ningun_valor_ni_la_tasa_y_es_repetible() throws IOException {
        baseAnterior();
        jdbc.update("INSERT INTO indirect_funder_offer_supplier_quota (offer_id, supplier_id, rate, quota, current_quota,"
                + " original_quota) VALUES (1, 1, 1.50, 10000.00, 2000.37, 10000.00), (1, 2, 1.50, NULL, NULL, NULL)");
        List<Map<String, Object>> antes = valores();

        correrV5("historial_v5_a");
        assertThat(COLUMNAS).allSatisfy(c -> assertThat(tipo(c)).isEqualTo("decimal(19,2)"));
        assertThat(tipo("rate")).isEqualTo("decimal(38,2)");
        assertThat(valores()).isEqualTo(antes);

        correrV5("historial_v5_b");
        assertThat(COLUMNAS).allSatisfy(c -> assertThat(tipo(c)).isEqualTo("decimal(19,2)"));
        assertThat(valores()).isEqualTo(antes);
    }

    @Test
    void el_maximo_de_19_2_se_guarda_exacto_y_uno_mas_se_rechaza_en_cada_columna() throws IOException {
        baseAnterior();
        jdbc.update("INSERT INTO indirect_funder_offer_supplier_quota (id, offer_id, supplier_id) VALUES (1, 1, 1)");
        correrV5("historial_v5_c");
        for (String c : COLUMNAS) {
            jdbc.update("UPDATE indirect_funder_offer_supplier_quota SET " + c + " = ? WHERE id = 1", MAXIMO);
            assertThat(jdbc.queryForObject("SELECT " + c + " FROM indirect_funder_offer_supplier_quota WHERE id = 1",
                    BigDecimal.class)).isEqualByComparingTo(MAXIMO);
            assertThatThrownBy(() -> jdbc.update("UPDATE indirect_funder_offer_supplier_quota SET " + c
                    + " = ? WHERE id = 1", FUERA)).isInstanceOf(DataAccessException.class);
        }
    }

    @Test
    void un_valor_que_no_cabe_detiene_la_v5_sin_convertir_nada() {
        baseAnterior();
        jdbc.update("INSERT INTO indirect_funder_offer_supplier_quota (offer_id, supplier_id, quota, current_quota,"
                + " original_quota) VALUES (1, 1, ?, 0, ?)", FUERA, FUERA);

        assertThatThrownBy(() -> correrV5("historial_v5_d")).isInstanceOf(FlywayException.class)
                .satisfies(e -> assertThat(e.getMessage() + e.getCause()).contains("V5 negociador detenida"));
        assertThat(tipo("quota")).isEqualTo("decimal(38,2)");
    }

    @Test
    void la_reversa_vuelve_a_38_2_sin_perder_dinero() throws IOException {
        baseAnterior();
        jdbc.update("INSERT INTO indirect_funder_offer_supplier_quota (offer_id, supplier_id, quota, current_quota,"
                + " original_quota) VALUES (1, 1, 5000.00, 1234.56, 5000.00)");
        correrV5("historial_v5_e");
        List<Map<String, Object>> antes = valores();
        // primero fuera los comentarios (pueden llevar ";"), después las sentencias
        String reversa = Files.readString(Path.of("db/manual/V5__cupos_decimal_19_2_reversa.sql")).lines()
                .filter(l -> !l.trim().startsWith("--")).reduce("", (x, y) -> x + "\n" + y);
        for (String sentencia : reversa.split(";")) {
            String limpia = sentencia.trim();
            if (!limpia.isEmpty()) {
                jdbc.execute(limpia);
            }
        }
        assertThat(COLUMNAS).allSatisfy(c -> assertThat(tipo(c)).isEqualTo("decimal(38,2)"));
        assertThat(valores()).isEqualTo(antes);
    }

    private void correrManual(String archivo, String historial) throws IOException {
        Path dir = Files.createTempDirectory("v6n");
        Files.copy(Path.of("db/manual/" + archivo), dir.resolve("V1__manual.sql"), StandardCopyOption.REPLACE_EXISTING);
        Flyway.configure().dataSource(ds).locations("filesystem:" + dir).table(historial).baselineOnMigrate(true)
                .baselineVersion("0").load().migrate();
    }

    @Test
    void v6_agrega_version_y_plantilla_y_las_rellena_con_la_plantilla_vigente_de_la_oferta_repetible() throws IOException {
        baseAnterior();
        jdbc.execute("CREATE TABLE indirect_funder_offer (id BIGINT PRIMARY KEY, supplier_quota_template DECIMAL(19,2),"
                + " cupo_version BIGINT NOT NULL DEFAULT 0)");
        jdbc.update("INSERT INTO indirect_funder_offer VALUES (1, 10000.00, 3), (2, 500.00, 0)");
        jdbc.update("INSERT INTO indirect_funder_offer_supplier_quota (offer_id, supplier_id, quota, current_quota,"
                + " original_quota) VALUES (1, 1, 8000.00, 100.00, 8000.00), (2, 1, 500.00, 500.00, 500.00)");
        assertThat(Files.readString(Path.of("db/manual/V6__oferta_cupo_propagacion.sql")))
                .isEqualTo(Files.readString(Path.of("src/main/resources/db/migration/V6__oferta_cupo_propagacion.sql")));

        correrManual("V6__oferta_cupo_propagacion.sql", "historial_v6_a");
        correrManual("V6__oferta_cupo_propagacion.sql", "historial_v6_b");

        assertThat(jdbc.queryForList("SELECT offer_id, oferta_cupo_plantilla, oferta_cupo_version, original_quota"
                + " FROM indirect_funder_offer_supplier_quota ORDER BY offer_id")).satisfiesExactly(
                f -> {
                    assertThat((BigDecimal) f.get("oferta_cupo_plantilla")).isEqualByComparingTo("10000.00");
                    assertThat(f.get("oferta_cupo_version")).isEqualTo(3L);
                    assertThat((BigDecimal) f.get("original_quota")).isEqualByComparingTo("8000.00");
                },
                f -> {
                    assertThat((BigDecimal) f.get("oferta_cupo_plantilla")).isEqualByComparingTo("500.00");
                    assertThat(f.get("oferta_cupo_version")).isEqualTo(0L);
                });
        assertThat(tipo("oferta_cupo_plantilla")).isEqualTo("decimal(19,2)");
    }

    /**
     * Fase 28d (P-19 / P-24, decisión de John): la V9 iguala los techos heredados a la plantilla vigente de la oferta con
     * piso en lo consumido, registra el exceso, es repetible y su reversa devuelve los valores respaldados.
     */
    @Test
    void v9_iguala_techos_heredados_a_la_plantilla_con_piso_en_lo_consumido_y_registra_el_exceso() throws IOException {
        baseAnterior();
        jdbc.execute("CREATE TABLE indirect_funder_offer (id BIGINT PRIMARY KEY, supplier_quota_template DECIMAL(19,2),"
                + " cupo_version BIGINT NOT NULL DEFAULT 0)");
        jdbc.update("INSERT INTO indirect_funder_offer VALUES (1, 10000.00, 2)");
        jdbc.update("INSERT INTO indirect_funder_offer_supplier_quota (offer_id, supplier_id, quota, current_quota,"
                + " original_quota) VALUES (1, 1, 8000.00, 8000.00, 8000.00), (1, 2, 12000.00, 1000.00, 12000.00),"
                + " (1, 3, 10000.00, 4000.00, 10000.00)");
        correrManual("V5__cupos_decimal_19_2.sql", "historial_v9_v5");
        correrManual("V6__oferta_cupo_propagacion.sql", "historial_v9_v6");
        correrManual("V7__cupo_oferta_exceso.sql", "historial_v9_v7");
        List<Map<String, Object>> antes = valores();
        assertThat(Files.readString(Path.of("db/manual/V9__igualar_techos_heredados.sql")))
                .isEqualTo(Files.readString(Path.of("src/main/resources/db/migration/V9__igualar_techos_heredados.sql")));

        correrManual("V9__igualar_techos_heredados.sql", "historial_v9_a");

        // 1: sin consumo, sube a la plantilla; 2: consumió 11.000 > 10.000, techo 11.000 y disponible 0 (exceso 1.000);
        // 3: ya estaba en la plantilla, sin cambio
        assertThat(jdbc.queryForList("SELECT original_quota, current_quota, quota, oferta_cupo_plantilla,"
                + " oferta_cupo_version FROM indirect_funder_offer_supplier_quota ORDER BY supplier_id")).satisfiesExactly(
                f -> filaV9(f, "10000.00", "10000.00"),
                f -> filaV9(f, "11000.00", "0.00"),
                f -> filaV9(f, "10000.00", "4000.00"));
        assertThat(jdbc.queryForList("SELECT supplier_id, consumido, plantilla_nueva, exceso, version FROM"
                + " cupo_oferta_exceso")).singleElement().satisfies(e -> {
                    assertThat(e.get("supplier_id")).isEqualTo(2L);
                    assertThat((BigDecimal) e.get("consumido")).isEqualByComparingTo("11000.00");
                    assertThat((BigDecimal) e.get("exceso")).isEqualByComparingTo("1000.00");
                    assertThat(e.get("version")).isEqualTo(2L);
                });

        List<Map<String, Object>> igualadas = valores();
        correrManual("V9__igualar_techos_heredados.sql", "historial_v9_b");
        assertThat(valores()).isEqualTo(igualadas);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cupo_oferta_exceso", Integer.class)).isEqualTo(1);

        String reversa = Files.readString(Path.of("db/manual/V9__igualar_techos_heredados_reversa.sql")).lines()
                .filter(l -> !l.trim().startsWith("--")).reduce("", (x, y) -> x + "\n" + y);
        // la última corrida respaldó los valores ya igualados: la reversa deja todo como antes de esa corrida
        for (String sentencia : reversa.split(";")) {
            if (!sentencia.trim().isEmpty()) {
                jdbc.execute(sentencia.trim());
            }
        }
        assertThat(valores()).isEqualTo(igualadas);
        jdbc.update("DELETE FROM cupos_respaldo_v9_negociador WHERE corrida = (SELECT m FROM (SELECT MAX(corrida) m"
                + " FROM cupos_respaldo_v9_negociador) x)");
        for (String sentencia : reversa.split(";")) {
            if (!sentencia.trim().isEmpty()) {
                jdbc.execute(sentencia.trim());
            }
        }
        assertThat(valores()).isEqualTo(antes);
    }

    private static void filaV9(Map<String, Object> f, String techo, String disponible) {
        assertThat((BigDecimal) f.get("original_quota")).isEqualByComparingTo(techo);
        assertThat((BigDecimal) f.get("quota")).isEqualByComparingTo(techo);
        assertThat((BigDecimal) f.get("current_quota")).isEqualByComparingTo(disponible);
        assertThat((BigDecimal) f.get("oferta_cupo_plantilla")).isEqualByComparingTo("10000.00");
        assertThat(f.get("oferta_cupo_version")).isEqualTo(2L);
    }
}
