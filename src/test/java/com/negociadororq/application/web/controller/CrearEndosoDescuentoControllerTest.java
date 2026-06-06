package com.negociadororq.application.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.negociadororq.application.dto.EndosoRequestDto;
import com.negociadororq.application.dto.EndosoResponseDto;
import com.negociadororq.application.exception.PeticionInvalidaException;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.service.EndosoDescuentoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = CrearEndosoDescuentoController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@DisplayName("CrearEndosoDescuentoController — endpoint POST /negociaciones/{id}/endosos")
class CrearEndosoDescuentoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EndosoDescuentoService endosoDescuentoService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("201 CREATED — registra endoso y retorna respuesta")
    void registrarEndoso_201() throws Exception {
        EndosoResponseDto response = EndosoResponseDto.builder()
                .idNegociacion("NEG-001")
                .estadoEndoso("PENDIENTE")
                .facturasEndosadas(List.of("FAC-001"))
                .facturasRechazadas(List.of())
                .build();

        when(endosoDescuentoService.registrarEndoso(any())).thenReturn(response);

        EndosoRequestDto request = new EndosoRequestDto();
        request.setRutActor("800111222");
        request.setNumerosFactura(List.of("FAC-001"));

        mockMvc.perform(post("/negociaciones/NEG-001/endosos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idNegociacion").value("NEG-001"))
                .andExpect(jsonPath("$.estadoEndoso").value("PENDIENTE"))
                .andExpect(jsonPath("$.facturasEndosadas[0]").value("FAC-001"));
    }

    @Test
    @DisplayName("El idNegociacion del path variable se inyecta en el request")
    void registrarEndoso_pathVariableInyectada() throws Exception {
        EndosoResponseDto response = EndosoResponseDto.builder()
                .idNegociacion("NEG-CUSTOM-123")
                .estadoEndoso("PENDIENTE")
                .facturasEndosadas(List.of())
                .facturasRechazadas(List.of())
                .build();

        when(endosoDescuentoService.registrarEndoso(any())).thenReturn(response);

        EndosoRequestDto request = new EndosoRequestDto();
        request.setRutActor("800111222");
        request.setNumerosFactura(List.of("FAC-001"));

        mockMvc.perform(post("/negociaciones/NEG-CUSTOM-123/endosos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(endosoDescuentoService).registrarEndoso(
                org.mockito.ArgumentMatchers.argThat(r -> "NEG-CUSTOM-123".equals(r.getIdNegociacion())));
    }

    @Test
    @DisplayName("404 — negociación no encontrada")
    void registrarEndoso_404_negociacionNoEncontrada() throws Exception {
        when(endosoDescuentoService.registrarEndoso(any()))
                .thenThrow(new RecursoNoEncontradoException("Negociación no encontrada", "NEGOCIACION_NO_ENCONTRADA"));

        EndosoRequestDto request = new EndosoRequestDto();
        request.setRutActor("800111222");
        request.setNumerosFactura(List.of("FAC-001"));

        mockMvc.perform(post("/negociaciones/NEG-X/endosos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("400 — facturas vacías retorna BAD_REQUEST")
    void registrarEndoso_400_sinFacturas() throws Exception {
        when(endosoDescuentoService.registrarEndoso(any()))
                .thenThrow(new PeticionInvalidaException("Debe indicar al menos una factura", "FACTURAS_REQUERIDAS"));

        EndosoRequestDto request = new EndosoRequestDto();
        request.setRutActor("800111222");
        request.setNumerosFactura(List.of());

        mockMvc.perform(post("/negociaciones/NEG-001/endosos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Estado RECHAZADO cuando todas las facturas son rechazadas")
    void registrarEndoso_estadoRechazado() throws Exception {
        EndosoResponseDto response = EndosoResponseDto.builder()
                .idNegociacion("NEG-001")
                .estadoEndoso("RECHAZADO")
                .facturasEndosadas(List.of())
                .facturasRechazadas(List.of("FAC-001 (no pertenece al PROVEEDOR)"))
                .build();

        when(endosoDescuentoService.registrarEndoso(any())).thenReturn(response);

        EndosoRequestDto request = new EndosoRequestDto();
        request.setRutActor("ACTOR-EQUIVOCADO");
        request.setNumerosFactura(List.of("FAC-001"));

        mockMvc.perform(post("/negociaciones/NEG-001/endosos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoEndoso").value("RECHAZADO"))
                .andExpect(jsonPath("$.facturasRechazadas[0]").value("FAC-001 (no pertenece al PROVEEDOR)"));
    }
}