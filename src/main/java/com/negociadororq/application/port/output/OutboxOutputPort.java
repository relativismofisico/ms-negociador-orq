package com.negociadororq.application.port.output;

public interface OutboxOutputPort {
    void guardarEvento(String aggregateId, String tipoEvento, Object evento);
}
