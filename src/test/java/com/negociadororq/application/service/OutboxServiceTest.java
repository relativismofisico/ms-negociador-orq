package com.negociadororq.application.service;

import com.negociadororq.application.port.output.OutboxOutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxService — delegación de eventos al port")
class OutboxServiceTest {

    @Mock
    private OutboxOutputPort outboxPort;

    @InjectMocks
    private OutboxService service;

    @Test
    @DisplayName("guardarEvento delega al port con los mismos parámetros")
    void guardarEvento_delegaAlPort() {
        Object evento = new Object();

        service.guardarEvento("AGG-001", "EVENTO_TIPO", evento);

        verify(outboxPort).guardarEvento("AGG-001", "EVENTO_TIPO", evento);
    }

    @Test
    @DisplayName("guardarEvento acepta aggregateId null sin errores")
    void guardarEvento_aggregateIdNulo() {
        service.guardarEvento(null, "TIPO", "datos");

        verify(outboxPort).guardarEvento(null, "TIPO", "datos");
    }

    @Test
    @DisplayName("guardarEvento acepta objeto evento complejo")
    void guardarEvento_eventoComplejo() {
        record Payload(String campo, int numero) { }
        Payload payload = new Payload("test", 42);

        service.guardarEvento("ID-123", "TOPIC-X", payload);

        verify(outboxPort).guardarEvento("ID-123", "TOPIC-X", payload);
    }
}