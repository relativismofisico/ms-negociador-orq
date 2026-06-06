package com.negociadororq.application.service;

import com.negociadororq.application.port.output.EmpresaQuotaOutputPort;
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.application.port.output.UserQuotaOutputPort;
import com.negociadororq.domain.model.EmpresaQuota;
import com.negociadororq.domain.model.FondeadorQuota;
import com.negociadororq.domain.model.UserQuota;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OperadorServiceImpl — gestión de cupos")
class OperadorServiceImplTest {

    @Mock
    private UserQuotaOutputPort userQuotaPort;

    @Mock
    private EmpresaQuotaOutputPort empresaQuotaPort;

    @Mock
    private FondeadorQuotaOutputPort fondeadorQuotaPort;

    @InjectMocks
    private OperadorServiceImpl service;

    @Captor
    private ArgumentCaptor<UserQuota> userQuotaCaptor;

    @Captor
    private ArgumentCaptor<EmpresaQuota> empresaQuotaCaptor;

    @Captor
    private ArgumentCaptor<FondeadorQuota> fondeadorQuotaCaptor;

    // ─── obtenerCupoDisponible ────────────────────────────────────

    @Test
    @DisplayName("obtenerCupoDisponible retorna currentQuota del usuario")
    void obtenerCupoDisponible_retornaCupo() {
        UserQuota quota = UserQuota.builder().userId(1L).currentQuota(new BigDecimal("5000")).build();
        when(userQuotaPort.findByUserId(1L)).thenReturn(Optional.of(quota));

        BigDecimal result = service.obtenerCupoDisponible(1L);

        assertThat(result).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("obtenerCupoDisponible lanza RuntimeException cuando el usuario no existe")
    void obtenerCupoDisponible_usuarioInexistente() {
        when(userQuotaPort.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerCupoDisponible(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("99");
    }

    // ─── descontarCupo ────────────────────────────────────────────

    @Test
    @DisplayName("descontarCupo descuenta correctamente cuando hay cupo suficiente")
    void descontarCupo_suficiente() {
        UserQuota quota = UserQuota.builder().userId(1L).currentQuota(new BigDecimal("10000")).build();
        when(userQuotaPort.findByUserId(1L)).thenReturn(Optional.of(quota));

        service.descontarCupo(1L, new BigDecimal("3000"));

        verify(userQuotaPort).save(userQuotaCaptor.capture());
        assertThat(userQuotaCaptor.getValue().getCurrentQuota()).isEqualByComparingTo("7000");
    }

    @Test
    @DisplayName("descontarCupo lanza RuntimeException cuando el cupo es insuficiente")
    void descontarCupo_insuficiente() {
        UserQuota quota = UserQuota.builder().userId(1L).currentQuota(new BigDecimal("500")).build();
        when(userQuotaPort.findByUserId(1L)).thenReturn(Optional.of(quota));

        assertThatThrownBy(() -> service.descontarCupo(1L, new BigDecimal("1000")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cupo insuficiente");
    }

    @Test
    @DisplayName("descontarCupo lanza RuntimeException cuando el usuario no existe")
    void descontarCupo_usuarioInexistente() {
        when(userQuotaPort.findByUserId(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.descontarCupo(2L, BigDecimal.TEN))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("descontarCupo con monto igual al cupo disponible funciona (límite exacto)")
    void descontarCupo_limiteExacto() {
        UserQuota quota = UserQuota.builder().userId(1L).currentQuota(new BigDecimal("1000")).build();
        when(userQuotaPort.findByUserId(1L)).thenReturn(Optional.of(quota));

        service.descontarCupo(1L, new BigDecimal("1000"));

        verify(userQuotaPort).save(userQuotaCaptor.capture());
        assertThat(userQuotaCaptor.getValue().getCurrentQuota()).isEqualByComparingTo("0");
    }

    // ─── revertirCupo ─────────────────────────────────────────────

    @Test
    @DisplayName("revertirCupo suma el valor al cupo actual del usuario")
    void revertirCupo_sumaValor() {
        UserQuota quota = UserQuota.builder().userId(1L).currentQuota(new BigDecimal("2000")).build();
        when(userQuotaPort.findByUserId(1L)).thenReturn(Optional.of(quota));

        service.revertirCupo(1L, new BigDecimal("500"));

        verify(userQuotaPort).save(userQuotaCaptor.capture());
        assertThat(userQuotaCaptor.getValue().getCurrentQuota()).isEqualByComparingTo("2500");
    }

    @Test
    @DisplayName("revertirCupo lanza excepción cuando el usuario no existe")
    void revertirCupo_usuarioInexistente() {
        when(userQuotaPort.findByUserId(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revertirCupo(5L, BigDecimal.ONE))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── revertirCupoUsuario ──────────────────────────────────────

    @Test
    @DisplayName("revertirCupoUsuario suma valor y no baja de cero")
    void revertirCupoUsuario_noMenorQueCero() {
        UserQuota quota = UserQuota.builder().userId(1L).currentQuota(BigDecimal.ZERO).build();
        when(userQuotaPort.findByUserId(1L)).thenReturn(Optional.of(quota));

        service.revertirCupoUsuario(1L, new BigDecimal("100"));

        verify(userQuotaPort).save(userQuotaCaptor.capture());
        assertThat(userQuotaCaptor.getValue().getCurrentQuota()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("revertirCupoUsuario no hace nada si el usuario no existe (ifPresent)")
    void revertirCupoUsuario_usuarioInexistente_noOp() {
        when(userQuotaPort.findByUserId(99L)).thenReturn(Optional.empty());

        service.revertirCupoUsuario(99L, BigDecimal.TEN);

        verify(userQuotaPort, org.mockito.Mockito.never()).save(any());
    }

    // ─── revertirCupoEmpresa ──────────────────────────────────────

    @Test
    @DisplayName("revertirCupoEmpresa suma valor absoluto sin exceder la cuota máxima")
    void revertirCupoEmpresa_sinExcederMaximo() {
        EmpresaQuota eq = EmpresaQuota.builder()
                .companyId(10L)
                .quota(new BigDecimal("50000"))
                .currentQuota(new BigDecimal("45000"))
                .build();
        when(empresaQuotaPort.findByCompanyId(10L)).thenReturn(Optional.of(eq));

        service.revertirCupoEmpresa(10L, new BigDecimal("3000"));

        verify(empresaQuotaPort).saveAndFlush(empresaQuotaCaptor.capture());
        assertThat(empresaQuotaCaptor.getValue().getCurrentQuota()).isEqualByComparingTo("48000");
    }

    @Test
    @DisplayName("revertirCupoEmpresa limita al máximo de la cuota si se excede")
    void revertirCupoEmpresa_limitaAlMaximo() {
        EmpresaQuota eq = EmpresaQuota.builder()
                .companyId(10L)
                .quota(new BigDecimal("50000"))
                .currentQuota(new BigDecimal("49000"))
                .build();
        when(empresaQuotaPort.findByCompanyId(10L)).thenReturn(Optional.of(eq));

        service.revertirCupoEmpresa(10L, new BigDecimal("5000"));

        verify(empresaQuotaPort).saveAndFlush(empresaQuotaCaptor.capture());
        assertThat(empresaQuotaCaptor.getValue().getCurrentQuota()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("revertirCupoEmpresa convierte valor negativo a positivo (abs)")
    void revertirCupoEmpresa_valorNegativo_convierte() {
        EmpresaQuota eq = EmpresaQuota.builder()
                .companyId(10L)
                .quota(new BigDecimal("50000"))
                .currentQuota(new BigDecimal("10000"))
                .build();
        when(empresaQuotaPort.findByCompanyId(10L)).thenReturn(Optional.of(eq));

        service.revertirCupoEmpresa(10L, new BigDecimal("-2000"));

        verify(empresaQuotaPort).saveAndFlush(empresaQuotaCaptor.capture());
        assertThat(empresaQuotaCaptor.getValue().getCurrentQuota()).isEqualByComparingTo("12000");
    }

    @Test
    @DisplayName("revertirCupoEmpresa lanza NoSuchElementException si empresa no encontrada")
    void revertirCupoEmpresa_empresaNoEncontrada() {
        when(empresaQuotaPort.findByCompanyId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revertirCupoEmpresa(99L, BigDecimal.TEN))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ─── revertirCupoFondeadorConfirming ─────────────────────────

    @Test
    @DisplayName("revertirCupoFondeadorConfirming suma y guarda sin exceder payerQuota")
    void revertirCupoFondeadorConfirming_conLimite() {
        FondeadorQuota fq = FondeadorQuota.builder()
                .funderId(1L).payerId(2L)
                .payerQuota(new BigDecimal("10000"))
                .payerCurrentQuota(new BigDecimal("8000"))
                .build();
        when(fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 2L)).thenReturn(Optional.of(fq));

        service.revertirCupoFondeadorConfirming(1L, 2L, new BigDecimal("500"));

        verify(fondeadorQuotaPort).save(fondeadorQuotaCaptor.capture());
        assertThat(fondeadorQuotaCaptor.getValue().getPayerCurrentQuota()).isEqualByComparingTo("8500");
    }

    @Test
    @DisplayName("revertirCupoFondeadorConfirming limita al máximo de payerQuota")
    void revertirCupoFondeadorConfirming_limitaAlMaximo() {
        FondeadorQuota fq = FondeadorQuota.builder()
                .funderId(1L).payerId(2L)
                .payerQuota(new BigDecimal("10000"))
                .payerCurrentQuota(new BigDecimal("9800"))
                .build();
        when(fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 2L)).thenReturn(Optional.of(fq));

        service.revertirCupoFondeadorConfirming(1L, 2L, new BigDecimal("5000"));

        verify(fondeadorQuotaPort).save(fondeadorQuotaCaptor.capture());
        assertThat(fondeadorQuotaCaptor.getValue().getPayerCurrentQuota()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("revertirCupoFondeadorConfirming usa cero cuando payerCurrentQuota es null")
    void revertirCupoFondeadorConfirming_payerCurrentQuotaNulo() {
        FondeadorQuota fq = FondeadorQuota.builder()
                .funderId(1L).payerId(2L)
                .payerQuota(new BigDecimal("10000"))
                .payerCurrentQuota(null)
                .build();
        when(fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 2L)).thenReturn(Optional.of(fq));

        service.revertirCupoFondeadorConfirming(1L, 2L, new BigDecimal("1000"));

        verify(fondeadorQuotaPort).save(fondeadorQuotaCaptor.capture());
        assertThat(fondeadorQuotaCaptor.getValue().getPayerCurrentQuota()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("revertirCupoFondeadorConfirming lanza excepción cuando cupo no existe")
    void revertirCupoFondeadorConfirming_noExiste() {
        when(fondeadorQuotaPort.findByFunderIdAndPayerId(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revertirCupoFondeadorConfirming(1L, 2L, BigDecimal.TEN))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("confirming");
    }

    // ─── revertirCupoFondeadorFactoring ──────────────────────────

    @Test
    @DisplayName("revertirCupoFondeadorFactoring suma y guarda sin exceder supplierQuota")
    void revertirCupoFondeadorFactoring_conLimite() {
        FondeadorQuota fq = FondeadorQuota.builder()
                .funderId(1L).supplierId(3L)
                .supplierQuota(new BigDecimal("20000"))
                .supplierCurrentQuota(new BigDecimal("15000"))
                .build();
        when(fondeadorQuotaPort.findByFunderIdAndSupplierId(1L, 3L)).thenReturn(Optional.of(fq));

        service.revertirCupoFondeadorFactoring(1L, 3L, new BigDecimal("2000"));

        verify(fondeadorQuotaPort).save(fondeadorQuotaCaptor.capture());
        assertThat(fondeadorQuotaCaptor.getValue().getSupplierCurrentQuota()).isEqualByComparingTo("17000");
    }

    @Test
    @DisplayName("revertirCupoFondeadorFactoring lanza excepción cuando cupo no existe")
    void revertirCupoFondeadorFactoring_noExiste() {
        when(fondeadorQuotaPort.findByFunderIdAndSupplierId(1L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revertirCupoFondeadorFactoring(1L, 3L, BigDecimal.TEN))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("factoring");
    }

    @Test
    @DisplayName("revertirCupoFondeadorFactoring usa cero cuando supplierCurrentQuota es null")
    void revertirCupoFondeadorFactoring_supplierCurrentQuotaNulo() {
        FondeadorQuota fq = FondeadorQuota.builder()
                .funderId(1L).supplierId(3L)
                .supplierQuota(new BigDecimal("10000"))
                .supplierCurrentQuota(null)
                .build();
        when(fondeadorQuotaPort.findByFunderIdAndSupplierId(1L, 3L)).thenReturn(Optional.of(fq));

        service.revertirCupoFondeadorFactoring(1L, 3L, new BigDecimal("500"));

        verify(fondeadorQuotaPort).save(fondeadorQuotaCaptor.capture());
        assertThat(fondeadorQuotaCaptor.getValue().getSupplierCurrentQuota()).isEqualByComparingTo("500");
    }
}