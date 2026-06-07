package com.negociadororq.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Generador de JWT de prueba compatible con JjwtDecoder del microservicio (HS384).
 * Llamado desde karate-config-dev.js y karate-config-qa.js via Java.type().
 */
public final class JwtTestHelper {

    private JwtTestHelper() {}

    public static String generate(String subject, String rol, String base64Secret) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        return Jwts.builder()
                .subject(subject)
                .claim("rol", rol)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key, Jwts.SIG.HS384)
                .compact();
    }

    public static String generateExpired(String subject, String rol, String base64Secret) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        return Jwts.builder()
                .subject(subject)
                .claim("rol", rol)
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000L))
                .expiration(new Date(System.currentTimeMillis() - 1_000L))
                .signWith(key, Jwts.SIG.HS384)
                .compact();
    }
}