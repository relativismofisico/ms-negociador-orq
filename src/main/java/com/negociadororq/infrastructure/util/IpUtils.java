package com.negociadororq.infrastructure.util;

import jakarta.servlet.http.HttpServletRequest;

public class IpUtils {

    public static String obtenerIp(HttpServletRequest request) {

        String header = request.getHeader("X-Forwarded-For");

        if (header != null && !header.isEmpty() && !"unknown".equalsIgnoreCase(header)) {
            // Puede venir: cliente, proxy1, proxy2
            return header.split(",")[0].trim();
        }

        header = request.getHeader("X-Real-IP");

        if (header != null && !header.isEmpty() && !"unknown".equalsIgnoreCase(header)) {
            return header;
        }

        return request.getRemoteAddr();
    }
}
