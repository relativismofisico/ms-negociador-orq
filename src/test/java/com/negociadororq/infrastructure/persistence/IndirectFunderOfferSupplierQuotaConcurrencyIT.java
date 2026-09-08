package com.negociadororq.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import com.negociadororq.application.exception.ConflictoException;
import com.negociadororq.infrastructure.persistence.adapter.IndirectFunderOfferSupplierQuotaAdapter;
import com.negociadororq.infrastructure.persistence.repository.IndirectFunderOfferSupplierQuotaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifica contra un MySQL/InnoDB real y efímero (Testcontainers) que
 * {@code descontarCupo} cierra las dos carreras de
 * {@code ProcesarFacturaNegociacionServiceImpl.descontarCupoIndirecto}:
 *
 * <ol>
 *   <li><b>Creación concurrente</b>: dos negociaciones que son la primera del
 *       mismo par {@code (offer_id, supplier_id)} chocan el UNIQUE
 *       {@code uq_indirect_supplier_quota}. Antes: {@code DataIntegrityViolationException}
 *       cruda (500 / fuga de constraint). Ahora: una crea, la otra recibe una
 *       {@link ConflictoException} de negocio y la fila queda una sola.</li>
 *   <li><b>Descuento concurrente (lost update)</b>: N negociaciones descontando
 *       a la vez sobre la misma fila. Antes (leer-modificar-escribir): se perdían
 *       descuentos. Ahora ({@code UPDATE ... SET current = current - :valor}
 *       atómico): el saldo final es la resta de todos.</li>
 * </ol>
 *
 * Requiere Docker en el runner. NO está gateado por ninguna env var: corre como
 * parte de {@code ./gradlew test} / {@code check} en cualquier pipeline.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(IndirectFunderOfferSupplierQuotaAdapter.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:test-schema/indirect_funder_offer_supplier_quota.sql"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IndirectFunderOfferSupplierQuotaConcurrencyIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Autowired
    IndirectFunderOfferSupplierQuotaAdapter adapter;

    @Autowired
    IndirectFunderOfferSupplierQuotaRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM indirect_funder_offer_supplier_quota");
    }

    @Test
    void creacion_concurrente_del_mismo_par_una_gana_la_otra_recibe_ConflictoException_y_queda_una_sola_fila()
            throws InterruptedException {
        long offerId = 900L;
        long supplierId = 800L;

        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger creadas = new AtomicInteger();
        List<Throwable> fallos = Collections.synchronizedList(new ArrayList<>());

        Runnable worker = () -> {
            listos.countDown();
            try {
                largada.await();
                adapter.crear(offerId, supplierId, new BigDecimal("2.5"), new BigDecimal("10000.00"));
                creadas.incrementAndGet();
            } catch (Exception e) {
                fallos.add(e);
            }
        };

        Thread t1 = new Thread(worker);
        Thread t2 = new Thread(worker);
        t1.start();
        t2.start();
        listos.await();
        largada.countDown();
        t1.join();
        t2.join();

        assertThat(creadas.get()).isEqualTo(1);
        assertThat(fallos).hasSize(1);
        assertThat(fallos.get(0)).isInstanceOf(ConflictoException.class);

        ConflictoException conflicto = (ConflictoException) fallos.get(0);
        assertThat(conflicto.getCodigo()).isEqualTo("CUPO_PROVEEDOR_CONCURRENCIA");
        assertThat(conflicto.getMessage().toLowerCase())
                .doesNotContain("duplicate entry")
                .doesNotContain("constraint")
                .doesNotContain("indirect_funder_offer_supplier_quota");

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findByOfferIdAndSupplierId(offerId, supplierId)).isPresent();
    }

    @Test
    void descuentos_concurrentes_sobre_la_misma_fila_no_pierden_ninguno() throws InterruptedException {
        long offerId = 901L;
        long supplierId = 801L;
        jdbcTemplate.update(
                "INSERT INTO indirect_funder_offer_supplier_quota "
                        + "(offer_id, supplier_id, rate, quota, current_quota, original_quota) VALUES (?,?,?,?,?,?)",
                offerId, supplierId, new BigDecimal("2.5"),
                new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("1000.00"));

        int hilos = 10;
        BigDecimal descuentoPorHilo = new BigDecimal("10.00");

        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch largada = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        List<Throwable> fallos = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < hilos; i++) {
            Thread t = new Thread(() -> {
                listos.countDown();
                try {
                    largada.await();
                    int filas = adapter.descontarCupo(offerId, supplierId, descuentoPorHilo);
                    assertThat(filas).isEqualTo(1);
                } catch (Exception e) {
                    fallos.add(e);
                }
            });
            threads.add(t);
            t.start();
        }
        listos.await();
        largada.countDown();
        for (Thread t : threads) {
            t.join();
        }

        assertThat(fallos).isEmpty();

        BigDecimal saldoFinal = repository.findByOfferIdAndSupplierId(offerId, supplierId)
                .orElseThrow()
                .getCurrentQuota();

        // 1000 - (10 hilos * 10) = 900. Con leer-modificar-escribir habría quedado
        // en algún valor > 900 (descuentos pisados).
        assertThat(saldoFinal).isEqualByComparingTo("900.00");
    }
}
