package com.negociadororq.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
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
import java.util.concurrent.ThreadLocalRandom;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.negociadororq.application.mapper.EstadoFacturaMapper;
import com.negociadororq.application.service.FirmaFinalLiquidacion;
import com.negociadororq.application.service.OutboxService;
import com.negociadororq.application.service.SeguimientoLiquidacionService;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.event.LiquidacionSolicitadaEvent;
import com.negociadororq.domain.model.ReferenciaFactura;
import com.negociadororq.domain.model.TipoOperacionPendiente;
import com.negociadororq.infrastructure.persistence.adapter.FacturaAdapter;
import com.negociadororq.infrastructure.persistence.adapter.FacturaPatchAdapter;
import com.negociadororq.infrastructure.persistence.adapter.OutboxAdapter;
import com.negociadororq.infrastructure.persistence.adapter.TransaccionMongoAdapter;
import com.negociadororq.infrastructure.persistence.entity.FacturaDocument;
import com.negociadororq.infrastructure.persistence.repository.FacturaPatchRepository;
import com.negociadororq.infrastructure.persistence.repository.FacturaRepository;
import com.negociadororq.infrastructure.persistence.repository.OutboxEventRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.testcontainers.containers.MongoDBContainer;
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
                + "classpath:db/migration/V3__operacion_pendiente.sql,"
                + "classpath:db/migration/V4__outbox_negociador.sql"
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

    /** Fase 28d (A5): Mongo 7 replica set real para la factura, el outbox y la transacción de la firma final. */
    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0")
            .withCreateContainerCmdModifier(c -> c.withName("integrity-e2e-tc-mongo-"
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
        for (String tabla : List.of("outbox_negociador", "operacion_pendiente", "indirect_funder_offer_supplier_quota",
                "user_quota",
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

        // Fase 28d (A2): la plantilla A va al outbox JDBC (outbox_negociador) en la MISMA transacción de la reversión,
        // con id determinista; este puerto de prueba inserta en la tabla real igual que el adaptador de commons.
        com.negociadororq.application.port.output.CorreoTransaccionalOutputPort correos = (id, evento) -> {
            try {
                jdbc.update("INSERT INTO outbox_negociador (event_id, tipo, clave_kafka, topic, payload, estado, intentos,"
                        + " proximo_intento, fecha_creacion) VALUES (?, 'negociador.notificacion-email', ?,"
                        + " 'notificacion.email', ?, 'PENDIENTE', 0, NOW(3), NOW(3))", id,
                        "negociacion:" + evento.getIdNegociacion(), evento.getTipoNotificacion());
                return true;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                return false;
            }
        };
        com.negociadororq.application.service.CorreosReversionService correosReversion =
                new com.negociadororq.application.service.CorreosReversionService(correos, companyPort, negociacionPort,
                        clock);
        com.negociadororq.application.service.AuditoriaNegociacionService auditoria =
                mock(com.negociadororq.application.service.AuditoriaNegociacionService.class);
        exacta = new ReversionExactaService(operaciones, operador, indirectoPort, tx, auditoria, correosReversion);
        procesar = new ProcesarFacturaNegociacionServiceImpl(facturaPort, operador, userPort, companyPort,
                empresaQuotaPort, fondeadorQuotaPort, facturaPatchPort, validacion, visibilidad, ofertas, indirectoPort,
                clock, operaciones, auditoria);
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
        // Fase 28d (decisión de John): la plantilla A solo si ningún otro correo informa la reversión (no en el rechazo
        // ni en la fallida, que tienen el suyo).
        long esperadoA = 1;
        if (camino == Camino.RECHAZO || camino == Camino.LIQUIDACION_FALLIDA) {
            esperadoA = 0;
        }
        assertThat(correosA(e)).isEqualTo(esperadoA);

        // reversión repetida, por el mismo camino y por otro: una sola vez (cupos y plantilla A)
        revertir(camino, e);
        revertir(Camino.LIQUIDACION_FALLIDA, e);
        assertIguales(inicial, cupos());
        assertThat(correosA(e)).isEqualTo(esperadoA);
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
        // los dos caminos de la carrera (fallida y rechazo) tienen su propio correo: ninguno lleva la plantilla A
        assertThat(correosA(e)).isZero();
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
        assertThat(estado(e)).isEqualTo(ReversionExactaService.CUPOS_DESCONTADOS);
        assertThat(correosA(e)).as("sin reversión no hay plantilla A").isZero();

        exacta.revertir(com.negociadororq.domain.model.TipoOperacionPendiente.RECHAZO, e.negociacionId, e.cufe,
                "rechazo");
        assertIguales(inicial, cupos());
        assertThat(correosA(e)).as("el rechazo avisa con su propio correo, sin plantilla A").isZero();
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
                    null, clock, "otp.requested", "liquidacion", null, null, null)
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
                // Fase 28d (A5): fila 1 de la tabla (negociación en curso, firmas pendientes): vence y revierte.
                e.factura.setFechavencimientoFactura(LocalDate.now().plusDays(3));
                e.factura.setId("id-" + e.cufe);
                e.factura.setEstadoFirma(e.modo == Modo.CONFIRMING ? "SIN_DESCUENTO" : "SIN_CESION");
                when(facturaPort.findCandidatasVencimiento(any(), any())).thenReturn(List.of(e.factura));
                when(facturaPort.findVencidasConReversionPendiente()).thenReturn(List.of());
                when(facturaPatchPort.marcarVencidaSiCoincide(any(), any(), any(), anyBoolean())).thenReturn(true);
                new FacturaVencimientoService(facturaPort, facturaPatchPort, operaciones, exacta, clock)
                        .actualizarFacturasVencidas();
                e.factura.setEstadoNegociacion("NEGOCIADA");
            }
            default -> throw new IllegalStateException();
        }
    }

    // ─── Fase 28d (A5): vencimiento nocturno contra la firma final, con Mongo y MySQL reales ─────────────────────

    /** Las piezas Mongo reales de A5 (factura, patch con compare-and-set, outbox y transacción). */
    private final class Mongo implements AutoCloseable {
        final MongoClient cliente = MongoClients.create(MONGO.getReplicaSetUrl());
        final SimpleMongoClientDatabaseFactory fabrica = new SimpleMongoClientDatabaseFactory(cliente, "negociador_a5");
        final MongoTemplate plantilla = new MongoTemplate(fabrica);
        final MongoRepositoryFactory repos = new MongoRepositoryFactory(plantilla);
        final OutboxEventRepository outboxRepo = repos.getRepository(OutboxEventRepository.class);
        final OutboxAdapter outbox = new OutboxAdapter(outboxRepo, new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        final FacturaAdapter facturas = new FacturaAdapter(repos.getRepository(FacturaRepository.class));
        final FacturaPatchAdapter patch = new FacturaPatchAdapter(new FacturaPatchRepository(plantilla,
                new EstadoFacturaMapper()));
        final FirmaFinalLiquidacion firma = new FirmaFinalLiquidacion(new TransaccionMongoAdapter(fabrica), patch,
                new OutboxService(outbox));
        final SeguimientoLiquidacionService seguimiento = new SeguimientoLiquidacionService(operaciones, outbox,
                facturas, patch, null, null, exacta, clock,
                new SeguimientoLiquidacionService.Parametros(java.time.Duration.ofMinutes(15), 4, 8, 50));

        Mongo() {
            plantilla.getDb().drop();
            // la colección debe existir antes de escribir en una transacción
            plantilla.createCollection(FacturaDocument.class);
            plantilla.createCollection("outbox_event");
        }

        FacturaVencimientoService job(ReversionExactaService reversion) {
            return new FacturaVencimientoService(facturas, patch, operaciones, reversion, clock);
        }

        /** La factura del escenario en Mongo, antes de la liquidación, a {@code dias} de vencer. */
        Factura guardar(Escenario e, String estado, String firma, int dias) {
            Factura f = e.factura;
            f.setId(null);
            f.setEstadoNegociacion(estado);
            f.setEstadoFirma(firma);
            f.setFechavencimientoFactura(LocalDate.now().plusDays(dias));
            return facturas.save(f);
        }

        FacturaDocument doc(String cufe) {
            return existente(plantilla.findOne(Query.query(Criteria.where("cufe").is(cufe)), FacturaDocument.class));
        }

        @Override
        public void close() {
            cliente.close();
        }
    }

    private static FacturaDocument existente(FacturaDocument d) {
        if (d == null) {
            throw new AssertionError("la factura no está en Mongo");
        }
        return d;
    }

    private static String idEvento(Escenario e) {
        return "liquidacion.solicitada|" + e.negociacionId + "|" + e.cufe;
    }

    private static EstadoFirma firmaFinal(Modo modo) {
        return modo == Modo.CONFIRMING ? EstadoFirma.DESCUENTO : EstadoFirma.CESION;
    }

    private long contar(String sql, Object... args) {
        return java.util.Objects.requireNonNullElse(jdbc.queryForObject(sql, Long.class, args), 0L);
    }

    private long plantillaA(Escenario e) {
        return contar("SELECT COUNT(*) FROM outbox_negociador WHERE clave_kafka = ? AND payload = ?",
                "negociacion:" + e.negociacionId, "OPERACION_REVERTIDA");
    }

    /** La firma final como en la aprobación: transacción Mongo y, si ganó, el seguimiento en MySQL. */
    private boolean firmarComoLaAprobacion(Mongo m, Escenario e) {
        ReferenciaFactura ref = new ReferenciaFactura(e.cufe, e.factura.getRutProveedor(), "PAG");
        LiquidacionSolicitadaEvent evento = new LiquidacionSolicitadaEvent(e.negociacionId, "op-1", e.numero,
                new Operacion(e.modo == Modo.CONFIRMING ? "CONFIRMING" : "FACTORING", "TRADICIONAL"), LocalDateTime.now(),
                true, "req-" + e.numero, e.cufe, ref.rutProveedor(), ref.rutPagador());
        if (!m.firma.firmarYSolicitar(ref, firmaFinal(e.modo), idEvento(e), e.negociacionId, "liquidacion.solicitada",
                evento)) {
            return false;
        }
        m.seguimiento.registrarSolicitud(e.negociacionId, e.cufe, e.numero, idEvento(e));
        m.patch.quitarSeguimientoLiquidacionPendiente(ref);
        return true;
    }

    @ParameterizedTest(name = "A5, carrera repetida firma final contra job en {0}: gana exactamente uno y nunca se "
            + "devuelven cupos de una factura que luego se liquida")
    @MethodSource("modos")
    void carrera_firma_final_contra_job_gana_exactamente_uno(Modo modo) throws Exception {
        int vueltas = 40;
        int ganoFirma = 0;
        int ganoJob = 0;
        // cupos de partida para 40 negociaciones (con centavos distintos en cada uno)
        jdbc.update("UPDATE user_quota SET quota = 99000000.00, current_quota = 90000000.11");
        jdbc.update("UPDATE parametrization SET quota = 99000000.00, current_quota = 90000000.22");
        jdbc.update("UPDATE funder SET supplier_quota = 99000000.00, supplier_current_quota = 90000000.33"
                + " WHERE supplier_id IS NOT NULL");
        jdbc.update("UPDATE funder SET payer_quota = 99000000.00, payer_current_quota = 90000000.44"
                + " WHERE payer_id IS NOT NULL");
        jdbc.update("UPDATE indirect_funder_offer_supplier_quota SET quota = 99000000.00, current_quota = 90000000.55");
        try (Mongo m = new Mongo()) {
            for (int i = 0; i < vueltas; i++) {
                Map<String, BigDecimal> inicial = cupos();
                Escenario e = crear(modo);
                Map<String, BigDecimal> trasCrear = cupos();
                m.guardar(e, "NEGOCIADA", "EN_APROBACION", 3);

                CountDownLatch listos = new CountDownLatch(2);
                CountDownLatch largada = new CountDownLatch(1);
                List<Throwable> fallos = Collections.synchronizedList(new ArrayList<>());
                boolean[] firmo = new boolean[1];
                int esperaFirma = ThreadLocalRandom.current().nextInt(0, 15);
                int esperaJob = ThreadLocalRandom.current().nextInt(0, 15);
                Thread a = hilo(() -> {
                    dormir(esperaFirma);
                    firmo[0] = firmarComoLaAprobacion(m, e);
                }, listos, largada, fallos);
                Thread b = hilo(() -> {
                    dormir(esperaJob);
                    m.job(exacta).actualizarFacturasVencidas();
                }, listos, largada, fallos);
                listos.await();
                largada.countDown();
                a.join();
                b.join();
                assertThat(fallos).isEmpty();

                FacturaDocument d = m.doc(e.cufe);
                boolean firmaGano = "NEGOCIADA".equals(d.getEstadoNegociacion())
                        && firmaFinal(modo).name().equals(d.getEstadoFirma());
                boolean jobGano = "VENCIDA".equals(d.getEstadoNegociacion());
                assertThat(firmaGano ^ jobGano).as("exactamente un ganador, vuelta %d: %s/%s", i,
                        d.getEstadoNegociacion(), d.getEstadoFirma()).isTrue();
                assertThat(firmo[0]).isEqualTo(firmaGano);
                boolean evento = m.outboxRepo.existsById(idEvento(e));
                boolean seguimiento = operaciones.buscar(TipoOperacionPendiente.LIQUIDACION_PENDIENTE,
                        OperacionPendiente.clave(e.negociacionId, e.cufe)).isPresent();
                if (firmaGano) {
                    ganoFirma++;
                    assertThat(evento).isTrue();
                    assertThat(seguimiento).isTrue();
                    assertThat(estado(e)).isNotEqualTo(ReversionExactaService.REVERTIDA);
                    assertIguales(trasCrear, cupos());
                    // la factura se liquida y el job vuelve a correr: no se devuelve nada
                    m.plantilla.updateFirst(Query.query(Criteria.where("cufe").is(e.cufe)),
                            new Update().set("estadoNegociacion", "LIQUIDADA"), FacturaDocument.class);
                    m.job(exacta).actualizarFacturasVencidas();
                    assertIguales(trasCrear, cupos());
                    assertThat(plantillaA(e)).isZero();
                } else {
                    ganoJob++;
                    assertThat(evento).isFalse();
                    assertThat(seguimiento).isFalse();
                    assertThat(estado(e)).isEqualTo(ReversionExactaService.REVERTIDA);
                    assertThat(d.getVencimientoReversionPendiente()).isNull();
                    assertIguales(inicial, cupos());
                    assertThat(plantillaA(e)).isEqualTo(1);
                    // la firma final llega tarde: no revive la factura ni pide la liquidación
                    assertThat(firmarComoLaAprobacion(m, e)).isFalse();
                    assertThat(m.outboxRepo.existsById(idEvento(e))).isFalse();
                }
            }
        }
        System.out.printf("[A5-CARRERA] %s: %d vueltas, ganó la firma %d, ganó el job %d%n", modo, vueltas, ganoFirma,
                ganoJob);
        assertThat(ganoFirma + ganoJob).isEqualTo(vueltas);
    }

    static Stream<Modo> modos() {
        return Stream.of(Modo.values());
    }

    private static void dormir(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @ParameterizedTest(name = "A5, caída a mitad de la corrida en {0}: la siguiente retoma la reversión, una sola vez")
    @MethodSource("modos")
    void caida_a_mitad_de_la_corrida_se_retoma_una_sola_vez(Modo modo) {
        try (Mongo m = new Mongo()) {
            Map<String, BigDecimal> inicial = cupos();
            Escenario e = crear(modo);
            Map<String, BigDecimal> trasCrear = cupos();
            m.guardar(e, "ENDOSADA", "EN_REVISION", 2);

            ReversionExactaService seCae = spy(exacta);
            doThrow(new IllegalStateException("caída a mitad de la corrida")).doCallRealMethod().when(seCae)
                    .revertir(eq(TipoOperacionPendiente.VENCIMIENTO), any(), any(), any());

            FacturaVencimientoService.Resumen r1 = m.job(seCae).actualizarFacturasVencidas();
            assertThat(r1.vencidas()).isEqualTo(1);
            assertThat(r1.fallidas()).isEqualTo(1);
            assertThat(m.doc(e.cufe).getEstadoNegociacion()).isEqualTo("VENCIDA");
            assertThat(m.doc(e.cufe).getVencimientoReversionPendiente()).isTrue();
            assertIguales(trasCrear, cupos());
            assertThat(plantillaA(e)).isZero();

            FacturaVencimientoService.Resumen r2 = m.job(seCae).actualizarFacturasVencidas();
            assertThat(r2.revertidas()).isEqualTo(1);
            assertThat(m.doc(e.cufe).getVencimientoReversionPendiente()).isNull();
            assertIguales(inicial, cupos());
            assertThat(estado(e)).isEqualTo(ReversionExactaService.REVERTIDA);
            assertThat(plantillaA(e)).isEqualTo(1);

            FacturaVencimientoService.Resumen r3 = m.job(exacta).actualizarFacturasVencidas();
            assertThat(r3.vencidas() + r3.revertidas()).isZero();
            assertIguales(inicial, cupos());
            assertThat(plantillaA(e)).isEqualTo(1);
        }
    }

    @Test
    void a5_filas_que_no_se_tocan_y_fila_5_con_mongo_real() {
        try (Mongo m = new Mongo()) {
            Map<String, BigDecimal> inicial = cupos();
            String[][] filas = {
                    {"CARGADA", "SIN_FIRMA", "VENCIDA"},
                    {"NEGOCIADA", "CESION", "NEGOCIADA"},
                    {"NEGOCIADA", "DESCUENTO", "NEGOCIADA"},
                    {"NEGOCIADA", "RECHAZADA", "NEGOCIADA"},
                    {"LIQUIDADA", "CESION", "LIQUIDADA"},
                    {"RECHAZADA", "RECHAZADA", "RECHAZADA"},
                    {"BLOQUEADA", "SIN_CESION", "BLOQUEADA"},
                    {"VENDIDA", "SIN_CESION", "VENDIDA"},
                    {"INEXISTENTE", "SIN_CESION", "INEXISTENTE"},
            };
            for (int i = 0; i < filas.length; i++) {
                Factura f = new Factura();
                f.setNumeroFactura("FILA-" + i);
                f.setCufe("CUFE-FILA-" + i + "-" + System.nanoTime());
                f.setRutProveedor("PROV");
                f.setRutPagador("PAG");
                f.setNegociacionId(i == 0 ? null : "neg-fila-" + i);
                f.setEstadoNegociacion(filas[i][0]);
                f.setEstadoFirma(filas[i][1]);
                f.setFechavencimientoFactura(LocalDate.now().plusDays(1));
                m.facturas.save(f);
            }
            m.job(exacta).actualizarFacturasVencidas();
            for (int i = 0; i < filas.length; i++) {
                FacturaDocument d = existente(m.plantilla.findOne(
                        Query.query(Criteria.where("numeroFactura").is("FILA-" + i)), FacturaDocument.class));
                assertThat(d.getEstadoNegociacion()).as("fila %s/%s", filas[i][0], filas[i][1]).isEqualTo(filas[i][2]);
                assertThat(d.getEstadoFirma()).isEqualTo(filas[i][1]);
            }
            assertIguales(inicial, cupos());
            assertThat(contar("SELECT COUNT(*) FROM operacion_pendiente WHERE tipo = 'VENCIMIENTO'")).isZero();
        }
    }

    @Test
    void a5_la_firma_final_no_revive_una_factura_vencida_y_el_reintento_de_la_misma_firma_no_falla() {
        try (Mongo m = new Mongo()) {
            Escenario e = crear(Modo.FACTORING_DIRECTO);
            m.guardar(e, "NEGOCIADA", "EN_APROBACION", 30);
            assertThat(firmarComoLaAprobacion(m, e)).isTrue();
            // reintento de la misma aprobación (caída antes de marcar el efecto): la firma ya es la final, no falla
            assertThat(firmarComoLaAprobacion(m, e)).isTrue();
            assertThat(m.outboxRepo.count()).isEqualTo(1);
            assertThat(m.doc(e.cufe).getSolicitudLiquidacionId()).isEqualTo(idEvento(e));
            assertThat(m.doc(e.cufe).getSeguimientoLiquidacionPendiente()).isNull();

            Escenario v = crear(Modo.CONFIRMING);
            m.guardar(v, "VENCIDA", "SIN_DESCUENTO", 30);
            assertThat(firmarComoLaAprobacion(m, v)).isFalse();
            assertThat(m.doc(v.cufe).getEstadoNegociacion()).isEqualTo("VENCIDA");
            assertThat(m.outboxRepo.existsById(idEvento(v))).isFalse();
        }
    }

    @Test
    void a5_reconciliador_completa_el_seguimiento_de_una_firma_final_sin_seguimiento() {
        try (Mongo m = new Mongo()) {
            Escenario e = crear(Modo.FACTORING_INDIRECTO);
            m.guardar(e, "NEGOCIADA", "EN_APROBACION", 2);
            ReferenciaFactura ref = new ReferenciaFactura(e.cufe, e.factura.getRutProveedor(), "PAG");
            // la instancia cae justo después de la transacción Mongo: firma y evento escritos, sin seguimiento
            assertThat(m.firma.firmarYSolicitar(ref, EstadoFirma.CESION, idEvento(e), e.negociacionId,
                    "liquidacion.solicitada", Map.of("negociacionId", e.negociacionId))).isTrue();
            assertThat(operaciones.buscar(TipoOperacionPendiente.LIQUIDACION_PENDIENTE,
                    OperacionPendiente.clave(e.negociacionId, e.cufe))).isEmpty();
            assertThat(m.doc(e.cufe).getSeguimientoLiquidacionPendiente()).isTrue();
            // ni el job la toca (vence en 2 días): la firma es la final
            m.job(exacta).actualizarFacturasVencidas();
            assertThat(m.doc(e.cufe).getEstadoNegociacion()).isEqualTo("NEGOCIADA");

            assertThat(m.seguimiento.reemitirSinSeguimiento()).isEqualTo(1);
            assertThat(operaciones.buscar(TipoOperacionPendiente.LIQUIDACION_PENDIENTE,
                    OperacionPendiente.clave(e.negociacionId, e.cufe))).isPresent();
            assertThat(m.doc(e.cufe).getSeguimientoLiquidacionPendiente()).isNull();
            assertThat(m.seguimiento.reemitirSinSeguimiento()).isZero();
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

    private long correosA(Escenario e) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_negociador WHERE clave_kafka = ?"
                + " AND payload = 'OPERACION_REVERTIDA'", Long.class, "negociacion:" + e.negociacionId);
        return n == null ? 0L : n;
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
