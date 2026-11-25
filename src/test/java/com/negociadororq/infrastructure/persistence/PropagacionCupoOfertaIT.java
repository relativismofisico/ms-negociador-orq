package com.negociadororq.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import com.negociadororq.application.port.output.IndirectFunderOfferSupplierQuotaOutputPort.Propagacion;
import com.negociadororq.infrastructure.persistence.adapter.IndirectFunderOfferSupplierQuotaAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * Fase 28d (P-19, decisión de John J-09): propagación del cupo por proveedor de la oferta indirecta contra MySQL 8 real
 * (la sentencia relativa del repositorio y la creación de la fila desde la oferta).
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
                + "classpath:test-schema/indirect_funder_offer_supplier_quota.sql,"
                + "classpath:test-schema/cupo_oferta_exceso.sql"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PropagacionCupoOfertaIT {

    private static final long OFERTA = 50L;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withCreateContainerCmdModifier(c -> c.withName("integrity-e2e-tc-mysql-"
                    + java.util.UUID.randomUUID().toString().substring(0, 8)));

    @Autowired
    IndirectFunderOfferSupplierQuotaAdapter adapter;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void oferta() {
        jdbc.update("INSERT INTO indirect_funder_offer (id, supplier_quota_template, cupo_version) VALUES (?, 10000.00, 0)",
                OFERTA);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM indirect_funder_offer_supplier_quota");
        jdbc.update("DELETE FROM indirect_funder_offer");
        jdbc.update("DELETE FROM cupo_oferta_exceso");
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private void proveedor(long supplierId, String techo, String disponible, String plantilla) {
        jdbc.update("INSERT INTO indirect_funder_offer_supplier_quota (offer_id, supplier_id, rate, quota, current_quota,"
                + " original_quota, oferta_cupo_plantilla, oferta_cupo_version) VALUES (?, ?, 2.50, ?, ?, ?, ?, 0)",
                OFERTA, supplierId, d(techo), d(disponible), d(techo), plantilla == null ? null : d(plantilla));
    }

    private Map<String, Object> fila(long supplierId) {
        return jdbc.queryForMap("SELECT original_quota AS techo, current_quota AS disponible, quota,"
                + " oferta_cupo_plantilla AS plantilla, oferta_cupo_version AS version"
                + " FROM indirect_funder_offer_supplier_quota WHERE offer_id = ? AND supplier_id = ?", OFERTA, supplierId);
    }

    private void cambioEnRegistro(String plantilla, long version) {
        jdbc.update("UPDATE indirect_funder_offer SET supplier_quota_template = ?, cupo_version = ? WHERE id = ?",
                d(plantilla), version, OFERTA);
    }

    private static void techoYDisponible(Map<String, Object> f, String techo, String disponible) {
        assertThat((BigDecimal) f.get("techo")).isEqualByComparingTo(techo);
        assertThat((BigDecimal) f.get("quota")).isEqualByComparingTo(techo);
        assertThat((BigDecimal) f.get("disponible")).isEqualByComparingTo(disponible);
    }

    @Test
    void subir_suma_d_al_techo_y_al_disponible_de_cada_proveedor_que_ya_negocio() {
        proveedor(1, "10000.00", "4000.00", "10000.00");
        proveedor(2, "10000.00", "10000.00", "10000.00");

        Propagacion p = adapter.propagarCupoOferta(OFERTA, 1, d("15000.00"));

        assertThat(p.ajustadas()).isEqualTo(2);
        assertThat(p.porEncima()).isZero();
        techoYDisponible(fila(1), "15000.00", "9000.00");
        techoYDisponible(fila(2), "15000.00", "15000.00");
        assertThat(fila(1).get("version")).isEqualTo(1L);
        assertThat((BigDecimal) fila(1).get("plantilla")).isEqualByComparingTo("15000.00");
    }

    @Test
    void bajar_nunca_queda_por_debajo_de_lo_consumido_y_reporta_el_exceso() {
        proveedor(1, "10000.00", "4000.00", "10000.00");   // consumió 6.000
        proveedor(2, "10000.00", "10000.00", "10000.00");  // no consumió

        Propagacion p = adapter.propagarCupoOferta(OFERTA, 1, d("5000.00"));

        techoYDisponible(fila(1), "6000.00", "0.00");      // MAX(10.000 - 5.000, 6.000) = 6.000; disponible 0
        techoYDisponible(fila(2), "5000.00", "5000.00");
        assertThat(p.porEncima()).isEqualTo(1);              // el proveedor 1 quedó por encima de los 5.000 pedidos
    }

    @Test
    void el_proveedor_que_aun_no_negocia_toma_el_valor_vigente_en_su_primera_negociacion() {
        proveedor(1, "10000.00", "10000.00", "10000.00");
        cambioEnRegistro("12000.00", 1);
        adapter.propagarCupoOferta(OFERTA, 1, d("12000.00"));

        adapter.crear(OFERTA, 2L, d("2.50"), d("10000.00"));

        techoYDisponible(fila(2), "12000.00", "12000.00");
        assertThat(fila(2).get("version")).isEqualTo(1L);
        // un evento repetido de la versión 1 no lo vuelve a tocar
        assertThat(adapter.propagarCupoOferta(OFERTA, 1, d("12000.00")).ajustadas()).isZero();
        techoYDisponible(fila(2), "12000.00", "12000.00");
    }

    @Test
    void reentrega_y_reordenamiento_no_cambian_el_resultado() {
        proveedor(1, "10000.00", "7000.00", "10000.00");

        adapter.propagarCupoOferta(OFERTA, 2, d("12000.00"));   // llega primero el más nuevo
        Propagacion viejo = adapter.propagarCupoOferta(OFERTA, 1, d("15000.00"));
        Propagacion repetido = adapter.propagarCupoOferta(OFERTA, 2, d("12000.00"));

        assertThat(viejo.ajustadas()).isZero();
        assertThat(repetido.ajustadas()).isZero();
        techoYDisponible(fila(1), "12000.00", "9000.00");
    }

    @Test
    void las_filas_heredadas_con_techos_distintos_siguen_distintas_al_sumar_la_diferencia() {
        proveedor(1, "8000.00", "8000.00", "10000.00");   // negoció cuando la plantilla era 8.000
        proveedor(2, "10000.00", "10000.00", "10000.00");

        adapter.propagarCupoOferta(OFERTA, 1, d("15000.00"));

        techoYDisponible(fila(1), "13000.00", "13000.00");
        techoYDisponible(fila(2), "15000.00", "15000.00");
    }

    @Test
    void una_fila_sin_plantilla_creada_por_un_negociador_anterior_usa_su_techo_como_plantilla() {
        proveedor(1, "9000.00", "9000.00", null);

        adapter.propagarCupoOferta(OFERTA, 1, d("10000.00"));

        techoYDisponible(fila(1), "10000.00", "10000.00");
    }

    @Test
    void la_recarga_restaura_el_techo_ya_ajustado() {
        proveedor(1, "10000.00", "3000.00", "10000.00");
        adapter.propagarCupoOferta(OFERTA, 1, d("14000.00"));
        techoYDisponible(fila(1), "14000.00", "7000.00");

        adapter.recargar(OFERTA, 1L);

        techoYDisponible(fila(1), "14000.00", "14000.00");
    }

    private List<Map<String, Object>> excesos() {
        return jdbc.queryForList("SELECT supplier_id, consumido, plantilla_nueva, exceso, version FROM cupo_oferta_exceso"
                + " WHERE offer_id = ? ORDER BY version, supplier_id", OFERTA);
    }

    /**
     * Condición 5 de John: tres proveedores en la misma oferta, en una secuencia: subir, bajar por encima de lo
     * consumido en todos, bajar por debajo de lo consumido en solo uno (exceso persistido), reentrega del mismo evento y
     * una versión vieja que llega después de una nueva.
     */
    @Test
    void tres_proveedores_en_la_misma_oferta_subir_bajar_exceso_reentrega_y_version_vieja() {
        proveedor(1, "10000.00", "2000.00", "10000.00");   // consumió 8.000
        proveedor(2, "10000.00", "7000.00", "10000.00");   // consumió 3.000
        proveedor(3, "10000.00", "10000.00", "10000.00");  // no consumió

        // 1. subir a 12.000 (v1): +2.000 a techo y disponible de los tres
        assertThat(adapter.propagarCupoOferta(OFERTA, 1, d("12000.00")).ajustadas()).isEqualTo(3);
        techoYDisponible(fila(1), "12000.00", "4000.00");
        techoYDisponible(fila(2), "12000.00", "9000.00");
        techoYDisponible(fila(3), "12000.00", "12000.00");

        // 2. bajar a 9.000 (v2): por encima de lo consumido en todos (8.000, 3.000 y 0)
        Propagacion v2 = adapter.propagarCupoOferta(OFERTA, 2, d("9000.00"));
        assertThat(v2.ajustadas()).isEqualTo(3);
        assertThat(v2.porEncima()).isZero();
        techoYDisponible(fila(1), "9000.00", "1000.00");
        techoYDisponible(fila(2), "9000.00", "6000.00");
        techoYDisponible(fila(3), "9000.00", "9000.00");
        assertThat(excesos()).isEmpty();

        // 3. bajar a 5.000 (v3): por debajo de lo consumido SOLO en el proveedor 1 (8.000): techo 8.000, disponible 0
        Propagacion v3 = adapter.propagarCupoOferta(OFERTA, 3, d("5000.00"));
        assertThat(v3.porEncima()).isEqualTo(1);
        techoYDisponible(fila(1), "8000.00", "0.00");
        techoYDisponible(fila(2), "5000.00", "2000.00");
        techoYDisponible(fila(3), "5000.00", "5000.00");
        assertThat(excesos()).singleElement().satisfies(e -> {
            assertThat(e.get("supplier_id")).isEqualTo(1L);
            assertThat((BigDecimal) e.get("consumido")).isEqualByComparingTo("8000.00");
            assertThat((BigDecimal) e.get("plantilla_nueva")).isEqualByComparingTo("5000.00");
            assertThat((BigDecimal) e.get("exceso")).isEqualByComparingTo("3000.00");
            assertThat(e.get("version")).isEqualTo(3L);
        });

        // 4. reentrega del mismo evento (v3): nada cambia y el exceso no se duplica
        assertThat(adapter.propagarCupoOferta(OFERTA, 3, d("5000.00")).ajustadas()).isZero();
        techoYDisponible(fila(1), "8000.00", "0.00");
        techoYDisponible(fila(2), "5000.00", "2000.00");
        assertThat(excesos()).hasSize(1);

        // 5. llega tarde la v2 (9.000) después de la v3: la guarda monotónica la ignora
        assertThat(adapter.propagarCupoOferta(OFERTA, 2, d("9000.00")).ajustadas()).isZero();
        techoYDisponible(fila(1), "8000.00", "0.00");
        techoYDisponible(fila(2), "5000.00", "2000.00");
        techoYDisponible(fila(3), "5000.00", "5000.00");
        assertThat(fila(1).get("version")).isEqualTo(3L);
    }

    private static void correr(List<Runnable> tareas) throws InterruptedException {
        CountDownLatch listos = new CountDownLatch(tareas.size());
        CountDownLatch largada = new CountDownLatch(1);
        List<Throwable> fallos = Collections.synchronizedList(new ArrayList<>());
        List<Thread> hilos = new ArrayList<>();
        for (Runnable t : tareas) {
            Thread h = new Thread(() -> {
                listos.countDown();
                try {
                    largada.await();
                    t.run();
                } catch (Throwable e) {
                    fallos.add(e);
                }
            });
            h.start();
            hilos.add(h);
        }
        listos.await();
        largada.countDown();
        for (Thread h : hilos) {
            h.join();
        }
        assertThat(fallos).isEmpty();
    }

    @Test
    void ochenta_hilos_descuentos_y_subidas_sin_perdida_ni_negativos() throws InterruptedException {
        proveedor(1, "10000.00", "10000.00", "10000.00");
        List<Runnable> tareas = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            tareas.add(() -> assertThat(adapter.descontarCupo(OFERTA, 1L, d("10.00"))).isEqualTo(1));
        }
        for (int v = 1; v <= 20; v++) {
            final int version = v;
            tareas.add(() -> adapter.propagarCupoOferta(OFERTA, version, d("10000.00").add(d("100.00")
                    .multiply(BigDecimal.valueOf(version)))));
        }

        correr(tareas);

        // la subida final es la de la versión 20 (12.000), llegue en el orden que llegue; los 60 descuentos, todos
        techoYDisponible(fila(1), "12000.00", "11400.00");
        assertThat(fila(1).get("version")).isEqualTo(20L);
    }

    @Test
    void ochenta_hilos_con_descuentos_recargas_y_cambios_nunca_negativo_ni_sobre_el_techo() throws InterruptedException {
        proveedor(1, "10000.00", "10000.00", "10000.00");
        List<Runnable> tareas = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            tareas.add(() -> adapter.descontarCupo(OFERTA, 1L, d("150.00")));
        }
        for (int i = 0; i < 20; i++) {
            tareas.add(() -> adapter.recargar(OFERTA, 1L));
        }
        for (int v = 1; v <= 20; v++) {
            final int version = v;
            // baja y sube: termina en la versión 20 = 6.000
            tareas.add(() -> adapter.propagarCupoOferta(OFERTA, version,
                    d(version % 2 == 0 ? "8000.00" : "4000.00").subtract(version == 20 ? d("2000.00") : BigDecimal.ZERO)));
        }

        correr(tareas);
        adapter.recargar(OFERTA, 1L);

        Map<String, Object> f = fila(1);
        assertThat((BigDecimal) f.get("disponible")).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat((BigDecimal) f.get("disponible")).isLessThanOrEqualTo((BigDecimal) f.get("techo"));
        assertThat(f.get("version")).isEqualTo(20L);
        assertThat((BigDecimal) f.get("plantilla")).isEqualByComparingTo("6000.00");
        // tras la última recarga no queda nada consumido: el techo es el de la versión 20, salvo que lo consumido lo
        // haya dejado arriba al bajar (nunca por debajo de 6.000 ni por encima del mayor techo posible)
        assertThat((BigDecimal) f.get("techo")).isBetween(d("6000.00"), d("16000.00"));
        assertThat((BigDecimal) f.get("disponible")).isEqualByComparingTo((BigDecimal) f.get("techo"));
    }
}
