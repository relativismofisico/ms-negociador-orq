package com.negociadororq.infrastructure.client;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKey;

import com.negociadororq.application.exception.ServicioDependienteNoDisponibleException;
import com.negociadororq.application.port.output.ValidacionOtpOutputPort;
import com.negociadororq.domain.model.ValidacionOtp;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link ValidacionOtpOutputPort} por HTTP contra ms-security-util (Fase 28d, H b), con el JWT de servicio de la
 * plataforma (secreto act-as-operario, HS384, rol ADMINISTRADOR, subject {@code ms-negociador-orq}), como
 * ms-notificaciones-util al leer el código por referencia.
 *
 * <ul>
 *   <li>Security caído, timeout o 5xx: {@link ServicioDependienteNoDisponibleException} (503); quien llama no cambia el
 *       pendiente y el reintento usa la misma clave de idempotencia.</li>
 *   <li>4xx (configuración: secreto, subject no autorizado, cuerpo inválido): error con el código HTTP en el log, sin
 *       el cuerpo.</li>
 *   <li>El código no se escribe en ningún log; la URL base tampoco.</li>
 * </ul>
 */
@Slf4j
@Component
public class ValidacionOtpHttpAdapter implements ValidacionOtpOutputPort {

    static final String SUBJECT = "ms-negociador-orq";
    static final String ROL = "ADMINISTRADOR";
    static final String RUTA = "/auth/internal/otp/validacion";
    static final int MIN_BYTES = 48;
    static final int MAX_BYTES = 63;
    private static final Duration VIDA_TOKEN = Duration.ofSeconds(60);
    private static final ParameterizedTypeReference<Map<String, Object>> RESPUESTA =
            new ParameterizedTypeReference<>() { };
    private static final String NO_DISPONIBLE =
            "El servicio de seguridad no está disponible en este momento. Intente de nuevo.";

    private final String baseUrl;
    private final String secreto;
    private final RestClient restClient;
    private SecretKey clave;

    /**
     * @param baseUrl URL base de ms-security-util ({@code otp.validacion.security-base-url})
     * @param secreto secreto de servicio, Base64 ({@code security.act-as-operario-service-secret})
     * @param timeout timeout de conexión y lectura ({@code otp.validacion.timeout}, 3 s)
     */
    public ValidacionOtpHttpAdapter(@Value("${otp.validacion.security-base-url:}") final String baseUrl,
            @Value("${security.act-as-operario-service-secret:}") final String secreto,
            @Value("${otp.validacion.timeout:PT3S}") final Duration timeout) {
        this.baseUrl = baseUrl;
        this.secreto = secreto;
        final SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeout);
        fabrica.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(fabrica).build();
    }

    @Override
    public ValidacionOtp.Resultado validar(final ValidacionOtp.Solicitud solicitud) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("[OTP-HTTP] falta otp.validacion.security-base-url (SECURITY_UTIL_BASE_URL)");
        }
        final Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("idNegociacion", solicitud.idNegociacion());
        cuerpo.put("rutEmpresa", solicitud.rutEmpresa());
        cuerpo.put("rutOperador", solicitud.rutOperador());
        cuerpo.put("otp", solicitud.otp());
        cuerpo.put("ipAprobacion", solicitud.ipAprobacion());
        cuerpo.put("claveIdempotencia", solicitud.claveIdempotencia());
        final Map<String, Object> respuesta;
        try {
            respuesta = restClient.post()
                    .uri(baseUrl + RUTA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(token()))
                    .body(cuerpo)
                    .retrieve()
                    .body(RESPUESTA);
        } catch (HttpClientErrorException e) {
            log.error("[OTP-HTTP] security rechazó la validación de la negociación {} (clave {}): HTTP {} (revisar el "
                    + "secreto de servicio y security.otp.servicios-validacion)", solicitud.idNegociacion(),
                    solicitud.claveIdempotencia(), e.getStatusCode().value());
            throw new IllegalStateException("security rechazó la validación del OTP: HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            // Caído, timeout o 5xx (HttpServerErrorException es RestClientException): nada cambió.
            log.warn("[OTP-HTTP] security no disponible al validar la negociación {} (clave {}): {}",
                    solicitud.idNegociacion(), solicitud.claveIdempotencia(), e.getClass().getSimpleName());
            throw new ServicioDependienteNoDisponibleException(NO_DISPONIBLE);
        }
        if (respuesta == null) {
            throw new ServicioDependienteNoDisponibleException(NO_DISPONIBLE);
        }
        return new ValidacionOtp.Resultado(Boolean.TRUE.equals(respuesta.get("valido")),
                texto(respuesta.get("motivo")), entero(respuesta.get("intentosRestantes")),
                texto(respuesta.get("otpId")), fecha(respuesta.get("fechaValidacion")),
                instante(respuesta.get("instanteValidacion")));
    }

    String token() {
        final Date ahora = new Date();
        return Jwts.builder()
                .claim("rol", ROL)
                .subject(SUBJECT)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + VIDA_TOKEN.toMillis()))
                .signWith(clave(), Jwts.SIG.HS384)
                .compact();
    }

    private synchronized SecretKey clave() {
        if (clave == null) {
            clave = Keys.hmacShaKeyFor(decodificar(secreto));
        }
        return clave;
    }

    static byte[] decodificar(final String valor) {
        final String ayuda = " Defina ACT_AS_OPERARIO_SERVICE_SECRET con un valor Base64 de " + MIN_BYTES + " a "
                + MAX_BYTES + " bytes, igual en todos los servicios del ambiente.";
        if (valor == null || valor.isBlank() || valor.startsWith("${")) {
            throw new IllegalStateException("[OTP-HTTP] falta el secreto de servicio." + ayuda);
        }
        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(valor.trim().getBytes(StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("[OTP-HTTP] el secreto de servicio no es Base64." + ayuda, e);
        }
        if (bytes.length < MIN_BYTES || bytes.length > MAX_BYTES) {
            throw new IllegalStateException("[OTP-HTTP] el secreto de servicio tiene " + bytes.length + " bytes." + ayuda);
        }
        return bytes;
    }

    private static String texto(final Object valor) {
        if (valor == null) {
            return null;
        }
        return valor.toString();
    }

    private static int entero(final Object valor) {
        if (valor instanceof Number numero) {
            return numero.intValue();
        }
        return 0;
    }

    private static LocalDateTime fecha(final Object valor) {
        if (valor == null) {
            return null;
        }
        return LocalDateTime.parse(valor.toString());
    }

    private static Instant instante(final Object valor) {
        if (valor == null) {
            return null;
        }
        return Instant.parse(valor.toString());
    }
}
