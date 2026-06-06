package com.negociadororq.application.service;

import com.negociadororq.application.dto.NegociacionRequestDto;
import com.negociadororq.application.dto.ValidacionFacturaResponseDto;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.EmpresaQuotaOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.EmpresaQuota;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.FondeadorQuota;
import com.negociadororq.domain.model.Operacion;
import com.negociadororq.domain.model.User;
import com.negociadororq.domain.model.UserQuota;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcesarFacturaNegociacionServiceImpl — validación y descuento de cupos por factura")
class ProcesarFacturaNegociacionServiceImplTest {

    @Mock
    private FacturaOutputPort facturaPort;

    @Mock
    private CupoConsumidorService cupoConsumidorService;

    @Mock
    private UserOutputPort userPort;

    @Mock
    private CompanyOutputPort companyPort;

    @Mock
    private EmpresaQuotaOutputPort empresaQuotaPort;

    @Mock
    private FondeadorQuotaOutputPort fondeadorQuotaPort;

    @Mock
    private FacturaPatchOutputPort facturaPatchPort;

    @Mock
    private ValidacionFacturaService validacionFacturaService;

    @InjectMocks
    private ProcesarFacturaNegociacionServiceImpl service;

    private NegociacionRequestDto buildRequest(String tipoOp) {
        NegociacionRequestDto req = new NegociacionRequestDto();
        req.setRutEmpresa("800111222");
        req.setRolEmpresa("PROVEEDOR");
        req.setRutOperador("op-rut");
        req.setTipoOperacion(Operacion.builder().tipo(tipoOp).clase("NORMAL").build());
        req.setFondeadoresGlobales(List.of(FondeadorAsignacion.builder()
                .rutFondeador("700555666").nombreFondeador("Fond").build()));
        return req;
    }

    private Factura buildFactura(String rutProveedor, String rutPagador, String estado) {
        Factura f = new Factura();
        f.setId("fac-id-001");
        f.setNumeroFactura("FAC-001");
        f.setRutProveedor(rutProveedor);
        f.setRutPagador(rutPagador);
        f.setEstadoNegociacion(estado);
        f.setValorFactura(new BigDecimal("5000"));
        f.setValorNetoFactura(new BigDecimal("4800"));
        return f;
    }

    private FacturaNegociada buildFacturaNegociada() {
        return FacturaNegociada.builder()
                .numeroFactura("FAC-001")
                .build();
    }

    private void mockHappyPathFactoring(BigDecimal valorEmpresa, BigDecimal valorFondeador) {
        when(facturaPort.findByNumeroFactura("FAC-001"))
                .thenReturn(Optional.of(buildFactura("800111222", "900333444", "SIN_NEGOCIAR")));
        when(validacionFacturaService.validarFacturaVencida("FAC-001", null))
                .thenReturn(ValidacionFacturaResponseDto.builder().valida(true).build());
        when(userPort.findByUsername("op-rut"))
                .thenReturn(Optional.of(User.builder().ideUser(10L).build()));
        when(cupoConsumidorService.obtenerCupoDisponible(10L))
                .thenReturn(new BigDecimal("10000"));
        when(companyPort.findByNit("800111222"))
                .thenReturn(Optional.of(Company.builder().ideCompany(20L).build()));
        when(empresaQuotaPort.findByCompanyId(20L))
                .thenReturn(Optional.of(EmpresaQuota.builder()
                        .quota(new BigDecimal("50000")).currentQuota(valorEmpresa).build()));
        when(companyPort.findByNit("700555666"))
                .thenReturn(Optional.of(Company.builder().ideCompany(30L).build()));
        when(companyPort.findByNit("900333444"))
                .thenReturn(Optional.of(Company.builder().ideCompany(40L).build()));
        when(fondeadorQuotaPort.findByFunderIdAndSupplierId(30L, 20L))
                .thenReturn(Optional.of(FondeadorQuota.builder()
                        .supplierCurrentQuota(valorFondeador)
                        .payerCurrentQuota(valorFondeador)
                        .build()));
        when(fondeadorQuotaPort.findByFunderIdAndPayerId(30L, 40L))
                .thenReturn(Optional.of(FondeadorQuota.builder()
                        .supplierCurrentQuota(valorFondeador)
                        .payerCurrentQuota(valorFondeador)
                        .build()));
        when(empresaQuotaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fondeadorQuotaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── Validaciones ─────────────────────────────────────────────

    @Test
    @DisplayName("Lanza RuntimeException cuando la factura no existe")
    void facturaNoExiste_lanzaExcepcion() {
        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.procesarFactura(buildRequest("FACTORING"), buildFacturaNegociada()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("FAC-001");
    }

    @Test
    @DisplayName("Lanza RuntimeException cuando la factura está vencida")
    void facturaVencida_lanzaExcepcion() {
        when(facturaPort.findByNumeroFactura("FAC-001"))
                .thenReturn(Optional.of(buildFactura("800111222", "900333444", "SIN_NEGOCIAR")));
        when(validacionFacturaService.validarFacturaVencida("FAC-001", null))
                .thenReturn(ValidacionFacturaResponseDto.builder()
                        .valida(false)
                        .mensaje("Factura vencida")
                        .build());

        assertThatThrownBy(() -> service.procesarFactura(buildRequest("FACTORING"), buildFacturaNegociada()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Factura vencida");
    }

    @Test
    @DisplayName("Lanza RuntimeException cuando la factura no está en estado SIN_NEGOCIAR")
    void estadoInvalido_lanzaExcepcion() {
        when(facturaPort.findByNumeroFactura("FAC-001"))
                .thenReturn(Optional.of(buildFactura("800111222", "900333444", "NEGOCIADA")));
        when(validacionFacturaService.validarFacturaVencida("FAC-001", null))
                .thenReturn(ValidacionFacturaResponseDto.builder().valida(true).build());

        assertThatThrownBy(() -> service.procesarFactura(buildRequest("FACTORING"), buildFacturaNegociada()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("NEGOCIADA");
    }

    @Test
    @DisplayName("FACTORING: rechaza cuando la empresa no es el proveedor")
    void factoring_empresaNoEsProveedor() {
        NegociacionRequestDto req = buildRequest("FACTORING");
        req.setRutEmpresa("OTRO-RUT");
        Factura factura = buildFactura("800111222", "900333444", "SIN_NEGOCIAR");

        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));
        when(validacionFacturaService.validarFacturaVencida("FAC-001", null))
                .thenReturn(ValidacionFacturaResponseDto.builder().valida(true).build());

        assertThatThrownBy(() -> service.procesarFactura(req, buildFacturaNegociada()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("proveedor");
    }

    @Test
    @DisplayName("CONFIRMING: rechaza cuando la empresa no es el pagador")
    void confirming_empresaNoEsPagador() {
        NegociacionRequestDto req = buildRequest("CONFIRMING");
        req.setRutEmpresa("800111222");
        Factura factura = buildFactura("800PROV", "900333444", "SIN_NEGOCIAR");
        factura.setRutPagador("OTRO-PAG");

        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));
        when(validacionFacturaService.validarFacturaVencida("FAC-001", null))
                .thenReturn(ValidacionFacturaResponseDto.builder().valida(true).build());

        assertThatThrownBy(() -> service.procesarFactura(req, buildFacturaNegociada()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("pagador");
    }

    @Test
    @DisplayName("Lanza RuntimeException cuando el operador no existe")
    void operadorNoExiste_lanzaExcepcion() {
        when(facturaPort.findByNumeroFactura("FAC-001"))
                .thenReturn(Optional.of(buildFactura("800111222", "900333444", "SIN_NEGOCIAR")));
        when(validacionFacturaService.validarFacturaVencida("FAC-001", null))
                .thenReturn(ValidacionFacturaResponseDto.builder().valida(true).build());
        when(userPort.findByUsername("op-rut")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.procesarFactura(buildRequest("FACTORING"), buildFacturaNegociada()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Operador no existe");
    }

    @Test
    @DisplayName("Lanza RuntimeException cuando el cupo del operador es insuficiente")
    void cupoOperadorInsuficiente_lanzaExcepcion() {
        when(facturaPort.findByNumeroFactura("FAC-001"))
                .thenReturn(Optional.of(buildFactura("800111222", "900333444", "SIN_NEGOCIAR")));
        when(validacionFacturaService.validarFacturaVencida("FAC-001", null))
                .thenReturn(ValidacionFacturaResponseDto.builder().valida(true).build());
        when(userPort.findByUsername("op-rut"))
                .thenReturn(Optional.of(User.builder().ideUser(10L).build()));
        when(cupoConsumidorService.obtenerCupoDisponible(10L)).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.procesarFactura(buildRequest("FACTORING"), buildFacturaNegociada()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("sin cupo disponible");
    }

    @Test
    @DisplayName("Lanza RuntimeException cuando la empresa no existe")
    void empresaNoExiste_lanzaExcepcion() {
        when(facturaPort.findByNumeroFactura("FAC-001"))
                .thenReturn(Optional.of(buildFactura("800111222", "900333444", "SIN_NEGOCIAR")));
        when(validacionFacturaService.validarFacturaVencida("FAC-001", null))
                .thenReturn(ValidacionFacturaResponseDto.builder().valida(true).build());
        when(userPort.findByUsername("op-rut"))
                .thenReturn(Optional.of(User.builder().ideUser(10L).build()));
        when(cupoConsumidorService.obtenerCupoDisponible(10L)).thenReturn(new BigDecimal("10000"));
        when(companyPort.findByNit("800111222")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.procesarFactura(buildRequest("FACTORING"), buildFacturaNegociada()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Empresa no encontrada");
    }

    @Test
    @DisplayName("Lanza RuntimeException cuando la empresa no tiene cupo suficiente")
    void cupoEmpresaInsuficiente_lanzaExcepcion() {
        when(facturaPort.findByNumeroFactura("FAC-001"))
                .thenReturn(Optional.of(buildFactura("800111222", "900333444", "SIN_NEGOCIAR")));
        when(validacionFacturaService.validarFacturaVencida("FAC-001", null))
                .thenReturn(ValidacionFacturaResponseDto.builder().valida(true).build());
        when(userPort.findByUsername("op-rut"))
                .thenReturn(Optional.of(User.builder().ideUser(10L).build()));
        when(cupoConsumidorService.obtenerCupoDisponible(10L)).thenReturn(new BigDecimal("10000"));
        when(companyPort.findByNit("800111222"))
                .thenReturn(Optional.of(Company.builder().ideCompany(20L).build()));
        when(empresaQuotaPort.findByCompanyId(20L))
                .thenReturn(Optional.of(EmpresaQuota.builder()
                        .quota(new BigDecimal("50000")).currentQuota(BigDecimal.ZERO).build()));

        assertThatThrownBy(() -> service.procesarFactura(buildRequest("FACTORING"), buildFacturaNegociada()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("sin cupo");
    }

    // ─── Flujo exitoso ────────────────────────────────────────────

    @Test
    @DisplayName("FACTORING exitoso: retorna FacturaNegociada con fondeadores y valores")
    void factoring_exitoso() {
        mockHappyPathFactoring(new BigDecimal("30000"), new BigDecimal("20000"));

        NegociacionRequestDto req = buildRequest("FACTORING");
        FacturaNegociada fn = buildFacturaNegociada();

        FacturaNegociada result = service.procesarFactura(req, fn);

        assertThat(result.getRutProveedor()).isEqualTo("800111222");
        assertThat(result.getRutPagador()).isEqualTo("900333444");
        assertThat(result.getFondeadores()).isNotEmpty();
        verify(cupoConsumidorService).descontarCupo(10L, new BigDecimal("4800"));
        verify(facturaPatchPort).marcarComoNegociada(eq("fac-id-001"), eq(EstadoFirma.SIN_CESION), any());
    }

    @Test
    @DisplayName("CONFIRMING exitoso: usa EstadoFirma.SIN_DESCUENTO")
    void confirming_exitoso_estadoFirmaSinDescuento() {
        Factura factura = buildFactura("800PROV", "800111222", "SIN_NEGOCIAR");
        factura.setValorNetoFactura(new BigDecimal("3000"));

        NegociacionRequestDto req = buildRequest("CONFIRMING");
        req.setRutEmpresa("800111222");

        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));
        when(validacionFacturaService.validarFacturaVencida("FAC-001", null))
                .thenReturn(ValidacionFacturaResponseDto.builder().valida(true).build());
        when(userPort.findByUsername("op-rut"))
                .thenReturn(Optional.of(User.builder().ideUser(10L).build()));
        when(cupoConsumidorService.obtenerCupoDisponible(10L)).thenReturn(new BigDecimal("10000"));
        when(companyPort.findByNit("800111222"))
                .thenReturn(Optional.of(Company.builder().ideCompany(20L).build()));
        when(empresaQuotaPort.findByCompanyId(20L))
                .thenReturn(Optional.of(EmpresaQuota.builder()
                        .quota(new BigDecimal("50000")).currentQuota(new BigDecimal("10000")).build()));
        when(companyPort.findByNit("700555666"))
                .thenReturn(Optional.of(Company.builder().ideCompany(30L).build()));
        when(companyPort.findByNit("800PROV"))
                .thenReturn(Optional.of(Company.builder().ideCompany(50L).build()));
        when(fondeadorQuotaPort.findByFunderIdAndPayerId(30L, 20L))
                .thenReturn(Optional.of(FondeadorQuota.builder()
                        .payerCurrentQuota(new BigDecimal("5000"))
                        .supplierCurrentQuota(new BigDecimal("5000")).build()));
        when(empresaQuotaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fondeadorQuotaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.procesarFactura(req, buildFacturaNegociada());

        verify(facturaPatchPort).marcarComoNegociada(eq("fac-id-001"), eq(EstadoFirma.SIN_DESCUENTO), any());
    }

    @Test
    @DisplayName("Usa valorFactura cuando valorNetoFactura es null")
    void sinValorNeto_usaValorFactura() {
        Factura factura = buildFactura("800111222", "900333444", "SIN_NEGOCIAR");
        factura.setValorNetoFactura(null);
        factura.setValorFactura(new BigDecimal("7000"));

        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));
        when(validacionFacturaService.validarFacturaVencida("FAC-001", null))
                .thenReturn(ValidacionFacturaResponseDto.builder().valida(true).build());
        when(userPort.findByUsername("op-rut"))
                .thenReturn(Optional.of(User.builder().ideUser(10L).build()));
        when(cupoConsumidorService.obtenerCupoDisponible(10L)).thenReturn(new BigDecimal("10000"));
        when(companyPort.findByNit("800111222"))
                .thenReturn(Optional.of(Company.builder().ideCompany(20L).build()));
        when(empresaQuotaPort.findByCompanyId(20L))
                .thenReturn(Optional.of(EmpresaQuota.builder()
                        .quota(new BigDecimal("50000")).currentQuota(new BigDecimal("20000")).build()));
        when(companyPort.findByNit("700555666"))
                .thenReturn(Optional.of(Company.builder().ideCompany(30L).build()));
        when(companyPort.findByNit("900333444"))
                .thenReturn(Optional.of(Company.builder().ideCompany(40L).build()));
        when(fondeadorQuotaPort.findByFunderIdAndSupplierId(30L, 20L))
                .thenReturn(Optional.of(FondeadorQuota.builder()
                        .supplierCurrentQuota(new BigDecimal("15000"))
                        .payerCurrentQuota(new BigDecimal("15000")).build()));
        when(fondeadorQuotaPort.findByFunderIdAndPayerId(30L, 40L))
                .thenReturn(Optional.of(FondeadorQuota.builder()
                        .supplierCurrentQuota(new BigDecimal("15000"))
                        .payerCurrentQuota(new BigDecimal("15000")).build()));
        when(empresaQuotaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fondeadorQuotaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.procesarFactura(buildRequest("FACTORING"), buildFacturaNegociada());

        verify(cupoConsumidorService).descontarCupo(10L, new BigDecimal("7000"));
    }

    @Test
    @DisplayName("Fondeadores del request facturaReq tienen prioridad sobre los globales")
    void fondeadoresFactura_tienePrioridad() {
        FondeadorAsignacion fondeadorLocal = FondeadorAsignacion.builder()
                .rutFondeador("LOCAL-FOND").nombreFondeador("Local").build();
        FacturaNegociada fn = FacturaNegociada.builder()
                .numeroFactura("FAC-001")
                .fondeadores(List.of(fondeadorLocal))
                .build();

        NegociacionRequestDto req = buildRequest("FACTORING");
        Factura factura = buildFactura("800111222", "900333444", "SIN_NEGOCIAR");

        when(facturaPort.findByNumeroFactura("FAC-001")).thenReturn(Optional.of(factura));
        when(validacionFacturaService.validarFacturaVencida("FAC-001", null))
                .thenReturn(ValidacionFacturaResponseDto.builder().valida(true).build());
        when(userPort.findByUsername("op-rut"))
                .thenReturn(Optional.of(User.builder().ideUser(10L).build()));
        when(cupoConsumidorService.obtenerCupoDisponible(10L)).thenReturn(new BigDecimal("10000"));
        when(companyPort.findByNit("800111222"))
                .thenReturn(Optional.of(Company.builder().ideCompany(20L).build()));
        when(empresaQuotaPort.findByCompanyId(20L))
                .thenReturn(Optional.of(EmpresaQuota.builder()
                        .quota(new BigDecimal("50000")).currentQuota(new BigDecimal("20000")).build()));
        when(companyPort.findByNit("LOCAL-FOND"))
                .thenReturn(Optional.of(Company.builder().ideCompany(99L).build()));
        when(companyPort.findByNit("900333444"))
                .thenReturn(Optional.of(Company.builder().ideCompany(40L).build()));
        when(fondeadorQuotaPort.findByFunderIdAndSupplierId(99L, 20L))
                .thenReturn(Optional.of(FondeadorQuota.builder()
                        .supplierCurrentQuota(new BigDecimal("15000"))
                        .payerCurrentQuota(new BigDecimal("15000")).build()));
        when(fondeadorQuotaPort.findByFunderIdAndPayerId(99L, 40L))
                .thenReturn(Optional.of(FondeadorQuota.builder()
                        .supplierCurrentQuota(new BigDecimal("15000"))
                        .payerCurrentQuota(new BigDecimal("15000")).build()));
        when(empresaQuotaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fondeadorQuotaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FacturaNegociada result = service.procesarFactura(req, fn);

        assertThat(result.getFondeadores()).hasSize(1);
        assertThat(result.getFondeadores().get(0).getRutFondeador()).isEqualTo("LOCAL-FOND");

        verify(fondeadorQuotaPort, never()).findByFunderIdAndSupplierId(eq(30L), any());
    }
}