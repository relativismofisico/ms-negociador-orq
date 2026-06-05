package com.negociadororq.application.controller;

import com.negociadororq.application.controller.dto.AprobacionRequestDto;
import com.negociadororq.application.controller.dto.ConfirmacionOtpRequestDto;
import com.negociadororq.application.service.AprobacionEndosoDescuentoService;
import com.negociadororq.infrastructure.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/negociaciones")
@RequiredArgsConstructor
public class AprobarEndosoDescuentoController {

    private final AprobacionEndosoDescuentoService service;

    // 202 ACCEPTED: operación asíncrona — solo encola el evento OTP en Kafka/Outbox,
    // no garantiza procesamiento inmediato. El cliente debe consultar el estado.
    // ⚠ RIESGO DE COMPATIBILIDAD: clientes que esperan 200 deben actualizarse a aceptar 202
    @PostMapping("/solicitar-otp")
    public ResponseEntity<String> solicitarOtp(@RequestBody AprobacionRequestDto request) {
        service.solicitarOtp(request);
        return ResponseEntity.accepted().body("OTP solicitado");
    }

    // 202 ACCEPTED: operación asíncrona — publica evento de validación a Kafka/Outbox.
    // La respuesta de validación llega por el consumer OtpValidatedListener.
    // ⚠ RIESGO DE COMPATIBILIDAD: clientes que esperan 200 deben actualizarse a aceptar 202
    @PostMapping("/confirmar-otp")
    public ResponseEntity<String> confirmarOtp(
            @RequestBody ConfirmacionOtpRequestDto request,
            HttpServletRequest httpRequest) {
        String ip = IpUtils.obtenerIp(httpRequest);
        request.setIpAprobacion(ip);
        service.confirmarOtp(request);
        return ResponseEntity.accepted().body("Validando OTP...");
    }
}