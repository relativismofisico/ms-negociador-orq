# JWT + Basic Auth Authentication Implementation

## Resumen

Se ha implementado una estrategia de autenticación híbrida que permite:
- **JWT** para usuarios normales (frontend/API)
- **Basic Auth** para comunicación servicio-a-servicio (ms-liquidador-neg)

Ambos mecanismos coexisten sin interferencias, permitiendo que ms-liquidador-neg autentique cuando llama a endpoints de ms-negociador-orq.

---

## Cambios en ms-negociador-orq

### 1. SecurityConfig.java
```java
// Antes: .httpBasic(AbstractHttpConfigurer::disable)
// Después: .httpBasic(Customizer.withDefaults())

@Bean
public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder, AuthenticationManager authenticationManager) throws Exception {
    http
        .authenticationManager(authenticationManager)  // ← Clave para Basic Auth
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // ... resto de configuración
        .httpBasic(Customizer.withDefaults())
```

**Cambios clave:**
- Agregado parámetro `AuthenticationManager authenticationManager`
- Agregada línea `.authenticationManager(authenticationManager)` ANTES de sessionManagement
- Cambiado de `disable()` a `Customizer.withDefaults()` en httpBasic()

### 2. SecurityProperties.java
```java
private Map<String, ServiceCredential> serviceCredentials = new HashMap<>();

@Getter @Setter
public static class ServiceCredential {
    private String username;
    private String password;
}
```

Permite configurar credenciales de servicios en application.yml.

### 3. ServiceCredentialsUserDetailsService.java (NUEVO)
```java
@Service
@RequiredArgsConstructor
public class ServiceCredentialsUserDetailsService implements UserDetailsService {
    
    private final SecurityProperties securityProperties;
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SecurityProperties.ServiceCredential credential = securityProperties.getServiceCredentials()
            .get(username);
        
        if (credential == null) {
            throw new UsernameNotFoundException("Service account not found: " + username);
        }
        
        // Asignar autoridad dinámicamente: ms-liquidador-neg → SCOPE_ms-liquidador-neg
        String scope = "SCOPE_" + username;
        return User.builder()
            .username(username)
            .password(credential.getPassword())  // Hash BCrypt en application.yml
            .authorities(Collections.singleton(new SimpleGrantedAuthority(scope)))
            .build();
    }
}
```

**Responsabilidad:** Cargar credenciales de application.yml y asignar autoridades SCOPE.

### 4. Beans de AuthenticationManager
```java
@Bean
public BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}

@Bean
public DaoAuthenticationProvider daoAuthenticationProvider(
        UserDetailsService userDetailsService,
        BCryptPasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return provider;
}

@Bean
public AuthenticationManager authenticationManager(
        HttpSecurity http,
        DaoAuthenticationProvider daoAuthenticationProvider) throws Exception {
    return http.getSharedObject(AuthenticationManagerBuilder.class)
        .authenticationProvider(daoAuthenticationProvider)
        .build();
}
```

**Responsabilidad:** Crear el AuthenticationManager que httpBasic() usa.

### 5. application.yml
```yaml
security:
  service-credentials:
    ms-liquidador-neg:
      username: ms-liquidador-neg
      password: $2a$10$slPvTTm1S5hy8zyL0X8Ige5h1KwHTvG4/Hln6Yx2vU8TZ5PBMqKha
```

**Nota:** La contraseña es un hash BCrypt. El hash anterior corresponde a `liquidador-secret`.

### 6. ValidacionFacturaController.java
```java
@PreAuthorize("@sec.tieneRol(authentication, 'validacion') or hasAuthority('SCOPE_ms-liquidador-neg')")
@PostMapping("/vencimiento")
public ResponseEntity<ValidacionFacturaResponseDto> validarFacturaVencida(
        @RequestBody ValidacionFacturaRequestDto request) {
    // ...
}
```

Permite acceso a usuarios con rol 'validacion' O a servicios con autoridad SCOPE_ms-liquidador-neg.

---

## Cambios en ms-liquidador-neg

### 1. FeignConfig.java
```java
@Bean
public RequestInterceptor feignRequestInterceptor() {
    return requestTemplate -> {
        String username = null;
        String password = feignProperties.getPassword();

        // Estrategia: Para ms-negociador-orq, usar credenciales del servicio
        if (requestTemplate.request().url().contains("ms-negociador-orq")) {
            username = feignProperties.getUsername();
            log.fine("Usando credenciales del servicio para ms-negociador-orq");
        } else {
            // Para otros servicios, usar RUT del operador
            username = OperacionContextHolder.getRutOperador();
        }

        if (username != null && password != null) {
            String credentials = username + ":" + password;
            String encodedCredentials = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            requestTemplate.header("Authorization", "Basic " + encodedCredentials);
        }
    };
}
```

**Cambios clave:**
- Detecta llamadas a ms-negociador-orq
- Usa `feignProperties.getUsername()` en lugar de `OperacionContextHolder.getRutOperador()`
- Mantiene compatibilidad con otros servicios

### 2. application.yml
```yaml
feign:
  security:
    username: ${FEIGN_USERNAME:ms-liquidador-neg}
    password: ${FEIGN_PASSWORD:liquidador-secret}
```

**Nota:** La contraseña debe coincidir con el hash BCrypt en ms-negociador-orq.

---

## Flujo de Autenticación

### 1. Usuario (JWT)
```
Cliente → ms-negociador-orq (JWT en header Authorization)
           ↓
           oauth2ResourceServer() valida el JWT
           ↓
           JwtRolConverter convierte claims a autoridades
           ↓
           @PreAuthorize valida el rol
           ↓
           Acceso permitido
```

### 2. Servicio (ms-liquidador-neg)
```
ms-liquidador-neg → Feign Client agrega Basic Auth
                    ↓
                    Authorization: Basic <base64(ms-liquidador-neg:liquidador-secret)>
                    ↓
                    POST /validaciones/facturas/vencimiento
                    ↓
ms-negociador-orq   httpBasic() intercepta
                    ↓
                    AuthenticationManager valida
                    ↓
                    DaoAuthenticationProvider → ServiceCredentialsUserDetailsService
                    ↓
                    Carga credenciales de application.yml
                    ↓
                    Compara password con BCrypt
                    ↓
                    Crea Authentication con autoridad SCOPE_ms-liquidador-neg
                    ↓
                    @PreAuthorize acepta la autoridad
                    ↓
                    Acceso permitido
```

---

## Verificación

Para verificar que funciona:

1. **Reinicia ambos servicios:**
   ```bash
   # ms-negociador-orq (puerto 8083)
   ./gradlew bootRun
   
   # ms-liquidador-neg (puerto 8084)
   ./gradlew bootRun
   ```

2. **Prueba JWT (usuario):**
   ```bash
   curl -X POST http://localhost:8083/validaciones/facturas/vencimiento \
     -H "Authorization: Bearer <JWT_TOKEN>" \
     -H "Content-Type: application/json" \
     -d '{"numeroFactura":"FECI3958","fechaProceso":"2026-06-13"}'
   
   # Esperado: 200 OK (si el rol está autorizado)
   ```

3. **Prueba Basic Auth (servicio):**
   ```bash
   curl -X POST http://localhost:8083/validaciones/facturas/vencimiento \
     -H "Authorization: Basic $(echo -n 'ms-liquidador-neg:liquidador-secret' | base64)" \
     -H "Content-Type: application/json" \
     -d '{"numeroFactura":"FECI3958","fechaProceso":"2026-06-13"}'
   
   # Esperado: 200 OK
   ```

4. **O simplemente ejecuta la operación de liquidación en ms-liquidador-neg** que llama automáticamente a la validación.

---

## Notas de Seguridad

⚠️ **Importante:**

- El hash BCrypt en `application.yml` es no-reversible. No se puede recuperar la contraseña a partir del hash.
- La contraseña en el cliente (ms-liquidador-neg) se envía en formato base64, que es fácilmente decodificable.
- **En producción**, usar HTTPS obligatoriamente para proteger las credenciales en tránsito.
- Considera usar variables de entorno o secretos para las credenciales en lugar de commitearlas en el código.

---

## Resolución de Problemas

### 401 Unauthorized

**Causa probable:** Las credenciales no coinciden.

**Verificación:**
1. Confirma que `application.yml` en ms-negociador-orq tiene las credenciales correctas
2. Confirma que `application.yml` en ms-liquidador-neg tiene credenciales que coinciden
3. Verifica que el hash BCrypt es válido

**Debug:**
```
# En ms-negociador-orq, habilita logs
logging.level.com.negociadororq.infrastructure.security: DEBUG
```

### 403 Forbidden

**Causa probable:** Las autoridades no coinciden.

**Verificación:**
1. Confirma que `@PreAuthorize` en el controller acepta `SCOPE_ms-liquidador-neg`
2. Verifica que `ServiceCredentialsUserDetailsService` asigna correctamente la autoridad

---

## Migración Futura

Para cambiar a OAuth2 Client Credentials (mejor práctica):

1. Implementar Spring Authorization Server
2. Configurar `spring-security-oauth2-client` en ms-liquidador-neg
3. Actualizar FeignConfig para usar `ClientHttpRequestInterceptor` con OAuth2 token
4. Mantener backward compatibility con Basic Auth durante transición

Pero por ahora, Basic Auth + JWT es suficiente y más simple de mantener.
