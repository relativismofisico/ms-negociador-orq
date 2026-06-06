package com.negociadororq.application.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.negociadororq.application.dto.AprobacionRequestDto;
import com.negociadororq.application.dto.ConfirmacionOtpRequestDto;
import com.negociadororq.application.service.AprobacionEndosoDescuentoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
@WebMvcTest(
        value = AprobarEndosoDescuentoController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@DisplayName("AprobarEndosoDescuentoController — endpoints OTP")
class AprobarEndosoDescuentoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AprobacionEndosoDescuentoService service;

    @Autowired
    private ObjectMapper objectMapper;

    // ─── solicitar-otp ────────────────────────────────────────────

    @Test
    @DisplayName("POST /solicitar-otp → 202 Accepted con mensaje 'OTP solicitado'")
    void solicitarOtp_202() throws Exception {
        doNothing().when(service).solicitarOtp(any());

        AprobacionRequestDto request = new AprobacionRequestDto();
        request.setIdNegociacion("NEG-001");
        request.setRutOperador("900333444");
        request.setRutEmpresa("800111222");

        mockMvc.perform(post("/negociaciones/solicitar-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().string("OTP solicitado"));
    }

    @Test
    @DisplayName("POST /solicitar-otp invoca al servicio con el request")
    void solicitarOtp_invocaServicio() throws Exception {
        doNothing().when(service).solicitarOtp(any());

        AprobacionRequestDto request = new AprobacionRequestDto();
        request.setIdNegociacion("NEG-TEST");
        request.setRutOperador("900111");
        request.setRutEmpresa("800222");

        mockMvc.perform(post("/negociaciones/solicitar-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        verify(service).solicitarOtp(any());
    }

    @Test
    @DisplayName("POST /solicitar-otp → 400 cuando el body es malformado")
    void solicitarOtp_400_bodyMalformado() throws Exception {
        mockMvc.perform(post("/negociaciones/solicitar-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad json}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /solicitar-otp → 500 cuando el servicio lanza excepción inesperada")
    void solicitarOtp_500_excepcionServicio() throws Exception {
        doThrow(new RuntimeException("Error interno")).when(service).solicitarOtp(any());

        AprobacionRequestDto request = new AprobacionRequestDto();
        request.setIdNegociacion("NEG-001");

        mockMvc.perform(post("/negociaciones/solicitar-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    // ─── confirmar-otp ────────────────────────────────────────────

    @Test
    @DisplayName("POST /confirmar-otp → 202 Accepted con mensaje 'Validando OTP...'")
    void confirmarOtp_202() throws Exception {
        doNothing().when(service).confirmarOtp(any());

        ConfirmacionOtpRequestDto request = ConfirmacionOtpRequestDto.builder()
                .idNegociacion("NEG-001")
                .rutEmpresa("800111222")
                .otp("123456")
                .build();

        mockMvc.perform(post("/negociaciones/confirmar-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(content().string("Validando OTP..."));
    }

    @Test
    @DisplayName("POST /confirmar-otp inyecta IP del cliente en el request")
    void confirmarOtp_inyectaIp() throws Exception {
        doNothing().when(service).confirmarOtp(any());

        ConfirmacionOtpRequestDto request = ConfirmacionOtpRequestDto.builder()
                .idNegociacion("NEG-001")
                .rutEmpresa("800111222")
                .otp("654321")
                .build();

        mockMvc.perform(post("/negociaciones/confirmar-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-Forwarded-For", "192.168.50.100"))
                .andExpect(status().isAccepted());

        verify(service).confirmarOtp(
                org.mockito.ArgumentMatchers.argThat(r ->
                        "192.168.50.100".equals(r.getIpAprobacion())));
    }

    @Test
    @DisplayName("POST /confirmar-otp → 400 cuando el body es malformado")
    void confirmarOtp_400_bodyMalformado() throws Exception {
        mockMvc.perform(post("/negociaciones/confirmar-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /confirmar-otp → 500 cuando el servicio falla")
    void confirmarOtp_500_excepcionServicio() throws Exception {
        doThrow(new RuntimeException("Fallo OTP")).when(service).confirmarOtp(any());

        ConfirmacionOtpRequestDto request = ConfirmacionOtpRequestDto.builder()
                .idNegociacion("NEG-001")
                .otp("000000")
                .build();

        mockMvc.perform(post("/negociaciones/confirmar-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }
}