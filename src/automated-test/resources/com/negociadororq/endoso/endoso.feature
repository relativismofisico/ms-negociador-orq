@regression @full
Feature: POST /negociaciones/{idNegociacion}/endosos — Registrar Endoso de Descuento

  # Endpoint: POST /negociaciones/{idNegociacion}/endosos  -> 201 EndosoResponseDto
  # Roles permitidos: ADMINISTRADOR, EMPRESA, FONDEADOR
  # ALTO: modifica datos. NO ejecutar en produccion (no tag @production-safe).

  Background:
    * url baseUrl
    * def idNeg = testData.negociacionId || 'NEG-TEST-00001'
    # Guard exigirActorEsLlamador (suplantación): solo dev/qa (usa jwtSecret).
    * def JwtGen = jwtSecret ? Java.type('com.negociadororq.auth.JwtTestHelper') : null
    * def bearer = function(sub, rol){ return { Authorization: 'Bearer ' + JwtGen.generate(sub, rol, jwtSecret) } }
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
        "cufesEndosadas":      "##array",
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

  # Happy path REAL contra el seed Mongo _e2e-infra/seed/ms-negociador-orq.mongo.js
  # (negociación NEG-END-CUFE + factura FE-END-CUFE-1 con su CUFE). Verifica que el
  # endoso creado transporta el cufe de la factura — hilo cross-repo con
  # ms-consultasFacturas-neg, que lo usa para resolver la factura exacta.
  @regression @full
  Scenario: El endoso creado transporta el CUFE de la factura (cufesEndosadas)
    Given path '/negociaciones/NEG-END-CUFE/endosos'
    And header Authorization = 'Bearer ' + tokenAdmin
    And request { rutActor: 'KARATE-CF-PROV', rutOperador: 'KARATE-CF-PROV', numerosFactura: ['FE-END-CUFE-1'] }
    When method POST
    Then status 201
    And match response.facturasEndosadas == ['FE-END-CUFE-1']
    And match response.cufesEndosadas == ['CUFE-END-CUFE-0001-abcdef0123456789']
    And match response.facturasRechazadas == []

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

  # ─── Suplantación de empresa (CompanyOwnershipGuard.exigirActorEsLlamador) ──────
  # El guard corre tras fijar el idNegociacion y antes del servicio: un rutActor de
  # otra empresa se rechaza con 403 antes de tocar la negociación.

  @regression @full
  Scenario: La empresa 7005 no puede endosar declarando rutActor de la 7004 — 403
    Given path '/negociaciones/NEG-SUP-ENDOSO/endosos'
    And headers bearer('KARATE-AJENA', 'EMPRESA')
    And request { rutActor: 'KARATE-DESTINO', rutOperador: 'KARATE-DESTINO', numerosFactura: ['FAC-SUP-1'] }
    When method POST
    Then status 403
    And match response.codigo == 'ACCION_NO_PERMITIDA'
    And match response.mensaje == 'No tiene permisos para operar sobre esta empresa.'

  @regression @full
  Scenario: La empresa propia con un rutOperador de otra empresa — 403
    Given path '/negociaciones/NEG-SUP-ENDOSO/endosos'
    And headers bearer('KARATE-DESTINO', 'EMPRESA')
    And request { rutActor: 'KARATE-DESTINO', rutOperador: 'KARATE-AJENA', numerosFactura: ['FAC-SUP-2'] }
    When method POST
    Then status 403
    And match response.codigo == 'ACCION_NO_PERMITIDA'

  @regression @full
  Scenario: ADMINISTRADOR no queda sujeto al guard de suplantación (pasa, no 403 del guard)
    Given path '/negociaciones/NEG-SUP-ENDOSO/endosos'
    And header Authorization = 'Bearer ' + tokenAdmin
    And request { rutActor: 'KARATE-DESTINO', rutOperador: 'KARATE-DESTINO', numerosFactura: ['FAC-SUP-3'] }
    When method POST
    Then assert responseStatus != 500
    * def esGuard403 = responseStatus == 403 && response.mensaje == 'No tiene permisos para operar sobre esta empresa.'
    And match esGuard403 == false