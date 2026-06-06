package com.negociadororq.application.web.controller;

import com.negociadororq.application.dto.AprobacionRequestDto;
import com.negociadororq.application.dto.ConfirmacionOtpRequestDto;
import com.negociadororq.application.service.AprobacionEndosoDescuentoService;
import com.negociadororq.application.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/negociaciones")
@RequiredArgsConstructor
public class AprobarEndosoDescuentoController {

    private final AprobacionEndosoDescuentoService service;

    @PreAuthorize("@sec.tieneRol(authentication, 'otp')")
    @PostMapping("/solicitar-otp")
    public ResponseEntity<String> solicitarOtp(@RequestBody AprobacionRequestDto request) {
        service.solicitarOtp(request);
        return ResponseEntity.accepted().body("OTP solicitado");
    }

    @PreAuthorize("@sec.tieneRol(authentication, 'otp')")
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
