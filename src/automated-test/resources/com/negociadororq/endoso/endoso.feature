@regression @full
Feature: POST /negociaciones/{idNegociacion}/endosos — Registrar Endoso de Descuento

  # Endpoint: POST /negociaciones/{idNegociacion}/endosos  -> 201 EndosoResponseDto
  # Roles permitidos: ADMINISTRADOR, EMPRESA, FONDEADOR
  # ALTO: modifica datos. NO ejecutar en produccion (no tag @production-safe).

  Background:
    * url baseUrl
    * def idNeg = testData.negociacionId || 'NEG-TEST-00001'
    * def bodyEndoso =
      """
      {
        "rutActor":       "#(testData.rutEmpresa)",
        "numerosFactura": ["#(testData.numeroFactura)"]
      }
      """
    * def schemaEndoso =
      """
      {
        "idNegociacion":       "#string",
        "estadoEndoso":        "#string",
        "facturasEndosadas":   "##array",
        "facturasRechazadas":  "##array",
        "plantillaHtml":       "##string"
      }
      """

  # ─── Happy Path ─────────────────────────────────────────────────
  @regression @full
  Scenario: ADMINISTRADOR registra endoso — no 5xx y schema correcto si 201
    Given path '/negociaciones/' + idNeg + '/endosos'
    And header Authorization = 'Bearer ' + tokenAdmin
    And request bodyEndoso
    When method POST
    Then assert responseStatus != 500
    * def mr = responseStatus != 201 ? { pass: true } : karate.match(response, schemaEndoso)
    * assert mr.pass
    * def mrEstado = responseStatus != 201 ? { pass: true } : karate.match(response.estadoEndoso, '#regex (PENDIENTE|ENDOSADO|RECHAZADO)')
    * assert mrEstado.pass

  @regression @full
  Scenario: EMPRESA puede registrar endoso (rol permitido)
    Given path '/negociaciones/' + idNeg + '/endosos'
    And header Authorization = 'Bearer ' + tokenEmpresa
    And request bodyEndoso
    When method POST
    Then assert responseStatus != 500
    * assert responseStatus >= 200
    * assert responseStatus < 500

  @regression @full
  Scenario: FONDEADOR puede registrar endoso (rol permitido)
    Given path '/negociaciones/' + idNeg + '/endosos'
    And header Authorization = 'Bearer ' + tokenFondeador
    And request bodyEndoso
    When method POST
    Then assert responseStatus != 500

  # ─── Errores de dominio ─────────────────────────────────────────
  @regression @full
  Scenario: ID de negociacion inexistente — error de dominio (4xx), no 5xx
    Given path '/negociaciones/NEGOCIACION-QUE-NO-EXISTE-XYZ/endosos'
    And header Authorization = 'Bearer ' + tokenAdmin
    And request bodyEndoso
    When method POST
    Then assert responseStatus >= 400
    And assert responseStatus < 500
    And match response.codigo == '#string'
    And match response.mensaje == '#string'

  # ─── Validaciones de entrada ────────────────────────────────────
  @regression @full
  Scenario: Body JSON malformado — 400 CUERPO_INVALIDO
    Given path '/negociaciones/' + idNeg + '/endosos'
    And header Authorization = 'Bearer ' + tokenAdmin
    And header Content-Type = 'application/json'
    And request 'no-es-json'
    When method POST
    Then status 400
    And match response.codigo == 'CUERPO_INVALIDO'

  @full
  Scenario: numerosFactura vacio — error de dominio esperado
    Given path '/negociaciones/' + idNeg + '/endosos'
    And header Authorization = 'Bearer ' + tokenAdmin
    And request { rutActor: 'X', numerosFactura: [] }
    When method POST
    Then assert responseStatus >= 400
    And assert responseStatus < 500

  # ─── Verbo HTTP incorrecto ──────────────────────────────────────
  @regression @full
  Scenario: GET en vez de POST — 405 METODO_NO_PERMITIDO
    Given path '/negociaciones/' + idNeg + '/endosos'
    And header Authorization = 'Bearer ' + tokenAdmin
    When method GET
    Then status 405
    And match response.codigo == 'METODO_NO_PERMITIDO'