package com.negociadororq.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.security.oauth2.jwt.BadJwtException
import spock.lang.Specification

import javax.crypto.SecretKey
import java.util.Base64

class JjwtDecoderSpec extends Specification {

    static final String SECRET =
            "AF84F1FGllNpNnLG055fdg5hGHJK4KGG5VH5TR5J05JFGGDFDGXVV545J4505G666JFGF2mMY95y"
    static final SecretKey SIGN_KEY = Keys.hmacShaKeyFor(Base64.decoder.decode(SECRET))

    JjwtDecoder decoder = new JjwtDecoder(SECRET, new ObjectMapper())

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
        def token = buildToken("access")

        when:
        def jwt = decoder.decode(token)

        then:
        jwt.getSubject() == "usuario@test.com"
    }

    def "decode acepta un token legacy sin claim 'typ' (retrocompatibilidad)"() {
        given:
        def token = buildToken(null)

        when:
        def jwt = decoder.decode(token)

        then:
        jwt.getSubject() == "usuario@test.com"
    }

    def "decode lanza BadJwtException para un refresh token usado como access token"() {
        given:
        def token = buildToken("refresh")

        when:
        decoder.decode(token)

        then:
        thrown(BadJwtException)
    }
}
