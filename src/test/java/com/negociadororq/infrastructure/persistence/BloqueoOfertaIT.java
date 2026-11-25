package com.negociadororq.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;

import com.negociadororq.infrastructure.persistence.adapter.IndirectFunderOfferSupplierQuotaAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Fase 28d (P-19, condiciones de John): orden de bloqueos del cupo indirecto (la OFERTA primero, después la fila del
 * proveedor) y lock wait timeout contra MySQL 8 real. Con la oferta bloqueada en exclusivo (como registro al cambiar la
 * plantilla), un descuento del cupo del proveedor espera a la oferta aunque la fila esté libre, y al vencer el tiempo de
 * espera sale una PessimisticLockingFailureException, que BloqueosProcesarFactura traduce a 409.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(IndirectFunderOfferSupplierQuotaAdapter.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:test-schema/indirect_funder_offer.sql,"
                + "classpath:test-schema/indirect_funder_offer_supplier_quota.sql,classpath:test-schema/cupo_oferta_exceso.sql",
        "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout = 2"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BloqueoOfertaIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withCreateContainerCmdModifier(c -> c.withName("integrity-e2e-tc-mysql-"
                    + java.util.UUID.randomUUID().toString().substring(0, 8)));

    @Autowired
    IndirectFunderOfferSupplierQuotaAdapter adapter;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM indirect_funder_offer_supplier_quota");
        jdbc.update("DELETE FROM indirect_funder_offer");
    }

    @Test
    void con_la_oferta_bloqueada_en_exclusivo_el_descuento_espera_a_la_oferta_y_vence_con_lock_wait_timeout()
            throws Exception {
        jdbc.update("INSERT INTO indirect_funder_offer (id, supplier_quota_template, cupo_version) VALUES (60, 1000.00, 0)");
        jdbc.update("INSERT INTO indirect_funder_offer_supplier_quota (offer_id, supplier_id, rate, quota, current_quota,"
                + " original_quota, oferta_cupo_plantilla) VALUES (60, 1, 2.50, 1000.00, 1000.00, 1000.00, 1000.00)");
        try (Connection registro = dataSource.getConnection(); Statement st = registro.createStatement()) {
            registro.setAutoCommit(false);
            // registro cambiando la plantilla: la oferta en exclusivo, sin tocar la fila del proveedor
            st.executeUpdate("UPDATE indirect_funder_offer SET supplier_quota_template = 1200.00 WHERE id = 60");

            assertThatThrownBy(() -> adapter.descontarCupo(60L, 1L, new BigDecimal("10.00")))
                    .isInstanceOf(PessimisticLockingFailureException.class);
            registro.rollback();
        }
        // la fila no se tocó (la espera fue por la oferta) y, sin bloqueo, el descuento entra
        assertThat(jdbc.queryForObject("SELECT current_quota FROM indirect_funder_offer_supplier_quota WHERE offer_id = 60",
                BigDecimal.class)).isEqualByComparingTo("1000.00");
        assertThat(adapter.descontarCupo(60L, 1L, new BigDecimal("10.00"))).isEqualTo(1);
    }
}
