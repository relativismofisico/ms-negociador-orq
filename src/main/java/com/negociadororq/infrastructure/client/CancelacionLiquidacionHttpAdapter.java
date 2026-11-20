package com.negociadororq.infrastructure.client;

import java.time.Duration;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKey;

import com.negociadororq.application.exception.ServicioDependienteNoDisponibleException;
import com.negociadororq.application.port.output.CancelacionLiquidacionOutputPort;
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
 * Fase 28d (A4): {@code POST /internal/liquidaciones/cancelacion} en ms-liquidador-neg con el JWT de servicio de la
 * plataforma (secreto act-as-operario, HS384, rol ADMINISTRADOR, subject {@code ms-negociador-orq}), como la validación
 * del OTP en ms-security-util.
 * <ul>
 *   <li>Liquidador caído, timeout, 5xx o sin URL configurada: {@link ServicioDependienteNoDisponibleException}; el
 *       seguimiento lo reintenta en el siguiente ciclo y, pasado el tope, la deja en revisión manual.</li>
 *   <li>4xx (secreto, subject no autorizado, cuerpo inválido): ERROR con el código HTTP y la misma excepción (es
 *       configuración: no se decide nada con una respuesta que no es del liquidador).</li>
 * </ul>
 */
@Slf4j
@Component
public class CancelacionLiquidacionHttpAdapter implements CancelacionLiquidacionOutputPort {

    static final String RUTA = "/internal/liquidaciones/cancelacion";
    private static final String NO_DISPONIBLE = "El liquidador no está disponible para cancelar la solicitud.";
    private static final Duration VIDA_TOKEN = Duration.ofMinutes(2);
    private static final ParameterizedTypeReference<Map<String, Object>> RESPUESTA =
            new ParameterizedTypeReference<>() { };

    private final String baseUrl;
    private final String secreto;
    private final RestClient restClient;
    private SecretKey clave;

    /**
     * @param baseUrl URL base de ms-liquidador-neg ({@code liquidacion.cancelacion.base-url}, sin valor por defecto)
     * @param secreto secreto de servicio, Base64 ({@code security.act-as-operario-service-secret})
     * @param timeout timeout de conexión y lectura ({@code liquidacion.cancelacion.timeout}, 5 s)
     */
    public CancelacionLiquidacionHttpAdapter(@Value("${liquidacion.cancelacion.base-url:}") final String baseUrl,
            @Value("${security.act-as-operario-service-secret:}") final String secreto,
            @Value("${liquidacion.cancelacion.timeout:PT5S}") final Duration timeout) {
        this.baseUrl = baseUrl;
        this.secreto = secreto;
        final SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeout);
        fabrica.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(fabrica).build();
    }

    @Override
    public Resultado cancelar(final String negociacionId, final String cufe) {
        if (baseUrl == null || baseUrl.isBlank()) {
            log.error("[LIQUIDACION-CANCELACION] falta liquidacion.cancelacion.base-url (LIQUIDADOR_BASE_URL): no se puede "
                    + "cancelar la solicitud de la negociación {}", negociacionId);
            throw new ServicioDependienteNoDisponibleException(NO_DISPONIBLE);
        }
        final Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("negociacionId", negociacionId);
        cuerpo.put("cufe", cufe);
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
            log.error("[LIQUIDACION-CANCELACION] el liquidador rechazó la cancelación de la negociación {}: HTTP {} "
                    + "(revisar el secreto de servicio y liquidacion.cancelacion.servicios)", negociacionId,
                    e.getStatusCode().value());
            throw new ServicioDependienteNoDisponibleException(NO_DISPONIBLE);
        } catch (RestClientException e) {
            log.warn("[LIQUIDACION-CANCELACION] liquidador no disponible al cancelar la negociación {}: {}", negociacionId,
                    e.getClass().getSimpleName());
            throw new ServicioDependienteNoDisponibleException(NO_DISPONIBLE);
        }
        if (respuesta == null || !(respuesta.get("estado") instanceof String estado)) {
            throw new ServicioDependienteNoDisponibleException(NO_DISPONIBLE);
        }
        try {
            return Resultado.valueOf(estado);
        } catch (IllegalArgumentException e) {
            log.error("[LIQUIDACION-CANCELACION] respuesta desconocida del liquidador para la negociación {}: {}",
                    negociacionId, estado);
            throw new ServicioDependienteNoDisponibleException(NO_DISPONIBLE);
        }
    }

    String token() {
        final Date ahora = new Date();
        return Jwts.builder()
                .claim("rol", ValidacionOtpHttpAdapter.ROL)
                .subject(ValidacionOtpHttpAdapter.SUBJECT)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + VIDA_TOKEN.toMillis()))
                .signWith(clave(), Jwts.SIG.HS384)
                .compact();
    }

    private synchronized SecretKey clave() {
        if (clave == null) {
            clave = Keys.hmacShaKeyFor(ValidacionOtpHttpAdapter.decodificar(secreto));
        }
        return clave;
    }
}
