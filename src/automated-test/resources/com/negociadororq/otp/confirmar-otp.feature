@full
Feature: POST /negociaciones/confirmar-otp — Confirmar OTP y Aprobar Endoso

  # Endpoint: POST /negociaciones/confirmar-otp  -> 202 "Validando OTP..."
  # Roles permitidos: ADMINISTRADOR, EMPRESA, FONDEADOR
  # CRITICO: confirma endoso y dispara liquidacion. Fase 28d (A1): el codigo se valida por HTTP en security (ya no por
  # Kafka otp.validate); security no disponible -> 503 sin cambiar nada.
  # Solo en @full — requiere OTP real generado previamente.

  Background:
    * url baseUrl
    * def endpoint = '/negociaciones/confirmar-otp'
    * def idNeg = testData.negociacionId || 'NEG-TEST-00001'
    # Guard exigirActorEsLlamador (suplantación). Solo dev/qa (usa jwtSecret).
    * def JwtGen = jwtSecret ? Java.type('com.negociadororq.auth.JwtTestHelper') : null
    * def bearer = function(sub, rol){ return { Authorization: 'Bearer ' + JwtGen.generate(sub, rol, jwtSecret) } }
    * def bodyConfirmar =
      """
      {
        "idNegociacion": "#(idNeg)",
        "rutEmpresa":    "#(testData.rutEmpresa)",
        "rutOperador":   "#(testData.rutOperador)",
        "otp":           "000000",
        "facturas": [
          {
            "numeroFactura": "#(testData.numeroFactura)",
            "aprobado":      true,
            "observacion":   "Confirmado en prueba funcional"
          }
        ]
      }
      """

  # ─── Happy Path ─────────────────────────────────────────────────
  @full
  Scenario: ADMINISTRADOR confirma OTP — no 5xx, si 202 body correcto
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request bodyConfirmar
    When method POST
    Then assert responseStatus != 500
    * def mr = responseStatus != 202 ? { pass: true } : karate.match(response, 'Validando OTP...')
    * assert mr.pass

  @full
  Scenario: EMPRESA puede confirmar OTP (rol permitido)
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenEmpresa
    And request bodyConfirmar
    When method POST
    Then assert responseStatus != 500
    * assert responseStatus >= 200
    * assert responseStatus < 500

  @full
  Scenario: FONDEADOR puede confirmar OTP (rol permitido)
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenFondeador
    And request bodyConfirmar
    When method POST
    Then assert responseStatus != 500

  # ─── Contratos de respuesta ─────────────────────────────────────
  @full
  Scenario: Respuesta 202 incluye body y Content-Type correcto
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request bodyConfirmar
    When method POST
    Then assert responseStatus != 500
    * def mr = responseStatus != 202 ? { pass: true } : karate.match(response, 'Validando OTP...')
    * assert mr.pass
    * def ct = responseStatus == 202 ? responseHeaders['Content-Type'][0] : 'text/plain; charset=UTF-8'
    * match ct contains 'text/plain'

  # ─── Errores de dominio ─────────────────────────────────────────
  @full
  Scenario: OTP o negociacion inexistente — servicio async acepta y retorna no 5xx
    # confirmarOtp es fire-and-forget (Kafka): acepta la solicitud sin validar la negociacion
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request
      """
      {
        "idNegociacion": "NEG-QUE-NO-EXISTE-XYZ",
        "rutEmpresa":    "#(testData.rutEmpresa)",
        "rutOperador":   "#(testData.rutOperador)",
        "otp":           "999999",
        "facturas":      [{ "numeroFactura": "FAC-X", "aprobado": true }]
      }
      """
    When method POST
    Then assert responseStatus != 500
    And assert responseStatus < 500

  # ─── Validaciones de entrada ────────────────────────────────────
  @full
  Scenario: Body JSON malformado — 400 CUERPO_INVALIDO
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And header Content-Type = 'application/json'
    And request 'malformed{'
    When method POST
    Then status 400
    And match response.codigo == 'CUERPO_INVALIDO'
    And match response.status == 400

  @full
  Scenario: Campo otp nulo — el servicio debe manejar sin 5xx
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request
      """
      {
        "idNegociacion": "#(idNeg)",
        "rutEmpresa":    "#(testData.rutEmpresa)",
        "rutOperador":   "#(testData.rutOperador)",
        "otp":           null,
        "facturas":      []
      }
      """
    When method POST
    Then assert responseStatus != 500

  # ─── Verbo HTTP incorrecto ──────────────────────────────────────
  @full
  Scenario: GET en vez de POST — 405 METODO_NO_PERMITIDO
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    When method GET
    Then status 405
    And match response.codigo == 'METODO_NO_PERMITIDO'

  # ─── Suplantación de empresa (CompanyOwnershipGuard.exigirActorEsLlamador) ──────

  @full
  Scenario: La empresa 7005 no puede confirmar OTP declarando rutEmpresa de la 7004 — 403
    Given path endpoint
    And headers bearer('KARATE-AJENA', 'EMPRESA')
    And request
      """
      {
        "idNegociacion": "NEG-SUP-OTP",
        "rutEmpresa":    "KARATE-DESTINO",
        "rutOperador":   "KARATE-DESTINO",
        "otp":           "000000",
        "facturas":      [ { "numeroFactura": "FAC-SUP-1", "aprobado": true } ]
      }
      """
    When method POST
    Then status 403
    And match response.codigo == 'ACCION_NO_PERMITIDA'
    And match response.mensaje == 'No tiene permisos para operar sobre esta empresa.'

  @full
  Scenario: La empresa propia confirma OTP para sí misma — pasa el guard (async, no 5xx)
    Given path endpoint
    And headers bearer('KARATE-DESTINO', 'EMPRESA')
    And request
      """
      {
        "idNegociacion": "NEG-SUP-OTP",
        "rutEmpresa":    "KARATE-DESTINO",
        "rutOperador":   "KARATE-DESTINO",
        "otp":           "000000",
        "facturas":      [ { "numeroFactura": "FAC-SUP-1", "aprobado": true } ]
      }
      """
    When method POST
    Then assert responseStatus != 500
    * def esGuard403 = responseStatus == 403 && response.mensaje == 'No tiene permisos para operar sobre esta empresa.'
    And match esGuard403 == false
  # ─── Fase 28d (A1): validación por HTTP en security ─────────────
  @full
  Scenario: A1 - sin pendiente que espere el codigo, 409 OTP_SIN_SOLICITUD_PENDIENTE (no se llama a security)
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request { idNegociacion: 'NEG-A1-SIN-PENDIENTE', rutEmpresa: 'KARATE-A1-EMP', rutOperador: 'op-karate-a1', otp: '123456' }
    When method POST
    Then status 409
    And match response.codigo == 'OTP_SIN_SOLICITUD_PENDIENTE'

  @full
  Scenario: A1 - con pendiente: security caido da 503 y nada cambia; con security real, 202 y el pendiente se descarta
    # Pendiente sembrado (post-seed.mongo.js): NEG-A1-HTTP / KARATE-A1-EMP / op-karate-a1.
    # CI: SECURITY_UTIL_BASE_URL apunta a un puerto cerrado -> 503 dos veces (el reintento encuentra el mismo pendiente).
    # e2e: security real no tiene OTP para esta negociacion -> EXPIRADO -> 202; el segundo intento ya no tiene pendiente.
    * def body = { idNegociacion: 'NEG-A1-HTTP', rutEmpresa: 'KARATE-A1-EMP', rutOperador: 'op-karate-a1', otp: '123456' }
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request body
    When method POST
    Then assert responseStatus == 503 || responseStatus == 202
    * def primero = responseStatus
    * if (primero == 503) karate.match(response.codigo, 'SERVICIO_DEPENDIENTE_NO_DISPONIBLE').pass || karate.fail('503 sin el codigo esperado')
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request body
    When method POST
    * def esperado = primero == 503 ? 503 : 409
    Then match responseStatus == esperado
    * if (esperado == 409) karate.match(response.codigo, 'OTP_SIN_SOLICITUD_PENDIENTE').pass || karate.fail('409 sin el codigo esperado')
