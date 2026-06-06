package com.negociadororq.application.service.notificacion;

import com.negociadororq.application.service.notificacion.impl.NegociacionCuerpoBuilder;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.Operacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CuerpoNotificacionFactory — construcción de cuerpos de notificación")
class CuerpoNotificacionFactoryTest {

    private CuerpoNotificacionFactory factory;

    @BeforeEach
    void setUp() {
        List<CuerpoNotificacionBuilder<?>> builders = List.of(new NegociacionCuerpoBuilder());
        factory = new CuerpoNotificacionFactory(builders);
        factory.init();
    }

    @Test
    @DisplayName("init() registra los builders en el mapa por tipo")
    void init_registraBuilders() {
        Negociacion neg = buildNegociacion();

        String resultado = factory.construir(neg);

        assertThat(resultado).isNotBlank();
    }

    @Test
    @DisplayName("construir() usa el builder correcto para Negociacion")
    void construir_usaBuilderNegociacion() {
        Negociacion neg = buildNegociacion();

        String cuerpo = factory.construir(neg);

        assertThat(cuerpo).contains("NEG-TEST");
        assertThat(cuerpo).contains("FACTORING");
    }

    @Test
    @DisplayName("construir() retorna mensaje de error para tipo sin builder registrado")
    void construir_tipoSinBuilder_retornaMensajeError() {
        String resultado = factory.construir("un string sin builder");

        assertThat(resultado).contains("No existe builder");
        assertThat(resultado).contains("String");
    }

    @Test
    @DisplayName("construir() para objeto nulo inicia correctamente la búsqueda por clase")
    void construir_tipoInteger_retornaMensajeError() {
        String resultado = factory.construir(Integer.valueOf(42));

        assertThat(resultado).contains("No existe builder");
    }

    private Negociacion buildNegociacion() {
        return Negociacion.builder()
                .id("NEG-TEST")
                .tipoOperacion(Operacion.builder().tipo("FACTORING").clase("NORMAL").build())
                .totalNegociado(new BigDecimal("5000"))
                .facturas(List.of(FacturaNegociada.builder().numeroFactura("F-1").build()))
                .build();
    }
}