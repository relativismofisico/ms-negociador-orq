package com.negociadororq.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Genera tokens JWT de prueba firmados con la misma clave que usa el servicio.
 * Ejecutar: mvn -pl . exec:java -Dexec.mainClass=com.negociadororq.util.JwtTestTokenGenerator
 * O bien correr el main directamente desde el IDE.
 */
public class JwtTestTokenGenerator {

    private static final String SECRET_DEV =
            "AF84F1FGllNpNnLG055fdg5hGHJK4KGG5VH5TR5J05JFGGDFDGXVV545J4505G666JFGF2mMY95y";

    public static void main(String[] args) {
        String token = generate("perf-tester", "ADMINISTRADOR", SECRET_DEV);
        System.out.println("\n=== JWT para desarrollo.properties ===");
        System.out.println(token);
        System.out.println("======================================\n");
    }

    public static String generate(String subject, String rol, String secretBase64) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(subject)
                .claim("rol", rol)
                .issuedAt(new Date(now))
                .expiration(new Date(now + 365L * 24 * 60 * 60 * 1000))
                .signWith(key)
                .compact();
    }
}