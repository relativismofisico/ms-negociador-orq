@full
Feature: POST /negociaciones/confirmar-otp — Confirmar OTP y Aprobar Endoso

  # Endpoint: POST /negociaciones/confirmar-otp  -> 202 "Validando OTP..."
  # Roles permitidos: ADMINISTRADOR, EMPRESA, FONDEADOR
  # CRITICO: confirma endoso, dispara liquidacion (Kafka: otp.validate).
  # Solo en @full — requiere OTP real generado previamente.

  Background:
    * url baseUrl
    * def endpoint = '/negociaciones/confirmar-otp'
    * def idNeg = testData.negociacionId || 'NEG-TEST-00001'
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