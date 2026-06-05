package com.negociadororq.infrastructure.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Decodificador JWT compatible con el ms-security que genera los tokens.
 *
 * Usa la misma librería (jjwt 0.12.x) y la misma clave secreta para garantizar
 * compatibilidad exacta de algoritmo y firma. No asume el algoritmo — lo extrae
 * del header del token para tolerancia ante cambios futuros del generador.
 *
 * Flujo:
 * 1. jjwt verifica firma y expiración.
 * 2. Claims se mapean a org.springframework.security.oauth2.jwt.Jwt.
 * 3. Spring Security extrae las autoridades vía JwtRolConverter.
 */
@Slf4j
public class JjwtDecoder implements JwtDecoder {

    private final SecretKey signingKey;
    private final ObjectMapper objectMapper;

    public JjwtDecoder(String secretBase64, ObjectMapper objectMapper) {
        byte[] keyBytes = Decoders.BASE64.decode(secretBase64);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.objectMapper = objectMapper;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        Claims claims;
        try {
            claims = Jwts.parser()
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

        Map<String, Object> headers = parseHeaders(token);
        Map<String, Object> claimsMap = new HashMap<>(claims);

        Instant issuedAt = claims.getIssuedAt() != null
                ? claims.getIssuedAt().toInstant() : Instant.now();
        Instant expiresAt = claims.getExpiration() != null
                ? claims.getExpiration().toInstant() : Instant.now().plusSeconds(86400);

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
            return objectMapper.readValue(headerBytes, new TypeReference<>() {});
        } catch (Exception ex) {
            log.debug("No se pudieron parsear los headers del JWT: {}", ex.getMessage());
            return Map.of("alg", "HS384", "typ", "JWT");
        }
    }
}