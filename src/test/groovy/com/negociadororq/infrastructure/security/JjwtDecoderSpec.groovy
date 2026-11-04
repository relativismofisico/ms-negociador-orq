package com.negociadororq.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.negociadororq.infrastructure.persistence.entity.UserEntity
import com.negociadororq.infrastructure.persistence.repository.UserRepository
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.dao.QueryTimeoutException
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.transaction.CannotCreateTransactionException
import spock.lang.Specification

import javax.crypto.SecretKey
import java.util.Base64

class JjwtDecoderSpec extends Specification {

    // Clave aleatoria de 48 bytes generada en el test: nunca un secreto literal.
    static final String SECRET = Base64.encoder.encodeToString(new byte[48].tap { new java.security.SecureRandom().nextBytes(it) })
    static final SecretKey SIGN_KEY = Keys.hmacShaKeyFor(Base64.decoder.decode(SECRET))

    UserRepository userRepository = Mock()

    JjwtDecoder decoder = new JjwtDecoder(SECRET, new ObjectMapper(), userRepository)

    private String buildToken(String typ) {
        def builder = Jwts.builder()
                .subject("usuario@test.com")
                .claim("rol", "ADMINISTRADOR")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86_400_000L))
        if (typ != null) {
            builder.claim("typ", typ)
        }
        builder.signWith(SIGN_KEY).compact()
    }

    def "decode acepta un token con typ=access"() {
        given:
        userRepository.findByUsername("usuario@test.com") >> Optional.of(new UserEntity())
        def token = buildToken("access")

        when:
        def jwt = decoder.decode(token)

        then:
        jwt.getSubject() == "usuario@test.com"
    }

    def "decode acepta un token legacy sin claim 'typ' (retrocompatibilidad)"() {
        given:
        userRepository.findByUsername("usuario@test.com") >> Optional.of(new UserEntity())
        def token = buildToken(null)

        when:
        def jwt = decoder.decode(token)

        then:
        jwt.getSubject() == "usuario@test.com"
    }

    def "un token malformado da BadJwtException sin el texto de jjwt en el mensaje: queda como causa"() {
        when:
        decoder.decode("no.es.un-jwt")

        then:
        def ex = thrown(BadJwtException)
        ex.message == "Token inválido."
        ex.cause instanceof io.jsonwebtoken.JwtException
    }

    def "un token expirado da BadJwtException (401), no una JwtException generica (que Spring relanza como 500)"() {
        given:
        def token = Jwts.builder()
                .subject("usuario@test.com")
                .claim("rol", "ADMINISTRADOR")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000L))
                .expiration(new Date(System.currentTimeMillis() - 60_000L))
                .signWith(SIGN_KEY).compact()

        when:
        decoder.decode(token)

        then:
        def ex = thrown(BadJwtException)
        ex.cause instanceof io.jsonwebtoken.ExpiredJwtException
        0 * userRepository._
    }

    def "decode lanza BadJwtException para un refresh token usado como access token"() {
        given:
        def token = buildToken("refresh")

        when:
        decoder.decode(token)

        then:
        thrown(BadJwtException)
    }

    def "decode lanza BadJwtException cuando el usuario del token ya no existe en BD"() {
        given:
        userRepository.findByUsername("usuario@test.com") >> Optional.empty()
        def token = buildToken("access")

        when:
        decoder.decode(token)

        then:
        thrown(BadJwtException)
    }

    def "el constructor impide arrancar si el secreto #caso"() {
        when:
        new JjwtDecoder(valor, new ObjectMapper(), userRepository)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("JWT_SECRET")

        where:
        caso                                                       | valor
        "falta"                                                    | null
        "quedó como texto sin resolver (@ConfigurationProperties)" | '${JWT_SECRET}'
        "mide 64 bytes"                                            | Base64.encoder.encodeToString(new byte[64])
    }

    def "decode acepta un token HS384 firmado con el secreto compartido"() {
        given:
        userRepository.findByUsername("usuario@test.com") >> Optional.of(new UserEntity())
        expect:
        decoder.decode(tokenCon("HS384", "HmacSHA384", Base64.decoder.decode(SECRET))).subject == "usuario@test.com"
    }

    def "decode rechaza (401) un token #caso"() {
        given:
        userRepository.findByUsername("usuario@test.com") >> Optional.of(new UserEntity())
        when:
        decoder.decode(tokenCon(alg, hmac, Base64.decoder.decode(SECRET)))

        then:
        thrown(BadJwtException)

        where:
        caso                                        | alg     | hmac
        "HS256 firmado con el mismo secreto"        | "HS256" | "HmacSHA256"
        "HS512 firmado a mano con el mismo secreto" | "HS512" | "HmacSHA512"
        "alg=none sin firma"                        | "none"  | null
        "con header HS256 y firma HS384 válida"     | "HS256" | "HmacSHA384"
        "con header RS256 y firma HMAC"             | "RS256" | "HmacSHA384"
    }

    /**
     * Arma un JWT a mano para probar el algoritmo: header con {@code alg} y firma con {@code hmac} sobre la clave dada
     * (sin firma si {@code hmac} es null). Así se puede firmar HS512 con una clave de 48 bytes, que jjwt no permite, y
     * falsificar el header.
     */
    private static String tokenCon(String alg, String hmac, byte[] clave, Map extra = [:]) {
        def enc = Base64.urlEncoder.withoutPadding()
        long ahora = System.currentTimeMillis().intdiv(1000L)
        Map claims = [sub: "usuario@test.com", rol: "ADMINISTRADOR", iat: ahora, exp: ahora + 600] + extra
        String json = "{" + claims.collect { k, v -> "\"${k}\":" + (v instanceof Number ? v : "\"${v}\"") }.join(",") + "}"
        String header = enc.encodeToString("{\"alg\":\"${alg}\",\"typ\":\"JWT\"}".getBytes("UTF-8"))
        String payload = enc.encodeToString(json.getBytes("UTF-8"))
        String firma = ""
        if (hmac != null) {
            def mac = javax.crypto.Mac.getInstance(hmac)
            mac.init(new javax.crypto.spec.SecretKeySpec(clave, hmac))
            firma = enc.encodeToString(mac.doFinal("${header}.${payload}".getBytes("UTF-8")))
        }
        "${header}.${payload}.${firma}"
    }

    def "BD caída al comprobar el usuario (#falla.class.simpleName): ValidacionTokenNoDisponibleException, no BadJwtException (401)"() {
        given:
        userRepository.findByUsername("usuario@test.com") >> { throw falla }

        when:
        decoder.decode(buildToken("access"))

        then:
        def ex = thrown(ValidacionTokenNoDisponibleException)
        ex.cause.is(falla)
        !(ex instanceof org.springframework.security.oauth2.jwt.JwtException)

        where:
        falla << [new CannotGetJdbcConnectionException("detalle-interno"),
                  new QueryTimeoutException("detalle-interno"),
                  new CannotCreateTransactionException("detalle-interno")]
    }
}
