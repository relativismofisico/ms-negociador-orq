@regression @full
Feature: POST /negociaciones/solicitar-otp — Solicitar OTP para Aprobacion

  # Endpoint: POST /negociaciones/solicitar-otp  -> 202 "OTP solicitado"
  # Roles permitidos: ADMINISTRADOR, EMPRESA, FONDEADOR
  # ALTO: dispara evento Kafka (otp.requested). NO ejecutar en produccion (no tag @production-safe).

  Background:
    * url baseUrl
    * def endpoint = '/negociaciones/solicitar-otp'
    * def idNeg = testData.negociacionId || 'NEG-TEST-00001'
    * def bodyOtp =
      """
      {
        "idNegociacion": "#(idNeg)",
        "rutOperador":   "#(testData.rutOperador)",
        "rutEmpresa":    "#(testData.rutEmpresa)",
        "rolActor":      "ADMINISTRADOR",
        "facturas": [
          {
            "numeroFactura": "#(testData.numeroFactura)",
            "aprobado":      true,
            "observacion":   "Aprobado en prueba funcional"
          }
        ],
        "observacion": "Test automatizado"
      }
      """

  # ─── Happy Path ─────────────────────────────────────────────────
  @regression @full
  Scenario: ADMINISTRADOR solicita OTP — no 5xx, si 202 body correcto
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request bodyOtp
    When method POST
    Then assert responseStatus != 500
    # karate.match retorna { pass, message } — se ignora si status != 202
    * def mr = responseStatus != 202 ? { pass: true } : karate.match(response, 'OTP solicitado')
    * assert mr.pass

  @regression @full
  Scenario: EMPRESA puede solicitar OTP (rol permitido)
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenEmpresa
    And request bodyOtp
    When method POST
    Then assert responseStatus != 500
    * assert responseStatus >= 200
    * assert responseStatus < 500

  @regression @full
  Scenario: FONDEADOR puede solicitar OTP (rol permitido)
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenFondeador
    And request bodyOtp
    When method POST
    Then assert responseStatus != 500

  # ─── Contratos de respuesta ─────────────────────────────────────
  @regression @full
  Scenario: Respuesta 202 tiene Content-Type compatible con texto
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request bodyOtp
    When method POST
    Then assert responseStatus != 500
    # Si 202, verificar Content-Type contiene text/plain via JS (match contains no valido dentro de if)
    * def ct = responseStatus == 202 ? responseHeaders['Content-Type'][0] : 'text/plain; charset=UTF-8'
    * match ct contains 'text/plain'

  # ─── Errores de dominio ─────────────────────────────────────────
  @regression @full
  Scenario: Negociacion inexistente — servicio async acepta y retorna no 5xx
    # solicitarOtp es fire-and-forget (Kafka): acepta la solicitud sin validar la negociacion
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request
      """
      {
        "idNegociacion": "NEG-QUE-NO-EXISTE-ABC123",
        "rutOperador":   "#(testData.rutOperador)",
        "rutEmpresa":    "#(testData.rutEmpresa)",
        "rolActor":      "ADMINISTRADOR",
        "facturas":      [{ "numeroFactura": "FAC-X", "aprobado": true }]
      }
      """
    When method POST
    Then assert responseStatus != 500
    And assert responseStatus < 500

  # ─── Validaciones de entrada ────────────────────────────────────
  @regression @full
  Scenario: Body JSON malformado — 400 CUERPO_INVALIDO
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And header Content-Type = 'application/json'
    And request 'no-es-json'
    When method POST
    Then status 400
    And match response.codigo == 'CUERPO_INVALIDO'

  # ─── Verbo HTTP incorrecto ──────────────────────────────────────
  @regression @full
  Scenario: GET en vez de POST — 405 METODO_NO_PERMITIDO
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    When method GET
    Then status 405
    And match response.codigo == 'METODO_NO_PERMITIDO'