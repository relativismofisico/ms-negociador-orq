package com.negociadororq.infrastructure.security;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Fase 28d (J-06, decisión de John): rotación sin cortes de las cuentas de servicio (Basic Auth, ms-liquidador-neg).
 * Acepta la contraseña si coincide con el hash ACTUAL o, durante una rotación, con el ANTERIOR
 * ({@code *_SERVICE_PASSWORD_HASH_ANTERIOR}). Un usuario que no es cuenta de servicio no lo atiende (devuelve null) y
 * sigue al proveedor de usuarios de la base. Registra cuál coincidió, sin valores: contador por cuenta y hash
 * ({@link #autenticaciones}, actual|anterior) y log; mientras el anterior esté definido, alerta
 * WARN periódica {@code [CUENTA-SERVICIO-HASH-ANTERIOR-ACTIVO]}. Procedimiento en rotacion-cuentas-servicio.md.
 */
@Slf4j
public class CuentaServicioAuthenticationProvider implements AuthenticationProvider {

    private final SecurityProperties propiedades;
    private final PasswordEncoder encoder;
    /** Autenticaciones por "cuenta|actual" o "cuenta|anterior" (sin valores). */
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.LongAdder> conteo =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * @param propiedades cuentas de servicio
     * @param encoder     bcrypt
     */
    public CuentaServicioAuthenticationProvider(SecurityProperties propiedades, PasswordEncoder encoder) {
        this.propiedades = propiedades;
        this.encoder = encoder;
    }

    /**
     * @param cuenta usuario de la cuenta de servicio
     * @param hash   actual o anterior
     * @return cuántas veces se autenticó con ese hash desde que arrancó
     */
    public long autenticaciones(String cuenta, String hash) {
        java.util.concurrent.atomic.LongAdder n = conteo.get(cuenta + "|" + hash);
        if (n == null) {
            return 0L;
        }
        return n.sum();
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String usuario = authentication.getName();
        SecurityProperties.ServiceCredential cuenta = propiedades.buscarPorUsuario(usuario);
        if (cuenta == null) {
            return null;
        }
        Object credencial = authentication.getCredentials();
        if (credencial == null) {
            throw new BadCredentialsException("Credenciales de servicio inválidas.");
        }
        String clave = credencial.toString();
        String coincidio;
        if (encoder.matches(clave, cuenta.getPassword())) {
            coincidio = "actual";
        } else if (cuenta.tieneAnterior() && encoder.matches(clave, cuenta.getPasswordAnterior())) {
            coincidio = "anterior";
            log.warn("[CUENTA-SERVICIO-HASH-ANTERIOR-USADO] la cuenta de servicio {} se autenticó con el hash anterior",
                    usuario);
        } else {
            throw new BadCredentialsException("Credenciales de servicio inválidas.");
        }
        conteo.computeIfAbsent(usuario + "|" + coincidio, k -> new java.util.concurrent.atomic.LongAdder()).increment();
        List<SimpleGrantedAuthority> roles = cuenta.getRoles().stream().map(SimpleGrantedAuthority::new).toList();
        return new UsernamePasswordAuthenticationToken(new User(cuenta.getUsername(), "", roles), null, roles);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    /** Alerta periódica mientras haya una rotación en curso (el hash anterior sigue definido). */
    @Scheduled(fixedDelayString = "${security.cuentas-servicio.alerta-anterior-ms:3600000}",
            initialDelayString = "${security.cuentas-servicio.alerta-anterior-inicial-ms:60000}")
    public void alertarHashAnteriorActivo() {
        propiedades.getServiceCredentials().forEach((nombre, cuenta) -> {
            if (cuenta.tieneAnterior()) {
                log.warn("[CUENTA-SERVICIO-HASH-ANTERIOR-ACTIVO] la cuenta de servicio {} acepta todavía el hash "
                        + "anterior: termina la rotación y retira la variable {}", nombre,
                        cuenta.getPasswordAnteriorVariable());
            }
        });
    }
}
