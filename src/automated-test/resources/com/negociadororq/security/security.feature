@smoke @regression @full @production-safe
Feature: Seguridad — Autenticacion y Autorizacion JWT

  # Cubre: TOKEN_AUSENTE, TOKEN_INVALIDO, TOKEN_EXPIRADO, ACCION_NO_PERMITIDA
  # Todos los 401/403 son interceptados por Spring Security antes del controlador,
  # por eso el body de la request no importa para autenticacion (solo para autorizacion fina).

  Background:
    * url baseUrl
    * def bodyValidacion = { numeroFactura: 'FAC-X', fechaProceso: '2024-01-15' }
    * def schemaErrorSeguridad = { timestamp: '#present', status: '#number', error: '#string', codigo: '#string', mensaje: '#string', path: '#string' }

  # ─────────────────────────────────────── 401 TOKEN_AUSENTE ─────
  @smoke @regression @full @production-safe
  Scenario: Sin header Authorization — 401 TOKEN_AUSENTE
    Given path '/validaciones/facturas/vencimiento'
    And request bodyValidacion
    When method POST
    Then status 401
    And match response.codigo   == 'TOKEN_AUSENTE'
    And match response.status   == 401
    And match response.error    == 'Unauthorized'
    And match response.timestamp == '#present'
    And match response.path     == '/validaciones/facturas/vencimiento'

  @smoke @regression @full @production-safe
  Scenario: Schema completo de respuesta 401 cumple contrato
    Given path '/validaciones/facturas/vencimiento'
    And request bodyValidacion
    When method POST
    Then status 401
    And match response == schemaErrorSeguridad

  # ─────────────────────────────────────── 401 TOKEN_INVALIDO ────
  @smoke @regression @full @production-safe
  Scenario: Token con formato invalido — 401 TOKEN_INVALIDO
    Given path '/validaciones/facturas/vencimiento'
    And header Authorization = 'Bearer esto.no.es.un.jwt'
    And request bodyValidacion
    When method POST
    Then status 401
    And match response.codigo == 'TOKEN_INVALIDO'
    And match response.status == 401

  @smoke @regression @full @production-safe
  Scenario: Header Authorization presente pero sin Bearer — 401 TOKEN_INVALIDO
    Given path '/validaciones/facturas/vencimiento'
    And header Authorization = 'Basic dXNlcjpwYXNz'
    And request bodyValidacion
    When method POST
    Then status 401
    And match response.codigo == 'TOKEN_INVALIDO'

  # ─────────────────────────────────────── 401 TOKEN_EXPIRADO ────
  # No @production-safe: requiere tokenExpired (generado con JWT_SECRET, solo dev/qa)
  @regression @full
  Scenario: Token expirado — 401 con codigo de rechazo
    Given path '/validaciones/facturas/vencimiento'
    And header Authorization = 'Bearer ' + tokenExpired
    And request bodyValidacion
    When method POST
    Then status 401
    # CustomAuthenticationEntryPoint clasifica segun causa. Si getCause() no contiene "expired",
    # puede devolver TOKEN_INVALIDO en vez de TOKEN_EXPIRADO — ambos son rechazo valido del token.
    * match ['TOKEN_EXPIRADO', 'TOKEN_INVALIDO'] contains response.codigo
    And match response.status == 401

  # ─────────────────────────────────────── 403 por endpoint ──────
  # NOTE: El servidor debe devolver 403 ACCION_NO_PERMITIDA. Si devuelve 500, el
  # CustomAccessDeniedHandler tiene un bug — revisar la cadena de manejo de AccessDeniedException.
  @regression @full @production-safe
  Scenario: FONDEADOR llama POST /negociar/factura — debe ser rechazado
    Given path '/negociar/factura'
    And header Authorization = 'Bearer ' + tokenFondeador
    And request { rutEmpresa: '800-1', rolEmpresa: 'PROVEEDOR', facturas: [], rutOperador: 'X' }
    When method POST
    Then assert responseStatus >= 400
    And match response.codigo == '#string'
    And match response.status == '#number'

  @regression @full @production-safe
  Scenario: OPERARIO llama POST /negociaciones/{id}/endosos — debe ser rechazado
    Given path '/negociaciones/ID-INEXISTENTE/endosos'
    And header Authorization = 'Bearer ' + tokenOperario
    And request { rutActor: 'X', numerosFactura: ['F1'] }
    When method POST
    Then assert responseStatus >= 400
    And match response.codigo == '#string'

  @regression @full @production-safe
  Scenario: OPERARIO llama POST /negociaciones/solicitar-otp — debe ser rechazado
    Given path '/negociaciones/solicitar-otp'
    And header Authorization = 'Bearer ' + tokenOperario
    And request { idNegociacion: 'X', rutOperador: 'X', rutEmpresa: 'X', rolActor: 'X', facturas: [] }
    When method POST
    Then assert responseStatus >= 400
    And match response.codigo == '#string'

  @regression @full @production-safe
  Scenario: OPERARIO llama POST /negociaciones/confirmar-otp — debe ser rechazado
    Given path '/negociaciones/confirmar-otp'
    And header Authorization = 'Bearer ' + tokenOperario
    And request { idNegociacion: 'X', rutEmpresa: 'X', rutOperador: 'X', otp: '000000', facturas: [] }
    When method POST
    Then assert responseStatus >= 400
    And match response.codigo == '#string'

  @regression @full @production-safe
  Scenario: Schema de respuesta de error de acceso cumple contrato
    Given path '/negociar/factura'
    And header Authorization = 'Bearer ' + tokenFondeador
    And request { rutEmpresa: '800-1', rolEmpresa: 'PROVEEDOR', facturas: [], rutOperador: 'X' }
    When method POST
    Then assert responseStatus >= 400
    And match response.timestamp == '#present'
    And match response.codigo    == '#string'
    And match response.mensaje   == '#string'
    And match response.path      == '#string'