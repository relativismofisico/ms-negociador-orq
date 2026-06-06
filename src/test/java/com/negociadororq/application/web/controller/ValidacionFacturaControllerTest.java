package com.negociadororq.application.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.negociadororq.application.dto.ValidacionFacturaRequestDto;
import com.negociadororq.application.dto.ValidacionFacturaResponseDto;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.service.ValidacionFacturaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = ValidacionFacturaController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@DisplayName("ValidacionFacturaController — endpoint POST /validaciones/facturas/vencimiento")
class ValidacionFacturaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ValidacionFacturaService validacionFacturaService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("200 OK — retorna resultado de validación para factura vigente")
    void validarVencimiento_200_vigente() throws Exception {
        ValidacionFacturaResponseDto dto = ValidacionFacturaResponseDto.builder()
                .valida(true)
                .vencida(false)
                .diasHastaVencimiento(30L)
                .codigo("FACTURA_VIGENTE")
                .mensaje("Factura vigente para negociación/liquidación")
                .build();

        when(validacionFacturaService.validarFacturaVencida(anyString(), any())).thenReturn(dto);

        ValidacionFacturaRequestDto request = ValidacionFacturaRequestDto.builder()
                .numeroFactura("FAC-001")
                .fechaProceso(LocalDate.now())
                .build();

        mockMvc.perform(post("/validaciones/facturas/vencimiento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(true))
                .andExpect(jsonPath("$.codigo").value("FACTURA_VIGENTE"))
                .andExpect(jsonPath("$.diasHastaVencimiento").value(30));
    }

    @Test
    @DisplayName("200 OK — retorna resultado de validación para factura vencida")
    void validarVencimiento_200_vencida() throws Exception {
        ValidacionFacturaResponseDto dto = ValidacionFacturaResponseDto.builder()
                .valida(false)
                .vencida(true)
                .diasHastaVencimiento(5L)
                .codigo("FACTURA_VENCIDA")
                .mensaje("Factura no negociable")
                .build();

        when(validacionFacturaService.validarFacturaVencida(anyString(), any())).thenReturn(dto);

        ValidacionFacturaRequestDto request = ValidacionFacturaRequestDto.builder()
                .numeroFactura("FAC-002")
                .build();

        mockMvc.perform(post("/validaciones/facturas/vencimiento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valida").value(false))
                .andExpect(jsonPath("$.vencida").value(true));
    }

    @Test
    @DisplayName("404 — factura no encontrada propaga la excepción")
    void validarVencimiento_404_noEncontrada() throws Exception {
        when(validacionFacturaService.validarFacturaVencida(anyString(), any()))
                .thenThrow(new RecursoNoEncontradoException("Factura no encontrada: FAC-X", "FACTURA_NO_ENCONTRADA"));

        ValidacionFacturaRequestDto request = ValidacionFacturaRequestDto.builder()
                .numeroFactura("FAC-X")
                .build();

        mockMvc.perform(post("/validaciones/facturas/vencimiento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("400 — body malformado retorna BAD_REQUEST")
    void validarVencimiento_400_bodyMalformado() throws Exception {
        mockMvc.perform(post("/validaciones/facturas/vencimiento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Content-Type de respuesta es application/json")
    void validarVencimiento_contentTypeJson() throws Exception {
        when(validacionFacturaService.validarFacturaVencida(anyString(), any()))
                .thenReturn(ValidacionFacturaResponseDto.builder()
                        .valida(true).build());

        ValidacionFacturaRequestDto request = ValidacionFacturaRequestDto.builder()
                .numeroFactura("FAC-001")
                .build();

        mockMvc.perform(post("/validaciones/facturas/vencimiento")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}