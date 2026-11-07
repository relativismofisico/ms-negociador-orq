package com.negociadororq.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.EmpresaQuotaOutputPort;
import com.negociadororq.application.port.output.EndosoPatchOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.application.port.output.UserQuotaOutputPort;
import com.negociadororq.application.service.CupoReversionService;
import com.negociadororq.application.service.NotificacionService;
import com.negociadororq.application.service.OperadorServiceImpl;
import com.negociadororq.application.service.ReversionCuposServiceImpl;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.Operacion;
import com.negociadororq.domain.model.User;
import com.negociadororq.infrastructure.persistence.adapter.EmpresaQuotaAdapter;
import com.negociadororq.infrastructure.persistence.adapter.FondeadorQuotaAdapter;
import com.negociadororq.infrastructure.persistence.adapter.ReversionCuposReclamoAdapter;
import com.negociadororq.infrastructure.persistence.adapter.UserQuotaAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@code revertirPorFactura} contra MySQL real: cupos de operador, empresa y fondeador (factoring y confirming) y el
 * reclamo {@code reversion_cupos_factura} en la misma transacción, con {@link OperadorServiceImpl} como bean (sus
 * {@code @Transactional} aplican de verdad, incluido el {@code REQUIRES_NEW} de {@code revertirCupoEmpresa} que esta
 * ruta ya no usa). Factura, negociación, firma y notificación (Mongo) son dobles.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({UserQuotaAdapter.class, EmpresaQuotaAdapter.class, FondeadorQuotaAdapter.class,
        ReversionCuposReclamoAdapter.class, ReversionCuposIdempotenciaIT.Operador.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:test-schema/reversion_cupos.sql,"
                + "classpath:db/migration/V2__reversion_cupos_factura.sql"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReversionCuposIdempotenciaIT {

    private static final long USUARIO = 1L;
    private static final long EMPRESA = 10L;
    private static final long FONDEADOR = 20L;
    private static final long PROVEEDOR = 30L;
    private static final long PAGADOR = 40L;

    private static final BigDecimal VALOR_FACTORING = new BigDecimal("150000.50");
    private static final BigDecimal VALOR_CONFIRMING = new BigDecimal("70000.25");

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @TestConfiguration
    static class Operador {
        @Bean
        OperadorServiceImpl operadorServiceImpl(UserQuotaOutputPort userQuotaPort,
                EmpresaQuotaOutputPort empresaQuotaPort, FondeadorQuotaOutputPort fondeadorQuotaPort) {
            return new OperadorServiceImpl(userQuotaPort, empresaQuotaPort, fondeadorQuotaPort);
        }
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CupoReversionService cupoReversionService;

    @Autowired
    ReversionCuposReclamoAdapter reclamoAdapter;

    @Autowired
    TransactionTemplate transactionTemplate;

    private final FacturaOutputPort facturaPort = mock(FacturaOutputPort.class);
    private final NegociacionOutputPort negociacionPort = mock(NegociacionOutputPort.class);
    private final FacturaPatchOutputPort facturaPatchPort = mock(FacturaPatchOutputPort.class);
    private final UserOutputPort userPort = mock(UserOutputPort.class);
    private final CompanyOutputPort companyPort = mock(CompanyOutputPort.class);
    private final NotificacionService notificacionService = mock(NotificacionService.class);

    private ReversionCuposServiceImpl service;

    @BeforeEach
    void preparar() {
        for (String tabla : List.of("reversion_cupos_factura", "user_quota", "`user`", "parametrization", "funder")) {
            jdbc.update("DELETE FROM " + tabla);
        }
        jdbc.update("INSERT INTO `user` (ide_user, username, password) VALUES (?, 'op-1', 'x')", USUARIO);
        jdbc.update("INSERT INTO user_quota (quota, current_quota, user_id) VALUES (1000000.00, 400000.00, ?)", USUARIO);
        jdbc.update("INSERT INTO parametrization (company_id, quota, current_quota, version)"
                + " VALUES (?, 5000000.00, 2000000.00, 0)", EMPRESA);
        jdbc.update("INSERT INTO funder (funder_id, supplier_id, supplier_quota, supplier_current_quota, estado, activo,"
                + " version) VALUES (?, ?, 3000000.00, 1000000.00, 'APROBADO', 1, 0)", FONDEADOR, PROVEEDOR);
        jdbc.update("INSERT INTO funder (funder_id, payer_id, payer_quota, payer_current_quota, estado, activo, version)"
                + " VALUES (?, ?, 2000000.00, 500000.00, 'APROBADO', 1, 0)", FONDEADOR, PAGADOR);

        when(userPort.findByUsername("op-1")).thenReturn(Optional.of(User.builder().ideUser(USUARIO).build()));
        when(companyPort.findByNit("EMP")).thenReturn(Optional.of(empresa(EMPRESA)));
        when(companyPort.findByNit("FOND")).thenReturn(Optional.of(empresa(FONDEADOR)));
        when(companyPort.findByNit("PROV")).thenReturn(Optional.of(empresa(PROVEEDOR)));
        when(companyPort.findByNit("PAG")).thenReturn(Optional.of(empresa(PAGADOR)));

        service = new ReversionCuposServiceImpl(negociacionPort, facturaPort, facturaPatchPort,
                mock(EndosoPatchOutputPort.class), cupoReversionService, userPort, companyPort, notificacionService,
                reclamoAdapter, transactionTemplate);
    }

    @Test
    void a_fallida_duplicada_revierte_una_sola_vez_operador_empresa_y_fondeadores() {
        // La firma NO se refleja en la relectura (el doble no la guarda): el segundo intento no lo frena la guarda
        // de estado sino el reclamo en MySQL.
        registrar("F-FACT", "neg-1", "FACTORING", VALOR_FACTORING, "CESION");
        registrar("F-CONF", "neg-2", "CONFIRMING", VALOR_CONFIRMING, "DESCUENTO");

        service.revertirPorFactura("F-FACT", "op-1", "fallo");
        service.revertirPorFactura("F-FACT", "op-1", "fallo");
        service.revertirPorFactura("F-CONF", "op-1", "fallo");
        service.revertirPorFactura("F-CONF", "op-1", "fallo");

        BigDecimal total = VALOR_FACTORING.add(VALOR_CONFIRMING);
        assertThat(cupoUsuario()).isEqualByComparingTo(new BigDecimal("400000.00").add(total));
        assertThat(cupoEmpresa()).isEqualByComparingTo(new BigDecimal("2000000.00").add(total));
        assertThat(cupoFondeadorFactoring()).isEqualByComparingTo(new BigDecimal("1000000.00").add(VALOR_FACTORING));
        assertThat(cupoFondeadorConfirming()).isEqualByComparingTo(new BigDecimal("500000.00").add(VALOR_CONFIRMING));
        assertThat(reclamos()).isEqualTo(2);
        verify(notificacionService, times(1)).notificarLiquidacionFallida(any(), eq("F-FACT"), anyString());
        verify(notificacionService, times(1)).notificarLiquidacionFallida(any(), eq("F-CONF"), anyString());
        // El duplicado completa la firma (idempotente) por si el primero no llegó a marcarla.
        verify(facturaPatchPort, times(2)).actualizarEstadoFirmaPorNoFactura("F-FACT", EstadoFirma.RECHAZADA);
    }

    @Test
    void b_fallo_a_mitad_no_deja_ningun_cupo_cambiado_y_el_reintento_revierte_bien() {
        registrar("F-MITAD", "neg-3", "FACTORING", VALOR_FACTORING, "CESION");
        when(companyPort.findByNit("FOND")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revertirPorFactura("F-MITAD", "op-1", "fallo"))
                .isInstanceOf(RecursoNoEncontradoException.class);

        // Operador y empresa ya se habían revertido antes del fondeador: nada quedó confirmado.
        assertThat(cupoUsuario()).isEqualByComparingTo("400000.00");
        assertThat(cupoEmpresa()).isEqualByComparingTo("2000000.00");
        assertThat(cupoFondeadorFactoring()).isEqualByComparingTo("1000000.00");
        assertThat(reclamos()).isZero();
        verify(facturaPatchPort, never()).actualizarEstadoFirmaPorNoFactura(anyString(), any());
        verify(notificacionService, never()).notificarLiquidacionFallida(any(), anyString(), anyString());

        when(companyPort.findByNit("FOND")).thenReturn(Optional.of(empresa(FONDEADOR)));
        service.revertirPorFactura("F-MITAD", "op-1", "fallo");

        assertThat(cupoUsuario()).isEqualByComparingTo(new BigDecimal("400000.00").add(VALOR_FACTORING));
        assertThat(cupoEmpresa()).isEqualByComparingTo(new BigDecimal("2000000.00").add(VALOR_FACTORING));
        assertThat(cupoFondeadorFactoring()).isEqualByComparingTo(new BigDecimal("1000000.00").add(VALOR_FACTORING));
        assertThat(reclamos()).isEqualTo(1);
        verify(notificacionService, times(1)).notificarLiquidacionFallida(any(), eq("F-MITAD"), anyString());
    }

    @Test
    void c_factura_liquidada_conserva_cupos_y_firma_y_no_notifica() {
        Factura factura = registrar("F-LIQ", "neg-4", "FACTORING", VALOR_FACTORING, "CESION");
        factura.setEstadoNegociacion("LIQUIDADA");

        service.revertirPorFactura("F-LIQ", "op-1", "fallo");

        assertThat(cupoUsuario()).isEqualByComparingTo("400000.00");
        assertThat(cupoEmpresa()).isEqualByComparingTo("2000000.00");
        assertThat(cupoFondeadorFactoring()).isEqualByComparingTo("1000000.00");
        assertThat(reclamos()).isZero();
        verify(facturaPatchPort, never()).actualizarEstadoFirmaPorNoFactura(anyString(), any());
        verify(notificacionService, never()).notificarLiquidacionFallida(any(), anyString(), anyString());
    }

    @Test
    void d_dos_mensajes_concurrentes_sobre_la_misma_factura_revierten_una_sola_vez() throws InterruptedException {
        registrar("F-CONC", "neg-5", "FACTORING", VALOR_FACTORING, "CESION");

        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch largada = new CountDownLatch(1);
        List<Throwable> fallos = Collections.synchronizedList(new ArrayList<>());
        Runnable entrega = () -> {
            listos.countDown();
            try {
                largada.await();
                service.revertirPorFactura("F-CONC", "op-1", "fallo");
            } catch (Exception e) {
                fallos.add(e);
            }
        };
        Thread t1 = new Thread(entrega);
        Thread t2 = new Thread(entrega);
        t1.start();
        t2.start();
        listos.await();
        largada.countDown();
        t1.join();
        t2.join();

        assertThat(fallos).isEmpty();
        assertThat(cupoUsuario()).isEqualByComparingTo(new BigDecimal("400000.00").add(VALOR_FACTORING));
        assertThat(cupoEmpresa()).isEqualByComparingTo(new BigDecimal("2000000.00").add(VALOR_FACTORING));
        assertThat(cupoFondeadorFactoring()).isEqualByComparingTo(new BigDecimal("1000000.00").add(VALOR_FACTORING));
        assertThat(reclamos()).isEqualTo(1);
        verify(notificacionService, times(1)).notificarLiquidacionFallida(any(), eq("F-CONC"), anyString());
    }

    private Factura registrar(String numero, String negociacionId, String tipo, BigDecimal valor, String firma) {
        Factura factura = new Factura();
        factura.setNumeroFactura(numero);
        factura.setRutProveedor("PROV");
        factura.setRutPagador("PAG");
        factura.setEstadoNegociacion("NEGOCIADA");
        factura.setEstadoFirma(firma);
        FacturaNegociada negociada = FacturaNegociada.builder()
                .numeroFactura(numero)
                .valorFactura(valor)
                .fondeadores(List.of(FondeadorAsignacion.builder().rutFondeador("FOND").build()))
                .build();
        Negociacion negociacion = Negociacion.builder()
                .id(negociacionId)
                .rutEmpresa("EMP")
                .tipoOperacion(new Operacion(tipo, "CON_RESPONSABILIDAD"))
                .facturas(List.of(negociada))
                .build();
        when(facturaPort.findByNumeroFactura(numero)).thenReturn(Optional.of(factura));
        when(negociacionPort.findByNumeroFactura(numero)).thenReturn(Optional.of(negociacion));
        return factura;
    }

    private static Company empresa(long id) {
        return Company.builder().ideCompany(id).build();
    }

    private BigDecimal cupoUsuario() {
        return jdbc.queryForObject("SELECT current_quota FROM user_quota WHERE user_id = ?", BigDecimal.class, USUARIO);
    }

    private BigDecimal cupoEmpresa() {
        return jdbc.queryForObject("SELECT current_quota FROM parametrization WHERE company_id = ?", BigDecimal.class,
                EMPRESA);
    }

    private BigDecimal cupoFondeadorFactoring() {
        return jdbc.queryForObject("SELECT supplier_current_quota FROM funder WHERE funder_id = ? AND supplier_id = ?",
                BigDecimal.class, FONDEADOR, PROVEEDOR);
    }

    private BigDecimal cupoFondeadorConfirming() {
        return jdbc.queryForObject("SELECT payer_current_quota FROM funder WHERE funder_id = ? AND payer_id = ?",
                BigDecimal.class, FONDEADOR, PAGADOR);
    }

    private long reclamos() {
        Long filas = jdbc.queryForObject("SELECT COUNT(*) FROM reversion_cupos_factura", Long.class);
        return filas == null ? 0L : filas;
    }
}
