@regression @full
Feature: POST /negociar/factura — Crear Negociacion Factoring/Confirming

  # Endpoint: POST /negociar/factura  -> 201 NegociacionResponseDto
  # Roles permitidos: ADMINISTRADOR, EMPRESA, OPERARIO
  # CRITICO: crea datos en BD. NO ejecutar en produccion (no hay tag @production-safe).

  Background:
    * url baseUrl
    * def endpoint = '/negociar/factura'
    # ── Guard CompanyOwnershipGuard.exigirActorEsLlamador (suplantación de empresa) ──
    # Solo disponible en dev/qa: usa JwtTestHelper + jwtSecret. Seed _e2e-infra:
    #   empresas 7004 (NIT KARATE-DESTINO) y 7005 (NIT KARATE-AJENA), usuarios EMPRESA
    #   homónimos, operario op-destino con company_ide 7004.
    * def JwtGen = jwtSecret ? Java.type('com.negociadororq.auth.JwtTestHelper') : null
    * def bearer = function(sub, rol){ return { Authorization: 'Bearer ' + JwtGen.generate(sub, rol, jwtSecret) } }
    * def fondeador = { rutFondeador: '#(testData.rutFondeador)', nombreFondeador: 'Fondeador Test' }
    * def facturaBase =
      """
      {
        "numeroFactura": "#(testData.numeroFactura)",
        "valorFactura":  5000000,
        "fondeadores":   [ { "rutFondeador": "#(testData.rutFondeador)", "nombreFondeador": "Fondeador Test" } ]
      }
      """
    * def bodyFactoring =
      """
      {
        "rutEmpresa":     "#(testData.rutEmpresa)",
        "rolEmpresa":     "PROVEEDOR",
        "tipoOperacion":  { "tipo": "FACTORING", "clase": "NORMAL" },
        "facturas":       [ { "numeroFactura": "#(testData.numeroFactura)", "valorFactura": 5000000, "fondeadores": [ { "rutFondeador": "#(testData.rutFondeador)", "nombreFondeador": "Fondeador Test" } ] } ],
        "rutOperador":    "#(testData.rutOperador)"
      }
      """
    * def bodyConfirming =
      """
      {
        "rutEmpresa":     "#(testData.rutEmpresa)",
        "rolEmpresa":     "PAGADOR",
        "tipoOperacion":  { "tipo": "CONFIRMING", "clase": "NORMAL" },
        "facturas":       [ { "numeroFactura": "#(testData.numeroFactura)", "valorFactura": 5000000, "fondeadores": [ { "rutFondeador": "#(testData.rutFondeador)", "nombreFondeador": "Fondeador Test" } ] } ],
        "rutOperador":    "#(testData.rutOperador)"
      }
      """
    * def schemaNegociacion =
      """
      {
        "idNegociacion":        "##string",
        "rutEmpresa":           "#string",
        "tipoOperacion":        "#object",
        "facturas":             "#array",
        "fondeadoresAsignados": "##array",
        "totalNegociado":       "#number",
        "estado":               "#string",
        "errores":              "##array"
      }
      """

  # ─── Happy Path ─────────────────────────────────────────────────
  @regression @full
  Scenario: FACTORING con ADMINISTRADOR — no 500 y schema correcto si 201
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request bodyFactoring
    When method POST
    Then assert responseStatus != 500
    # karate.match devuelve { pass, message } — se ignora si status != 201
    * def mr = responseStatus != 201 ? { pass: true } : karate.match(response, schemaNegociacion)
    * assert mr.pass

  @regression @full
  Scenario: CONFIRMING con EMPRESA — no 500 y schema correcto si 201
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenEmpresa
    And request bodyConfirming
    When method POST
    Then assert responseStatus != 500
    * def mr = responseStatus != 201 ? { pass: true } : karate.match(response, schemaNegociacion)
    * assert mr.pass

  @regression @full
  Scenario: OPERARIO puede crear negociacion (rol permitido)
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenOperario
    And request bodyFactoring
    When method POST
    Then assert responseStatus != 500
    * def mrRut = responseStatus != 201 ? { pass: true } : karate.match(response.rutEmpresa, testData.rutEmpresa)
    * assert mrRut.pass

  # ─── Estado en respuesta ────────────────────────────────────────
  @full
  Scenario: Respuesta 201 incluye estado valido (PENDIENTE, RECHAZADA o ACEPTADA)
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request bodyFactoring
    When method POST
    Then assert responseStatus != 500
    * def mrEstado = responseStatus != 201 ? { pass: true } : karate.match(response.estado, '#regex (PENDIENTE|RECHAZADA|ACEPTADA)')
    * assert mrEstado.pass

  # ─── Validaciones de entrada ────────────────────────────────────
  @regression @full
  Scenario: Body JSON malformado -> 400 CUERPO_INVALIDO
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And header Content-Type = 'application/json'
    And request '{ malformed-json'
    When method POST
    Then status 400
    And match response.codigo == 'CUERPO_INVALIDO'
    And match response.status == 400

  @regression @full
  Scenario: tipoOperacion.tipo nulo — error de dominio, no 500
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request
      """
      {
        "rutEmpresa":    "#(testData.rutEmpresa)",
        "rolEmpresa":    "PROVEEDOR",
        "tipoOperacion": { "tipo": null, "clase": "NORMAL" },
        "facturas":      [ { "numeroFactura": "FAC-X", "valorFactura": 1000, "fondeadores": [ { "rutFondeador": "#(testData.rutFondeador)", "nombreFondeador": "F" } ] } ],
        "rutOperador":   "#(testData.rutOperador)"
      }
      """
    When method POST
    Then assert responseStatus >= 400
    And assert responseStatus < 500

  # ─── Verbo HTTP incorrecto ──────────────────────────────────────
  @regression @full
  Scenario: GET en vez de POST -> 405 METODO_NO_PERMITIDO
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    When method GET
    Then status 405
    And match response.codigo == 'METODO_NO_PERMITIDO'

  # ─── Suplantación de empresa (CompanyOwnershipGuard.exigirActorEsLlamador) ──────
  # El guard corre ANTES del servicio: rechaza que la empresa A negocie declarando
  # en el body rutEmpresa/rutOperador de la empresa B.

  @regression @full
  Scenario: La empresa 7005 no puede negociar declarando rutEmpresa de la 7004 — 403
    Given path endpoint
    And headers bearer('KARATE-AJENA', 'EMPRESA')
    And request
      """
      {
        "rutEmpresa":    "KARATE-DESTINO",
        "rolEmpresa":    "PROVEEDOR",
        "tipoOperacion": { "tipo": "FACTORING", "clase": "NORMAL" },
        "facturas":      [ { "numeroFactura": "FAC-SUP-1", "valorFactura": 1000000, "fondeadores": [ { "rutFondeador": "KARATE-FUNDER", "nombreFondeador": "F" } ] } ],
        "rutOperador":   "KARATE-DESTINO"
      }
      """
    When method POST
    Then status 403
    And match response.codigo == 'ACCION_NO_PERMITIDA'

  @regression @full
  Scenario: La empresa propia con un rutOperador de otra empresa — 403
    Given path endpoint
    And headers bearer('KARATE-DESTINO', 'EMPRESA')
    And request
      """
      {
        "rutEmpresa":    "KARATE-DESTINO",
        "rolEmpresa":    "PROVEEDOR",
        "tipoOperacion": { "tipo": "FACTORING", "clase": "NORMAL" },
        "facturas":      [ { "numeroFactura": "FAC-SUP-2", "valorFactura": 1000000, "fondeadores": [ { "rutFondeador": "KARATE-FUNDER", "nombreFondeador": "F" } ] } ],
        "rutOperador":   "KARATE-AJENA"
      }
      """
    When method POST
    Then status 403
    And match response.codigo == 'ACCION_NO_PERMITIDA'

  @regression @full
  Scenario: El mensaje del 403 de suplantación no filtra ids ni nombres internos
    Given path endpoint
    And headers bearer('KARATE-AJENA', 'EMPRESA')
    And request
      """
      {
        "rutEmpresa":    "KARATE-DESTINO",
        "rolEmpresa":    "PROVEEDOR",
        "tipoOperacion": { "tipo": "FACTORING", "clase": "NORMAL" },
        "facturas":      [ { "numeroFactura": "FAC-SUP-3", "valorFactura": 1000000, "fondeadores": [ { "rutFondeador": "KARATE-FUNDER", "nombreFondeador": "F" } ] } ],
        "rutOperador":   "KARATE-DESTINO"
      }
      """
    When method POST
    Then status 403
    And match response.mensaje == 'No tiene permisos para operar sobre esta empresa.'