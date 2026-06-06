package com.negociadororq.application.service;

import com.negociadororq.application.dto.AprobacionFacturaRequestDto;
import com.negociadororq.application.dto.AprobacionRequestDto;
import com.negociadororq.application.dto.AprobacionResponseDto;
import com.negociadororq.application.dto.ConfirmacionOtpRequestDto;
import com.negociadororq.application.exception.ConflictoException;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.exception.ReglaDeNegocioException;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.EndosoOutputPort;
import com.negociadororq.application.port.output.EndosoPatchOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.port.output.PendingOtpOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.enums.RolActor;
import com.negociadororq.domain.model.AprobacionActorDto;
import com.negociadororq.domain.model.AprobacionFacturaRequest;
import com.negociadororq.domain.model.AprobacionRequest;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.Operacion;
import com.negociadororq.domain.model.PendingOtp;
import com.negociadororq.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AprobacionEndosoDescuentoServiceImpl — aprobación/endoso de facturas")
class AprobacionEndosoDescuentoServiceImplTest {

    @Mock private NegociacionOutputPort negociacionPort;
    @Mock private FacturaPatchOutputPort facturaPatchPort;
    @Mock private EndosoPatchOutputPort endosoPatchPort;
    @Mock private OutboxService outboxService;
    @Mock private CupoReversionService cupoReversionService;
    @Mock private CompanyOutputPort companyPort;
    @Mock private FondeadorQuotaOutputPort fondeadorQuotaPort;
    @Mock private FacturaOutputPort facturaPort;
    @Mock private UserOutputPort userPort;
    @Mock private PendingOtpOutputPort pendingOtpPort;
    @Mock private FirmaEndosoService firmaEndosoService;
    @Mock private NotificacionService notificacionService;
    @Mock private EndosoOutputPort endosoPort;

    @InjectMocks
    private AprobacionEndosoDescuentoServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "topicRequestOtp", "topics.otp-request");
        ReflectionTestUtils.setField(service, "topicValidateOtp", "topics.otp-validate");
        ReflectionTestUtils.setField(service, "topicLiquidacionSolicitada", "topics.liquidacion");
    }

    // ─── solicitarOtp ─────────────────────────────────────────────

    @Test
    @DisplayName("solicitarOtp: facturas null → mapea lista vacía y guarda pending")
    void solicitarOtp_facturasNull_listaVacia() {
        AprobacionRequestDto dto = new AprobacionRequestDto(
                "NEG-001", "OP-RUT", "800EMP", "PAGADOR", null, null);

        service.solicitarOtp(dto);

        verify(pendingOtpPort).save(any());
        verify(outboxService).guardarEvento(eq("NEG-001"), eq("topics.otp-request"), any());
    }

    @Test
    @DisplayName("solicitarOtp: facturas no null → mapea facturas y guarda pending")
    void solicitarOtp_facturasPresentes_mapea() {
        AprobacionFacturaRequestDto fDto = new AprobacionFacturaRequestDto("FAC-001", true, "ok");
        AprobacionRequestDto dto = new AprobacionRequestDto(
                "NEG-001", "OP-RUT", "800EMP", "PAGADOR", List.of(fDto), "obs");

        service.solicitarOtp(dto);

        verify(pendingOtpPort).save(any());
        verify(outboxService).guardarEvento(eq("NEG-001"), eq("topics.otp-request"), any());
    }

    // ─── confirmarOtp ─────────────────────────────────────────────

    @Test
    @DisplayName("confirmarOtp: publica evento OTP validate")
    void confirmarOtp_publicaEvento() {
        ConfirmacionOtpRequestDto dto = ConfirmacionOtpRequestDto.builder()
                .idNegociacion("NEG-001").rutEmpresa("800EMP").otp("123456").build();

        service.confirmarOtp(dto);

        verify(outboxService).guardarEvento(eq("NEG-001"), eq("topics.otp-validate"), any());
    }

    // ─── aprobarInterno: guarda de negociación ─────────────────────

    @Test
    @DisplayName("aprobarInterno: negociacion no encontrada → lanza RecursoNoEncontradoException")
    void aprobarInterno_negociacionNoEncontrada_lanzaExcepcion() {
        when(negociacionPort.findById("NEG-XXX")).thenReturn(Optional.empty());
        PendingOtp pending = buildPending("NEG-XXX", "900PAG", List.of());

        assertThatThrownBy(() -> service.aprobarInterno(pending))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("aprobarInterno: lista de facturas vacía → termina sin errores")
    void aprobarInterno_listaVacia_sinErrores() {
        Negociacion neg = buildNegociacion("FACTORING", List.of());
        when(negociacionPort.findById("NEG-001")).thenReturn(Optional.of(neg));
        when(negociacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AprobacionResponseDto result = service.aprobarInterno(buildPending("NEG-001", "900PAG", List.of()));

        assertThat(result.getErrores()).isEmpty();
        verify(firmaEndosoService, never()).firmarPdfEndoso(any(), any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("aprobarInterno: factura no encontrada en negociación → agrega error y continúa")
    void aprobarInterno_facturaNoEncontrada_agregaError() {
        Negociacion neg = buildNegociacion("FACTORING", List.of(
                buildFacturaNegociada("FAC-001", "900PAG", "800PROV", null)));
        when(negociacionPort.findById("NEG-001")).thenReturn(Optional.of(neg));
        when(negociacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PendingOtp pending = buildPending("NEG-001", "900PAG",
                List.of(new AprobacionFacturaRequest("FAC-999", true, "")));

        AprobacionResponseDto result = service.aprobarInterno(pending);

        assertThat(result.getErrores()).anyMatch(e -> e.contains("FAC-999"));
    }

    @Test
    @DisplayName("aprobarInterno: yaEndoso duplicado → lanza ConflictoException")
    void aprobarInterno_yaEndoso_lanzaConflicto() {
        AprobacionActorDto prevAprobacion = new AprobacionActorDto(
                "900PAG", RolActor.PAGADOR, true, "", LocalDateTime.now());
        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV", null);
        factura.setAprobaciones(new ArrayList<>(List.of(prevAprobacion)));

        Negociacion neg = buildNegociacion("FACTORING", List.of(factura));
        when(negociacionPort.findById("NEG-001")).thenReturn(Optional.of(neg));

        PendingOtp pending = buildPending("NEG-001", "900PAG",
                List.of(new AprobacionFacturaRequest("FAC-001", true, "")));

        assertThatThrownBy(() -> service.aprobarInterno(pending))
                .isInstanceOf(ConflictoException.class);
    }

    // ─── aprobarInterno: paths de resolverRolActor ─────────────────

    @Test
    @DisplayName("aprobarInterno: resolverRolActor lanza excepción cuando actor no es reconocido")
    void aprobarInterno_rolActorDesconocido_lanzaReglaDeNegocio() {
        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV",
                List.of(FondeadorAsignacion.builder().rutFondeador("700FOND").build()));
        Negociacion neg = buildNegociacion("FACTORING", List.of(factura));
        when(negociacionPort.findById("NEG-001")).thenReturn(Optional.of(neg));

        PendingOtp pending = buildPending("NEG-001", "999UNKNOWN",
                List.of(new AprobacionFacturaRequest("FAC-001", true, "")));

        assertThatThrownBy(() -> service.aprobarInterno(pending))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    @DisplayName("aprobarInterno: resolverRolActor lanza excepción cuando fondeadores son null")
    void aprobarInterno_fondeadoresNull_rolActorDesconocido_lanza() {
        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV", null);
        Negociacion neg = buildNegociacion("FACTORING", List.of(factura));
        when(negociacionPort.findById("NEG-001")).thenReturn(Optional.of(neg));

        PendingOtp pending = buildPending("NEG-001", "999UNKNOWN",
                List.of(new AprobacionFacturaRequest("FAC-001", true, "")));

        assertThatThrownBy(() -> service.aprobarInterno(pending))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    @DisplayName("aprobarInterno: actor es fondeador → rol FONDEADOR asignado")
    void aprobarInterno_actorEsFondeador_rolFondeador() {
        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV",
                List.of(FondeadorAsignacion.builder().rutFondeador("700FOND").build()));
        Negociacion neg = buildNegociacion("FACTORING", List.of(factura));
        when(negociacionPort.findById("NEG-001")).thenReturn(Optional.of(neg));
        mockPatchPorts();
        when(negociacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PendingOtp pending = buildPending("NEG-001", "700FOND",
                List.of(new AprobacionFacturaRequest("FAC-001", true, "")));

        AprobacionResponseDto result = service.aprobarInterno(pending);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("aprobarInterno: actor es proveedor → rol PROVEEDOR asignado (else branch EN_REVISION)")
    void aprobarInterno_actorEsProveedor_rolProveedor() {
        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV", null);
        Negociacion neg = buildNegociacion("FACTORING", List.of(factura));
        when(negociacionPort.findById("NEG-001")).thenReturn(Optional.of(neg));
        mockPatchPorts();
        when(negociacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PendingOtp pending = buildPending("NEG-001", "800PROV",
                List.of(new AprobacionFacturaRequest("FAC-001", true, "")));

        AprobacionResponseDto result = service.aprobarInterno(pending);

        verify(endosoPatchPort).actualizarEstadoEndosoPorNoFactura(anyString(), eq("FAC-001"), eq(EstadoEndoso.EN_REVISION));
        assertThat(result).isNotNull();
    }

    // ─── aprobarInterno: actualizarEstadosFacturaEndosoNegociacion ──

    @Test
    @DisplayName("aprobarInterno: aprobaciones null inicialmente → inicializa lista vacía")
    void aprobarInterno_aprobacionesNull_inicializaLista() {
        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV", null);
        // aprobaciones es null por defecto del builder
        Negociacion neg = buildNegociacion("CONFIRMING", List.of(factura));
        when(negociacionPort.findById("NEG-001")).thenReturn(Optional.of(neg));
        mockPatchPorts();
        when(negociacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PendingOtp pending = buildPending("NEG-001", "800PROV",
                List.of(new AprobacionFacturaRequest("FAC-001", true, "")));

        service.aprobarInterno(pending);

        verify(endosoPatchPort).actualizarEstadoEndosoPorNoFactura(anyString(), eq("FAC-001"), eq(EstadoEndoso.ACEPTADO));
    }

    @Test
    @DisplayName("aprobarInterno: hayRechazo=true → revierte cupos y marca RECHAZADA")
    void aprobarInterno_hayRechazo_revierteCuposYMarcaRechazada() {
        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV", null);
        Negociacion neg = buildNegociacion("FACTORING", List.of(factura));
        when(negociacionPort.findById("NEG-001")).thenReturn(Optional.of(neg));
        mockPatchPorts();

        Factura facturaDb = buildFactura("FAC-001", new BigDecimal("1000"), "800PROV", "900PAG");
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(facturaDb));
        when(userPort.findByUsername("OP-RUT")).thenReturn(Optional.of(User.builder().ideUser(10L).build()));
        when(companyPort.findByNit("800PROV")).thenReturn(Optional.of(Company.builder().ideCompany(20L).build()));
        when(negociacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PendingOtp pending = buildPending("NEG-001", "900PAG",
                List.of(new AprobacionFacturaRequest("FAC-001", false, "Rechazo")));

        service.aprobarInterno(pending);

        verify(cupoReversionService).revertirCupoUsuario(10L, new BigDecimal("1000"));
        verify(cupoReversionService).revertirCupoEmpresa(20L, new BigDecimal("1000"));
        verify(notificacionService).notificarLiquidacionFallida(any(), anyString());
        verify(facturaPatchPort).actualizarEstadoFirmaPorNoFactura("FAC-001", EstadoFirma.RECHAZADA);
        verify(endosoPatchPort).actualizarEstadoEndosoPorNoFactura(anyString(), eq("FAC-001"), eq(EstadoEndoso.RECHAZADO));
    }

    @Test
    @DisplayName("aprobarInterno: todosAprobaron FACTORING → estadoFirma=CESION, evento liquidación")
    void aprobarInterno_todosAprobanFactoring_estadoCesion() {
        AprobacionActorDto prevFond = new AprobacionActorDto(
                "700FOND", RolActor.FONDEADOR, true, "", LocalDateTime.now());
        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV",
                List.of(FondeadorAsignacion.builder().rutFondeador("700FOND").build()));
        factura.setAprobaciones(new ArrayList<>(List.of(prevFond)));

        Negociacion neg = buildNegociacion("FACTORING", List.of(factura));
        when(negociacionPort.findById("NEG-001")).thenReturn(Optional.of(neg));
        mockPatchPorts();
        when(negociacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PendingOtp pending = buildPending("NEG-001", "900PAG",
                List.of(new AprobacionFacturaRequest("FAC-001", true, "")));

        service.aprobarInterno(pending);

        verify(facturaPatchPort).actualizarEstadoFirmaPorNoFactura("FAC-001", EstadoFirma.CESION);
        verify(endosoPatchPort).actualizarEstadoEndosoPorNoFactura(anyString(), eq("FAC-001"), eq(EstadoEndoso.ACEPTADO));
        verify(outboxService).guardarEvento(anyString(), eq("topics.liquidacion"), any());
    }

    @Test
    @DisplayName("aprobarInterno: todosAprobaron CONFIRMING → estadoFirma=DESCUENTO")
    void aprobarInterno_todosAprobanConfirming_estadoDescuento() {
        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV", null);
        Negociacion neg = buildNegociacion("CONFIRMING", List.of(factura));
        when(negociacionPort.findById("NEG-001")).thenReturn(Optional.of(neg));
        mockPatchPorts();
        when(negociacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PendingOtp pending = buildPending("NEG-001", "800PROV",
                List.of(new AprobacionFacturaRequest("FAC-001", true, "")));

        service.aprobarInterno(pending);

        verify(facturaPatchPort).actualizarEstadoFirmaPorNoFactura("FAC-001", EstadoFirma.DESCUENTO);
        verify(endosoPatchPort).actualizarEstadoEndosoPorNoFactura(anyString(), eq("FAC-001"), eq(EstadoEndoso.ACEPTADO));
    }

    @Test
    @DisplayName("aprobarInterno: alguienAprobo pero no todos → estadoFirma=EN_APROBACION")
    void aprobarInterno_alguienAproboFactoring_estadoEnAprobacion() {
        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV",
                List.of(FondeadorAsignacion.builder().rutFondeador("700FOND").build()));
        Negociacion neg = buildNegociacion("FACTORING", List.of(factura));
        when(negociacionPort.findById("NEG-001")).thenReturn(Optional.of(neg));
        mockPatchPorts();
        when(negociacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PendingOtp pending = buildPending("NEG-001", "900PAG",
                List.of(new AprobacionFacturaRequest("FAC-001", true, "")));

        service.aprobarInterno(pending);

        verify(facturaPatchPort).actualizarEstadoFirmaPorNoFactura("FAC-001", EstadoFirma.EN_APROBACION);
        verify(endosoPatchPort).actualizarEstadoEndosoPorNoFactura(anyString(), eq("FAC-001"), eq(EstadoEndoso.PENDIENTE));
    }

    @Test
    @DisplayName("aprobarInterno: tipo desconocido con actoresRequeridos vacío → todosAprobaron=true, DESCUENTO")
    void aprobarInterno_tipoDesconocido_todosAproban() {
        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV", null);
        Negociacion neg = buildNegociacion("OTRO", List.of(factura));
        when(negociacionPort.findById("NEG-001")).thenReturn(Optional.of(neg));
        mockPatchPorts();
        when(negociacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PendingOtp pending = buildPending("NEG-001", "800PROV",
                List.of(new AprobacionFacturaRequest("FAC-001", true, "")));

        service.aprobarInterno(pending);

        verify(facturaPatchPort).actualizarEstadoFirmaPorNoFactura("FAC-001", EstadoFirma.DESCUENTO);
    }

    // ─── revertirCupos ────────────────────────────────────────────

    @Test
    @DisplayName("revertirCupos: factura no encontrada → lanza RuntimeException")
    void revertirCupos_facturaNoEncontrada_lanza() {
        when(facturaPort.findByNumeroFactura("FAC-X")).thenReturn(Optional.empty());

        FacturaNegociada factura = buildFacturaNegociada("FAC-X", "900PAG", "800PROV", null);

        assertThatThrownBy(() -> service.revertirCupos(
                factura, Operacion.builder().tipo("FACTORING").clase("N").build(), "800PROV", "OP"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("revertirCupos: valorNeto presente → usa valorNeto")
    void revertirCupos_valorNetoPresente_usaValorNeto() {
        Factura facturaDb = buildFactura("FAC-001", new BigDecimal("800"), "800PROV", "900PAG");
        facturaDb.setValorNetoFactura(new BigDecimal("750"));
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(facturaDb));
        when(userPort.findByUsername("OP")).thenReturn(Optional.of(User.builder().ideUser(1L).build()));
        when(companyPort.findByNit("800PROV")).thenReturn(Optional.of(Company.builder().ideCompany(2L).build()));

        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV", null);

        service.revertirCupos(factura, Operacion.builder().tipo("FACTORING").clase("N").build(), "800PROV", "OP");

        verify(cupoReversionService).revertirCupoUsuario(1L, new BigDecimal("750"));
    }

    @Test
    @DisplayName("revertirCupos: valorNeto null y valorFactura null → usa ZERO")
    void revertirCupos_ambosValoresNull_usaCero() {
        Factura facturaDb = new Factura();
        facturaDb.setNumeroFactura("FAC-001");
        facturaDb.setValorNetoFactura(null);
        facturaDb.setValorFactura(null);
        facturaDb.setRutProveedor("800PROV");
        facturaDb.setRutPagador("900PAG");
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(facturaDb));
        when(userPort.findByUsername("OP")).thenReturn(Optional.of(User.builder().ideUser(1L).build()));
        when(companyPort.findByNit("800PROV")).thenReturn(Optional.of(Company.builder().ideCompany(2L).build()));

        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV", null);

        service.revertirCupos(factura, Operacion.builder().tipo("FACTORING").clase("N").build(), "800PROV", "OP");

        verify(cupoReversionService).revertirCupoUsuario(1L, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("revertirCupos: usuario no encontrado → lanza RuntimeException")
    void revertirCupos_usuarioNoEncontrado_lanza() {
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(
                Optional.of(buildFactura("FAC-001", new BigDecimal("1000"), "800PROV", "900PAG")));
        when(userPort.findByUsername("OP")).thenReturn(Optional.empty());

        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV", null);

        assertThatThrownBy(() -> service.revertirCupos(
                factura, Operacion.builder().tipo("FACTORING").clase("N").build(), "800PROV", "OP"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    @DisplayName("revertirCupos: empresa no encontrada → lanza RuntimeException")
    void revertirCupos_empresaNoEncontrada_lanza() {
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(
                Optional.of(buildFactura("FAC-001", new BigDecimal("1000"), "800PROV", "900PAG")));
        when(userPort.findByUsername("OP")).thenReturn(Optional.of(User.builder().ideUser(1L).build()));
        when(companyPort.findByNit("800EMP")).thenReturn(Optional.empty());

        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV", null);

        assertThatThrownBy(() -> service.revertirCupos(
                factura, Operacion.builder().tipo("FACTORING").clase("N").build(), "800EMP", "OP"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Empresa no encontrada");
    }

    @Test
    @DisplayName("revertirCupos: fondeadores null → omite reversión de fondeadores")
    void revertirCupos_fondeadoresNull_omiteFondeadores() {
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(
                Optional.of(buildFactura("FAC-001", new BigDecimal("1000"), "800PROV", "900PAG")));
        when(userPort.findByUsername("OP")).thenReturn(Optional.of(User.builder().ideUser(1L).build()));
        when(companyPort.findByNit("800PROV")).thenReturn(Optional.of(Company.builder().ideCompany(2L).build()));

        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV", null);

        service.revertirCupos(factura, Operacion.builder().tipo("FACTORING").clase("N").build(), "800PROV", "OP");

        verify(cupoReversionService, never()).revertirCupoFondeadorFactoring(any(), any(), any());
        verify(cupoReversionService, never()).revertirCupoFondeadorConfirming(any(), any(), any());
    }

    @Test
    @DisplayName("revertirCupos: fondeadores vacíos → omite reversión de fondeadores")
    void revertirCupos_fondeadoresVacios_omiteFondeadores() {
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(
                Optional.of(buildFactura("FAC-001", new BigDecimal("1000"), "800PROV", "900PAG")));
        when(userPort.findByUsername("OP")).thenReturn(Optional.of(User.builder().ideUser(1L).build()));
        when(companyPort.findByNit("800PROV")).thenReturn(Optional.of(Company.builder().ideCompany(2L).build()));

        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV", List.of());

        service.revertirCupos(factura, Operacion.builder().tipo("FACTORING").clase("N").build(), "800PROV", "OP");

        verify(cupoReversionService, never()).revertirCupoFondeadorFactoring(any(), any(), any());
    }

    @Test
    @DisplayName("revertirCupos: FACTORING con fondeadores → revierte cupo fondeador factoring")
    void revertirCupos_factoring_conFondeadores_revierte() {
        Factura facturaDb = buildFactura("FAC-001", new BigDecimal("1000"), "800PROV", "900PAG");
        facturaDb.setValorNetoFactura(new BigDecimal("1000"));
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(facturaDb));
        when(userPort.findByUsername("OP")).thenReturn(Optional.of(User.builder().ideUser(1L).build()));
        when(companyPort.findByNit("800EMP")).thenReturn(Optional.of(Company.builder().ideCompany(2L).build()));
        when(companyPort.findByNit("700FOND")).thenReturn(Optional.of(Company.builder().ideCompany(30L).build()));
        when(companyPort.findByNit("800PROV")).thenReturn(Optional.of(Company.builder().ideCompany(40L).build()));

        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV",
                List.of(FondeadorAsignacion.builder().rutFondeador("700FOND").build()));

        service.revertirCupos(factura, Operacion.builder().tipo("FACTORING").clase("N").build(), "800EMP", "OP");

        verify(cupoReversionService).revertirCupoFondeadorFactoring(eq(30L), eq(40L), any());
        verify(cupoReversionService, never()).revertirCupoFondeadorConfirming(any(), any(), any());
    }

    @Test
    @DisplayName("revertirCupos: CONFIRMING con fondeadores → revierte cupo fondeador confirming")
    void revertirCupos_confirming_conFondeadores_revierte() {
        Factura facturaDb = buildFactura("FAC-001", new BigDecimal("1000"), "800PROV", "900PAG");
        facturaDb.setValorNetoFactura(new BigDecimal("1000"));
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(facturaDb));
        when(userPort.findByUsername("OP")).thenReturn(Optional.of(User.builder().ideUser(1L).build()));
        when(companyPort.findByNit("800EMP")).thenReturn(Optional.of(Company.builder().ideCompany(2L).build()));
        when(companyPort.findByNit("700FOND")).thenReturn(Optional.of(Company.builder().ideCompany(30L).build()));
        when(companyPort.findByNit("900PAG")).thenReturn(Optional.of(Company.builder().ideCompany(50L).build()));

        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV",
                List.of(FondeadorAsignacion.builder().rutFondeador("700FOND").build()));

        service.revertirCupos(factura, Operacion.builder().tipo("CONFIRMING").clase("N").build(), "800EMP", "OP");

        verify(cupoReversionService).revertirCupoFondeadorConfirming(eq(30L), eq(50L), any());
        verify(cupoReversionService, never()).revertirCupoFondeadorFactoring(any(), any(), any());
    }

    @Test
    @DisplayName("revertirCupos: fondeador no encontrado → lanza RuntimeException")
    void revertirCupos_fondeadorNoEncontrado_lanza() {
        Factura facturaDb = buildFactura("FAC-001", new BigDecimal("1000"), "800PROV", "900PAG");
        facturaDb.setValorNetoFactura(new BigDecimal("1000"));
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(facturaDb));
        when(userPort.findByUsername("OP")).thenReturn(Optional.of(User.builder().ideUser(1L).build()));
        when(companyPort.findByNit("800EMP")).thenReturn(Optional.of(Company.builder().ideCompany(2L).build()));
        when(companyPort.findByNit("700FOND")).thenReturn(Optional.empty());

        FacturaNegociada factura = buildFacturaNegociada("FAC-001", "900PAG", "800PROV",
                List.of(FondeadorAsignacion.builder().rutFondeador("700FOND").build()));

        assertThatThrownBy(() -> service.revertirCupos(
                factura, Operacion.builder().tipo("FACTORING").clase("N").build(), "800EMP", "OP"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Fondeador no encontrado");
    }

    // ─── helpers ──────────────────────────────────────────────────

    private PendingOtp buildPending(String idNeg, String rutEmpresa, List<AprobacionFacturaRequest> facturas) {
        AprobacionRequest req = new AprobacionRequest(idNeg, "OP-RUT", rutEmpresa, null, facturas, "");
        return PendingOtp.builder().idNegociacion(idNeg).rutEmpresa(rutEmpresa).request(req).build();
    }

    private Negociacion buildNegociacion(String tipo, List<FacturaNegociada> facturas) {
        return Negociacion.builder()
                .id("NEG-001")
                .tipoOperacion(Operacion.builder().tipo(tipo).clase("NORMAL").build())
                .facturas(new ArrayList<>(facturas))
                .build();
    }

    private FacturaNegociada buildFacturaNegociada(String num, String rutPagador, String rutProveedor,
                                                    List<FondeadorAsignacion> fondeadores) {
        return FacturaNegociada.builder()
                .numeroFactura(num)
                .rutPagador(rutPagador)
                .rutProveedor(rutProveedor)
                .fondeadores(fondeadores)
                .build();
    }

    private Factura buildFactura(String num, BigDecimal valor, String rutProveedor, String rutPagador) {
        Factura f = new Factura();
        f.setNumeroFactura(num);
        f.setValorFactura(valor);
        f.setRutProveedor(rutProveedor);
        f.setRutPagador(rutPagador);
        return f;
    }

    private void mockPatchPorts() {
        doNothing().when(firmaEndosoService).firmarPdfEndoso(any(), any(), any(), any(), any(), any(), anyBoolean(), any());
        doNothing().when(endosoPatchPort).registrarAprobacionActor(any(), any(), any(), any());
        doNothing().when(facturaPatchPort).actualizarEstadoFirmaPorNoFactura(any(), any());
        doNothing().when(endosoPatchPort).actualizarEstadoEndosoPorNoFactura(any(), any(), any());
        doNothing().when(notificacionService).notificarFirmaEndoso(any());
    }
}