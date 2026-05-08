package com.negociadororq.application.service;

public interface ReversionCuposService {

    void revertirPorFactura(String numeroFactura, String actorId, String motivo);
}
