package com.negociadororq.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

import com.negociadororq.application.dto.EndosoRequestDto;
import com.negociadororq.application.dto.NegociacionRequestDto;
import com.negociadororq.application.dto.ValidacionFacturaResponseDto;
import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.EmpresaQuotaOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.application.port.output.IndirectFunderOfferOutputPort;
import com.negociadororq.application.port.output.IndirectFunderOfferSupplierQuotaOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.port.output.PayerFundingVisibilityOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.application.port.output.UserQuotaOutputPort;
import com.negociadororq.application.service.AprobacionEndosoDescuentoServiceImpl;
import com.negociadororq.application.service.CupoReversionService;
import com.negociadororq.application.service.EndosoDescuentoServiceImpl;
import com.negociadororq.application.service.FacturaVencimientoService;
import com.negociadororq.application.service.NotificacionService;
import com.negociadororq.application.service.OperadorServiceImpl;
import com.negociadororq.application.service.ProcesarFacturaNegociacionServiceImpl;
import com.negociadororq.application.service.ReversionCuposServiceImpl;
import com.negociadororq.application.service.ReversionExactaService;
import com.negociadororq.application.service.ValidacionFacturaService;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.IndirectFunderOffer;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.OperacionPendiente;
import com.negociadororq.domain.model.Operacion;
import com.negociadororq.domain.model.User;
import com.negociadororq.infrastructure.persistence.adapter.EmpresaQuotaAdapter;
import com.negociadororq.infrastructure.persistence.adapter.FondeadorQuotaAdapter;
import com.negociadororq.infrastructure.persistence.adapter.IndirectFunderOfferSupplierQuotaAdapter;
import com.negociadororq.infrastructure.persistence.adapter.OperacionPendienteJdbcAdapter;
import com.negociadororq.infrastructure.persistence.adapter.UserQuotaAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Fase 28d (A2), MySQL real: conservación de cupos al centavo. Se crea la negociación de una factura (descuento real de
 * operador, empresa y fondeador directo, indirecto o de CONFIRMING, con el registro exacto en operacion_pendiente en la
 * misma transacción) y se revierte por cada camino (liquidación fallida, rechazo por OTP, endoso rechazado, vencimiento):
 * todos los cupos vuelven a ser idénticos a los iniciales. La factura tiene neto distinto del bruto (defecto 1: la
 * fallida devolvía el bruto). Factura, negociación, firma y notificaciones (Mongo) son dobles.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({UserQuotaAdapter.class, EmpresaQuotaAdapter.class, FondeadorQuotaAdapter.class,
        IndirectFunderOfferSupplierQuotaAdapter.class, ConservacionCuposIT.Beans.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:test-schema/reversion_cupos.sql,"
                + "classpath:test-schema/indirect_funder_offer_supplier_quota.sql,"
                + "classpath:db/migration/V3__operacion_pendiente.sql"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConservacionCuposIT {

    private static final long USUARIO = 1L;
    private static final long EMPRESA_PROV = 30L;
    private static final long EMPRESA_PROV_IND = 31L;
    private static final long EMPRESA_PAG = 40L;
    private static final long FONDEADOR = 20L;
    private static final long OFERTA = 77L;

    /** Bruto y neto distintos: la creación descuenta el neto. */
    private static final BigDecimal BRUTO = new BigDecimal("1000000.00");
    private static final BigDecimal NETO = new BigDecimal("950000.37");

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withCreateContainerCmdModifier(c -> c.withName("integrity-e2e-tc-mysql-"
                    + java.util.UUID.randomUUID().toString().substring(0, 8)));

    @TestConfiguration
    static class Beans {
        @Bean
        OperadorServiceImpl operadorServiceImpl(UserQuotaOutputPort userQuotaPort,
                EmpresaQuotaOutputPort empresaQuotaPort, FondeadorQuotaOutputPort fondeadorQuotaPort) {
            return new OperadorServiceImpl(userQuotaPort, empresaQuotaPort, fondeadorQuotaPort);
        }

        @Bean
        OperacionPendienteJdbcAdapter operacionPendienteJdbcAdapter(JdbcTemplate jdbc,
                NamedParameterJdbcTemplate conNombres) {
            ClockPort reloj = LocalDateTime::now;
            return new OperacionPendienteJdbcAdapter(jdbc, conNombres, reloj);
        }
    }

    enum Modo { FACTORING_DIRECTO, FACTORING_INDIRECTO, CONFIRMING }

    enum Camino { LIQUIDACION_FALLIDA, RECHAZO, ENDOSO_RECHAZADO, VENCIMIENTO }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    OperadorServiceImpl operador;
    @Autowired
    EmpresaQuotaOutputPort empresaQuotaPort;
    @Autowired
    FondeadorQuotaOutputPort fondeadorQuotaPort;
    @Autowired
    IndirectFunderOfferSupplierQuotaOutputPort indirectoPort;
    @Autowired
    OperacionPendienteJdbcAdapter operaciones;
    @Autowired
    TransactionTemplate tx;

    private final FacturaOutputPort facturaPort = mock(FacturaOutputPort.class);
    private final NegociacionOutputPort negociacionPort = mock(NegociacionOutputPort.class);
    private final FacturaPatchOutputPort facturaPatchPort = mock(FacturaPatchOutputPort.class);
    private final UserOutputPort userPort = mock(UserOutputPort.class);
    private final CompanyOutputPort companyPort = mock(CompanyOutputPort.class);
    private final NotificacionService notificacionService = mock(NotificacionService.class);
    private final ClockPort clock = LocalDateTime::now;

    private ReversionExactaService exacta;
    private ProcesarFacturaNegociacionServiceImpl procesar;
    private int secuencia;

    @BeforeEach
    void preparar() {
        for (String tabla : List.of("operacion_pendiente", "indirect_funder_offer_supplier_quota", "user_quota",
                "`user`", "parametrization", "funder")) {
            jdbc.update("DELETE FROM " + tabla);
        }
        jdbc.update("INSERT INTO `user` (ide_user, username, password) VALUES (?, 'op-1', 'x')", USUARIO);
        jdbc.update("INSERT INTO user_quota (quota, current_quota, user_id) VALUES (9000000.00, 4000000.11, ?)", USUARIO);
        for (long empresa : List.of(EMPRESA_PROV, EMPRESA_PROV_IND, EMPRESA_PAG)) {
            jdbc.update("INSERT INTO parametrization (company_id, quota, current_quota, version)"
                    + " VALUES (?, 9000000.00, 5000000.22, 0)", empresa);
        }
        jdbc.update("INSERT INTO funder (funder_id, supplier_id, supplier_quota, supplier_current_quota, estado,"
                + " aprobado_por_fondeador, activo, version) VALUES (?, ?, 9000000.00, 3000000.33, 'APROBADO', 1, 1, 0)",
                FONDEADOR, EMPRESA_PROV);
        jdbc.update("INSERT INTO funder (funder_id, payer_id, payer_quota, payer_current_quota, estado,"
                + " aprobado_por_fondeador, activo, version) VALUES (?, ?, 9000000.00, 2000000.44, 'APROBADO', 1, 1, 0)",
                FONDEADOR, EMPRESA_PAG);
        jdbc.update("INSERT INTO indirect_funder_offer_supplier_quota (offer_id, supplier_id, rate, quota, current_quota,"
                + " original_quota) VALUES (?, ?, 0.0150, 9000000.00, 1500000.55, 9000000.00)", OFERTA, EMPRESA_PROV_IND);

        when(userPort.findByUsername("op-1")).thenReturn(Optional.of(User.builder().ideUser(USUARIO).build()));
        when(companyPort.findByNit("PROV")).thenReturn(Optional.of(empresa(EMPRESA_PROV)));
        when(companyPort.findByNit("PROVIND")).thenReturn(Optional.of(empresa(EMPRESA_PROV_IND)));
        when(companyPort.findByNit("PAG")).thenReturn(Optional.of(empresa(EMPRESA_PAG)));
        when(companyPort.findByNit("FOND")).thenReturn(Optional.of(empresa(FONDEADOR)));

        ValidacionFacturaService validacion = mock(ValidacionFacturaService.class);
        when(validacion.validarFacturaVencida(any(), any()))
                .thenReturn(ValidacionFacturaResponseDto.builder().valida(true).build());
        PayerFundingVisibilityOutputPort visibilidad = mock(PayerFundingVisibilityOutputPort.class);
        when(visibilidad.estaHabilitado(anyLong())).thenReturn(true);
        IndirectFunderOfferOutputPort ofertas = mock(IndirectFunderOfferOutputPort.class);
        when(ofertas.findActivaAprobada(FONDEADOR, EMPRESA_PAG)).thenReturn(Optional.of(IndirectFunderOffer.builder()
                .id(OFERTA).funderId(FONDEADOR).payerId(EMPRESA_PAG).payerRate(new BigDecimal("0.0150"))
                .supplierQuotaTemplate(new BigDecimal("9000000.00")).estado("APROBADO").activo(true).build()));

        exacta = new ReversionExactaService(operaciones, operador, indirectoPort, tx);
        procesar = new ProcesarFacturaNegociacionServiceImpl(facturaPort, operador, userPort, companyPort,
                empresaQuotaPort, fondeadorQuotaPort, facturaPatchPort, validacion, visibilidad, ofertas, indirectoPort,
                clock, operaciones);
    }

    static Stream<Arguments> combinaciones() {
        List<Arguments> lista = new ArrayList<>();
        for (Camino c : Camino.values()) {
            for (Modo m : Modo.values()) {
                lista.add(Arguments.of(c, m));
            }
        }
        return lista.stream();
    }

    @ParameterizedTest(name = "{0} en {1}: todos los cupos quedan idénticos a los iniciales")
    @MethodSource("combinaciones")
    void crear_y_revertir_conserva_todos_los_cupos_al_centavo(Camino camino, Modo modo) {
        Map<String, BigDecimal> inicial = cupos();
        Escenario e = crear(modo);

        Map<String, BigDecimal> tras = cupos();
        assertThat(tras.get("operador")).isEqualByComparingTo(inicial.get("operador").subtract(NETO));
        assertThat(tras.get(empresaClave(modo))).isEqualByComparingTo(inicial.get(empresaClave(modo)).subtract(NETO));
        assertThat(tras.get(fondeoClave(modo))).isEqualByComparingTo(inicial.get(fondeoClave(modo)).subtract(NETO));

        revertir(camino, e);
        assertIguales(inicial, cupos());
        assertThat(estado(e)).isEqualTo(ReversionExactaService.REVERTIDA);

        // reversión repetida, por el mismo camino y por otro: una sola vez
        revertir(camino, e);
        revertir(Camino.LIQUIDACION_FALLIDA, e);
        assertIguales(inicial, cupos());
    }

    @RepeatedTest(20)
    void dos_caminos_a_la_vez_revierten_una_sola_vez() throws InterruptedException {
        Map<String, BigDecimal> inicial = cupos();
        Escenario e = crear(Modo.FACTORING_INDIRECTO);
        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch largada = new CountDownLatch(1);
        List<Throwable> fallos = Collections.synchronizedList(new ArrayList<>());
        Thread t1 = hilo(() -> exacta.revertir(com.negociadororq.domain.model.TipoOperacionPendiente.LIQUIDACION_FALLIDA,
                e.negociacionId, e.cufe, "fallida"), listos, largada, fallos);
        Thread t2 = hilo(() -> exacta.revertir(com.negociadororq.domain.model.TipoOperacionPendiente.RECHAZO,
                e.negociacionId, e.cufe, "rechazo"), listos, largada, fallos);
        listos.await();
        largada.countDown();
        t1.join();
        t2.join();
        assertThat(fallos).isEmpty();
        assertIguales(inicial, cupos());
    }

    @Test
    void sin_registro_de_lo_descontado_va_a_revision_manual_y_no_toca_cupos() {
        Map<String, BigDecimal> inicial = cupos();
        ReversionExactaService.Resultado r = exacta.revertir(
                com.negociadororq.domain.model.TipoOperacionPendiente.VENCIMIENTO, "neg-anterior", "CUFE-ANTERIOR",
                "vencimiento");
        assertThat(r).isEqualTo(ReversionExactaService.Resultado.SIN_REGISTRO);
        assertIguales(inicial, cupos());
        assertThat(jdbc.queryForObject("SELECT estado FROM operacion_pendiente WHERE tipo = 'VENCIMIENTO'"
                + " AND clave_natural = 'neg-anterior|CUFE-ANTERIOR'", String.class)).isEqualTo("REVISION_MANUAL");
    }

    @Test
    void un_fallo_a_mitad_no_deja_ningun_cupo_cambiado_y_el_reintento_revierte_exacto() {
        Map<String, BigDecimal> inicial = cupos();
        Escenario e = crear(Modo.CONFIRMING);
        Map<String, BigDecimal> tras = cupos();
        jdbc.update("UPDATE funder SET payer_id = 999 WHERE funder_id = ? AND payer_id = ?", FONDEADOR, EMPRESA_PAG);

        assertThatThrownBy(() -> exacta.revertir(com.negociadororq.domain.model.TipoOperacionPendiente.RECHAZO,
                e.negociacionId, e.cufe, "rechazo")).isInstanceOf(RuntimeException.class);
        jdbc.update("UPDATE funder SET payer_id = ? WHERE funder_id = ? AND payer_id = 999", EMPRESA_PAG, FONDEADOR);
        assertIguales(tras, cupos());
        assertThat(estado(e)).isEqualTo(ReversionExactaService.COMPLETADA);

        exacta.revertir(com.negociadororq.domain.model.TipoOperacionPendiente.RECHAZO, e.negociacionId, e.cufe,
                "rechazo");
        assertIguales(inicial, cupos());
    }

    // ─── apoyo ─────────────────────────────────────────────────────────────────────────────────────────────────────

    private static final class Escenario {
        String negociacionId;
        String cufe;
        String numero;
        Modo modo;
        Factura factura;
        FacturaNegociada negociada;
        Negociacion negociacion;
    }

    private Escenario crear(Modo modo) {
        Escenario e = new Escenario();
        int n = ++secuencia;
        e.modo = modo;
        e.numero = "F-" + n;
        e.cufe = "CUFE-" + modo + "-" + n + "-" + System.nanoTime();
        e.negociacionId = "neg-" + n + "-" + System.nanoTime();
        String tipo = modo == Modo.CONFIRMING ? "CONFIRMING" : "FACTORING";
        String proveedor = modo == Modo.FACTORING_INDIRECTO ? "PROVIND" : "PROV";
        String empresa = modo == Modo.CONFIRMING ? "PAG" : proveedor;

        Factura f = new Factura();
        f.setNumeroFactura(e.numero);
        f.setCufe(e.cufe);
        f.setRutProveedor(proveedor);
        f.setRutPagador("PAG");
        f.setValorFactura(BRUTO);
        f.setValorNetoFactura(NETO);
        f.setEstadoNegociacion("SIN_NEGOCIAR");
        f.setFechavencimientoFactura(LocalDate.now().plusDays(60));
        f.setActores(new ArrayList<>());
        e.factura = f;
        when(facturaPort.findByCufe(e.cufe)).thenReturn(Optional.of(f));

        FacturaNegociada solicitud = FacturaNegociada.builder().numeroFactura(e.numero).cufe(e.cufe)
                .rutProveedor(proveedor).rutPagador("PAG")
                .fondeadores(List.of(FondeadorAsignacion.builder().rutFondeador("FOND").build())).build();
        NegociacionRequestDto request = NegociacionRequestDto.builder().rutEmpresa(empresa)
                .tipoOperacion(new Operacion(tipo, modo == Modo.CONFIRMING ? "TRADICIONAL" : "CON_RESPONSABILIDAD"))
                .rutOperador("op-1").facturas(List.of(solicitud)).build();
        e.negociada = tx.execute(s -> procesar.procesarFactura(request, solicitud, e.negociacionId));

        e.negociacion = Negociacion.builder().id(e.negociacionId).rutEmpresa(empresa)
                .tipoOperacion(request.getTipoOperacion()).facturas(List.of(e.negociada))
                .rutFondeadores(List.of("FOND")).build();
        when(negociacionPort.findById(e.negociacionId)).thenReturn(Optional.of(e.negociacion));
        f.setEstadoNegociacion("NEGOCIADA");
        f.setNegociacionId(e.negociacionId);
        return e;
    }

    private void revertir(Camino camino, Escenario e) {
        switch (camino) {
            case LIQUIDACION_FALLIDA -> new ReversionCuposServiceImpl(negociacionPort, facturaPort, facturaPatchPort,
                    notificacionService, exacta).revertirPorFactura(e.negociacionId, e.cufe, e.numero, "op-1", "fallo");
            case RECHAZO -> new AprobacionEndosoDescuentoServiceImpl(negociacionPort, facturaPatchPort, null, null,
                    exacta, companyPort, fondeadorQuotaPort, facturaPort, userPort, null, null, notificacionService, null,
                    null, clock, "otp.requested", "liquidacion", null)
                    .revertirCupos(e.negociacionId, e.negociada, "rechazo");
            case ENDOSO_RECHAZADO -> {
                // La factura ya no está en la base: el endoso la rechaza y revierte sus cupos.
                when(facturaPort.findByCufe(e.cufe)).thenReturn(Optional.empty());
                EndosoRequestDto req = new EndosoRequestDto();
                req.setIdNegociacion(e.negociacionId);
                req.setRutActor(e.modo == Modo.CONFIRMING ? "PAG" : e.factura.getRutProveedor());
                req.setRutOperador("op-1");
                req.setNumerosFactura(List.of(e.numero));
                try {
                    new EndosoDescuentoServiceImpl(negociacionPort, mock(com.negociadororq.application.port.output.EndosoOutputPort.class),
                            facturaPort, notificacionService, facturaPatchPort, null,
                            mock(com.negociadororq.application.port.output.NegociacionPatchOutputPort.class), companyPort,
                            fondeadorQuotaPort, userPort, exacta, clock,
                            mock(com.negociadororq.application.service.AuditoriaNegociacionService.class))
                            .registrarEndoso(req);
                } catch (RuntimeException sinFacturasValidas) {
                    // sin facturas válidas el endoso responde con error: la reversión ya ocurrió
                }
                when(facturaPort.findByCufe(e.cufe)).thenReturn(Optional.of(e.factura));
            }
            case VENCIMIENTO -> {
                e.factura.setFechavencimientoFactura(LocalDate.now().plusDays(3));
                when(facturaPort.findFacturasPendientesVencimiento()).thenReturn(List.of(e.factura));
                new FacturaVencimientoService(facturaPort, negociacionPort, companyPort, exacta, clock)
                        .actualizarFacturasVencidas();
                e.factura.setEstadoNegociacion("NEGOCIADA");
            }
            default -> throw new IllegalStateException();
        }
    }

    private static Thread hilo(Runnable r, CountDownLatch listos, CountDownLatch largada, List<Throwable> fallos) {
        Thread t = new Thread(() -> {
            listos.countDown();
            try {
                largada.await();
                r.run();
            } catch (Throwable ex) {
                fallos.add(ex);
            }
        });
        t.start();
        return t;
    }

    private String estado(Escenario e) {
        return operaciones.buscar(com.negociadororq.domain.model.TipoOperacionPendiente.CREAR_NEGOCIACION,
                OperacionPendiente.clave(e.negociacionId, e.cufe)).orElseThrow().estado();
    }

    private Map<String, BigDecimal> cupos() {
        Map<String, BigDecimal> m = new java.util.LinkedHashMap<>();
        m.put("operador", jdbc.queryForObject("SELECT current_quota FROM user_quota WHERE user_id = ?",
                BigDecimal.class, USUARIO));
        for (long empresa : List.of(EMPRESA_PROV, EMPRESA_PROV_IND, EMPRESA_PAG)) {
            m.put("empresa-" + empresa, jdbc.queryForObject("SELECT current_quota FROM parametrization"
                    + " WHERE company_id = ?", BigDecimal.class, empresa));
        }
        m.put("factoring", jdbc.queryForObject("SELECT supplier_current_quota FROM funder WHERE funder_id = ?"
                + " AND supplier_id = ?", BigDecimal.class, FONDEADOR, EMPRESA_PROV));
        m.put("confirming", jdbc.queryForObject("SELECT payer_current_quota FROM funder WHERE funder_id = ?"
                + " AND payer_id = ?", BigDecimal.class, FONDEADOR, EMPRESA_PAG));
        m.put("indirecto", jdbc.queryForObject("SELECT current_quota FROM indirect_funder_offer_supplier_quota"
                + " WHERE offer_id = ? AND supplier_id = ?", BigDecimal.class, OFERTA, EMPRESA_PROV_IND));
        return m;
    }

    private static String empresaClave(Modo modo) {
        return switch (modo) {
            case FACTORING_DIRECTO -> "empresa-" + EMPRESA_PROV;
            case FACTORING_INDIRECTO -> "empresa-" + EMPRESA_PROV_IND;
            case CONFIRMING -> "empresa-" + EMPRESA_PAG;
        };
    }

    private static String fondeoClave(Modo modo) {
        return switch (modo) {
            case FACTORING_DIRECTO -> "factoring";
            case FACTORING_INDIRECTO -> "indirecto";
            case CONFIRMING -> "confirming";
        };
    }

    private static void assertIguales(Map<String, BigDecimal> esperado, Map<String, BigDecimal> real) {
        for (Map.Entry<String, BigDecimal> entrada : esperado.entrySet()) {
            assertThat(real.get(entrada.getKey())).as(entrada.getKey()).isEqualByComparingTo(entrada.getValue());
        }
    }

    private static Company empresa(long id) {
        return Company.builder().ideCompany(id).build();
    }
}
