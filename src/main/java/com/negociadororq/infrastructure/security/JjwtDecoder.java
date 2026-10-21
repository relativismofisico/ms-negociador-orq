package com.negociadororq.infrastructure.security;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.negociadororq.infrastructure.persistence.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Decodificador JWT compatible con el ms-security que genera los tokens.
 *
 * Usa la misma librería (jjwt 0.12.x) y la misma clave secreta para garantizar
 * compatibilidad exacta de firma. Acepta solo HS384, el algoritmo con el que firma
 * ms-security-util: un token con cualquier otro {@code alg} (HS256, HS512, none) se
 * rechaza aunque esté firmado con el secreto correcto.
 *
 * Flujo:
 * 1. jjwt verifica algoritmo (HS384), firma y expiración.
 * 2. Claims se mapean a org.springframework.security.oauth2.jwt.Jwt.
 * 3. Spring Security extrae las autoridades vía JwtRolConverter.
 */
@Slf4j
public class JjwtDecoder implements JwtDecoder {

    /** Vigencia de respaldo (24h) cuando el token no trae claim de expiración. */
    private static final long DEFAULT_EXPIRATION_SECONDS = 24L * 60 * 60;

    private final SecretKey signingKey;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    /**
     * @param secretBase64   clave secreta de firma, en Base64, compartida con ms-security-util
     * @param objectMapper   usado para parsear el header del JWT
     * @param userRepository usado para verificar que el usuario del token siga existiendo
     */
    public JjwtDecoder(String secretBase64, ObjectMapper objectMapper, UserRepository userRepository) {
        // Valida al arrancar (este decoder es un bean): si el secreto falta, quedó sin resolver o no es Base64 de
        // 48 a 63 bytes, la app no arranca.
        this.signingKey = Keys.hmacShaKeyFor(SecretoHmac.decodificar("security.jwt.secret", "JWT_SECRET", secretBase64));
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    @Override
    public Jwt decode(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    // Solo HS384, el algoritmo de la plataforma: se quitan HS256 y HS512; none ya se rechaza
                    // por defecto y los algoritmos asimétricos no aceptan una SecretKey.
                    .sig().remove(Jwts.SIG.HS256).remove(Jwts.SIG.HS512).and()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            log.warn("JWT expirado: {}", ex.getMessage());
            throw new JwtException("Token expirado", ex);
        } catch (io.jsonwebtoken.JwtException ex) {
            log.warn("JWT inválido: {}", ex.getMessage());
            throw new BadJwtException("Token inválido: " + ex.getMessage(), ex);
        }

        // Un refresh token (emitido por ms-security-util, mismo secreto, mismos claims que
        // un access token) nunca es válido como credencial de acceso normal — rechazar SOLO
        // si el claim está presente y vale "refresh": los tokens emitidos antes de que
        // existiera ese claim siguen validando igual, retrocompatible. Este chequeo solo
        // afecta el camino JWT (oauth2ResourceServer) — el de Basic Auth service-to-service
        // no pasa por este decoder.
        boolean isRefreshToken = "refresh".equals(claims.get("typ"));
        if (isRefreshToken) {
            log.warn("Refresh token usado como credencial de acceso");
            throw new BadJwtException("El token es un refresh token, no puede usarse como credencial de acceso.");
        }

        // El usuario del token debe seguir existiendo en la base compartida (dbcompany.user,
        // la misma que administra ms-security-util) — un usuario borrado no debe seguir
        // operando solo porque su JWT no haya expirado todavía. Mismo criterio ya aplicado
        // en ms-security-util.
        if (userRepository.findByUsername(claims.getSubject()).isEmpty()) {
            log.warn("Usuario del token ya no existe: {}", claims.getSubject());
            throw new BadJwtException("El usuario del token ya no existe.");
        }

        Map<String, Object> headers = parseHeaders(token);
        Map<String, Object> claimsMap = new HashMap<>(claims);

        Instant issuedAt;
        if (claims.getIssuedAt() != null) {
            issuedAt = claims.getIssuedAt().toInstant();
        } else {
            issuedAt = Instant.now();
        }

        Instant expiresAt;
        if (claims.getExpiration() != null) {
            expiresAt = claims.getExpiration().toInstant();
        } else {
            expiresAt = Instant.now().plusSeconds(DEFAULT_EXPIRATION_SECONDS);
        }

        return Jwt.withTokenValue(token)
                .headers(h -> h.putAll(headers))
                .claims(c -> c.putAll(claimsMap))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
    }

    private Map<String, Object> parseHeaders(String token) {
        try {
            String headerSegment = token.split("\\.")[0];
            byte[] headerBytes = Base64.getUrlDecoder().decode(headerSegment);
            return objectMapper.readValue(headerBytes, new TypeReference<>() { });
        } catch (Exception ex) {
            log.debug("No se pudieron parsear los headers del JWT: {}", ex.getMessage());
            return Map.of("alg", "HS384", "typ", "JWT");
        }
    }
}

