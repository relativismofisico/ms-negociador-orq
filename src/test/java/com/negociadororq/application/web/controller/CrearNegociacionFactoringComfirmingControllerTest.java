package com.negociadororq.application.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.negociadororq.application.dto.NegociacionRequestDto;
import com.negociadororq.application.dto.NegociacionResponseDto;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.service.NegociacionFactoringComfirmingService;
import com.negociadororq.domain.model.Operacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = CrearNegociacionFactoringComfirmingController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@DisplayName("CrearNegociacionFactoringComfirmingController — endpoint POST /negociar/factura")
class CrearNegociacionFactoringComfirmingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NegociacionFactoringComfirmingService negociacionFactoringComfirmingService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("201 CREATED — crea negociación y retorna DTO con estado")
    void crearNegociacion_201() throws Exception {
        NegociacionResponseDto response = new NegociacionResponseDto();
        response.setIdNegociacion("NEG-001");
        response.setEstado("PENDIENTE");
        response.setTotalNegociado(new BigDecimal("5000"));
        response.setFacturas(List.of());
        response.setErrores(Collections.emptyList());
        response.setFondeadoresAsignados(List.of());

        when(negociacionFactoringComfirmingService.crearNegociacion(any())).thenReturn(response);

        NegociacionRequestDto request = new NegociacionRequestDto();
        request.setRutEmpresa("800111222");
        request.setRolEmpresa("PROVEEDOR");
        request.setRutOperador("900333444");
        request.setTipoOperacion(Operacion.builder().tipo("FACTORING").clase("NORMAL").build());
        request.setFacturas(List.of());

        mockMvc.perform(post("/negociar/factura")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idNegociacion").value("NEG-001"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    @DisplayName("404 — operador no encontrado propaga la excepción")
    void crearNegociacion_404_operadorNoEncontrado() throws Exception {
        when(negociacionFactoringComfirmingService.crearNegociacion(any()))
                .thenThrow(new RecursoNoEncontradoException("Operador no encontrado", "OPERADOR_NO_ENCONTRADO"));

        NegociacionRequestDto request = new NegociacionRequestDto();
        request.setRutEmpresa("800111222");
        request.setRolEmpresa("PROVEEDOR");
        request.setRutOperador("OPERADOR-X");
        request.setTipoOperacion(Operacion.builder().tipo("FACTORING").clase("NORMAL").build());
        request.setFacturas(List.of());

        mockMvc.perform(post("/negociar/factura")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("500 — RuntimeException propaga como error interno")
    void crearNegociacion_500_errorInterno() throws Exception {
        when(negociacionFactoringComfirmingService.crearNegociacion(any()))
                .thenThrow(new RuntimeException("Error inesperado"));

        NegociacionRequestDto request = new NegociacionRequestDto();
        request.setRutEmpresa("800111222");
        request.setRolEmpresa("PROVEEDOR");
        request.setRutOperador("op-rut");
        request.setTipoOperacion(Operacion.builder().tipo("FACTORING").clase("NORMAL").build());
        request.setFacturas(List.of());

        mockMvc.perform(post("/negociar/factura")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("400 — body malformado retorna BAD_REQUEST")
    void crearNegociacion_400_bodyMalformado() throws Exception {
        mockMvc.perform(post("/negociar/factura")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Respuesta incluye lista de errores cuando hay facturas rechazadas")
    void crearNegociacion_conErrores() throws Exception {
        NegociacionResponseDto response = new NegociacionResponseDto();
        response.setIdNegociacion("NEG-002");
        response.setEstado("RECHAZADA");
        response.setTotalNegociado(BigDecimal.ZERO);
        response.setFacturas(List.of());
        response.setFondeadoresAsignados(List.of());
        response.setErrores(List.of("Factura sin cupo"));

        when(negociacionFactoringComfirmingService.crearNegociacion(any())).thenReturn(response);

        NegociacionRequestDto request = new NegociacionRequestDto();
        request.setRutEmpresa("800111222");
        request.setRolEmpresa("PROVEEDOR");
        request.setRutOperador("op-rut");
        request.setTipoOperacion(Operacion.builder().tipo("FACTORING").clase("NORMAL").build());
        request.setFacturas(List.of());

        mockMvc.perform(post("/negociar/factura")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.errores[0]").value("Factura sin cupo"));
    }
}