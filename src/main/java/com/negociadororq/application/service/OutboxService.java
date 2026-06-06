package com.negociadororq.application.service;

import com.negociadororq.application.port.output.OutboxOutputPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxOutputPort outboxPort;

    @Transactional
    public void guardarEvento(String aggregateId, String tipoEvento, Object evento) {
        outboxPort.guardarEvento(aggregateId, tipoEvento, evento);
    }
}
