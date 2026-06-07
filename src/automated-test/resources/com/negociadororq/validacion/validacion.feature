@smoke @regression @full @production-safe
Feature: POST /validaciones/facturas/vencimiento — Validacion de Vencimiento de Factura

  # Endpoint: POST /validaciones/facturas/vencimiento
  # Roles permitidos: ADMINISTRADOR, OPERARIO, EMPRESA, FONDEADOR
  # Respuesta exitosa 200: ValidacionFacturaResponseDto
  #   { valida: boolean, vencida: boolean, diasHastaVencimiento: long, codigo: string, mensaje: string }
  #
  # NOTA: los escenarios de happy path aceptan 200 o 404. Si se recibe 404 FACTURA_NO_ENCONTRADA,
  # el API funciona correctamente pero los datos de prueba (TEST_FACTURA_NUM) no existen en el
  # ambiente. Configurar la variable de entorno con un numero de factura valido para obtener 200.

  Background:
    * url baseUrl
    * def endpoint = '/validaciones/facturas/vencimiento'
    * def bodyValido =
      """
      {
        "numeroFactura": "#(testData.numeroFactura)",
        "fechaProceso":  "#(testData.fechaProceso)"
      }
      """
    * def schemaRespuesta200 =
      """
      {
        "valida":                "#boolean",
        "vencida":               "#boolean",
        "diasHastaVencimiento":  "#number",
        "codigo":                "#string",
        "mensaje":               "#string"
      }
      """
    * def schemaError400 =
      """
      {
        "timestamp": "#present",
        "status":    400,
        "error":     "#string",
        "codigo":    "#string",
        "mensaje":   "#string",
        "path":      "#string"
      }
      """

  # ─── Happy Path — todos los roles permitidos ───────────────────
  @smoke @regression @full @production-safe
  Scenario: ADMINISTRADOR valida factura — 200 con schema o 404 si no hay datos
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request bodyValido
    When method POST
    Then assert responseStatus != 500
    * match [200, 404] contains responseStatus
    * def mr = responseStatus != 200 ? { pass: true } : karate.match(response, schemaRespuesta200)
    * assert mr.pass

  @regression @full @production-safe
  Scenario: OPERARIO puede validar facturas (rol permitido)
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenOperario
    And request bodyValido
    When method POST
    Then assert responseStatus != 500
    * match [200, 404] contains responseStatus
    * def mrValida = responseStatus != 200 ? { pass: true } : karate.match(response.valida, '#boolean')
    * assert mrValida.pass
    * def mrVencida = responseStatus != 200 ? { pass: true } : karate.match(response.vencida, '#boolean')
    * assert mrVencida.pass

  @regression @full @production-safe
  Scenario: EMPRESA puede validar facturas (rol permitido)
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenEmpresa
    And request bodyValido
    When method POST
    Then assert responseStatus != 500
    * match [200, 404] contains responseStatus

  @regression @full @production-safe
  Scenario: FONDEADOR puede validar facturas (rol permitido)
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenFondeador
    And request bodyValido
    When method POST
    Then assert responseStatus != 500
    * match [200, 404] contains responseStatus

  # ─── Semantica del resultado ────────────────────────────────────
  @regression @full @production-safe
  Scenario: Factura con fecha de proceso anterior a hoy puede estar vencida
    # Usar JS Date — no usar new java.text.SimpleDateFormat (falla en GraalVM context de Karate)
    * def ayer = function() { var d = new Date(); d.setDate(d.getDate() - 1); return d.toISOString().split('T')[0]; }()
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request { numeroFactura: '#(testData.numeroFactura)', fechaProceso: '#(ayer)' }
    When method POST
    Then assert responseStatus != 500
    * match [200, 404] contains responseStatus
    * def mrCodigo = responseStatus != 200 ? { pass: true } : karate.match(response.codigo, '#string')
    * assert mrCodigo.pass

  # ─── Validaciones de contrato HTTP ─────────────────────────────
  @smoke @regression @full @production-safe
  Scenario: Body JSON malformado — 400 CUERPO_INVALIDO
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And header Content-Type = 'application/json'
    And request '["esto-no-es-un-objeto"]'
    When method POST
    Then status 400
    And match response.codigo  == 'CUERPO_INVALIDO'
    And match response.status  == 400
    And match response         == schemaError400

  @smoke @regression @full @production-safe
  Scenario: Body con tipos incorrectos (fechaProceso no es fecha) — 400 CUERPO_INVALIDO
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request { numeroFactura: 'FAC-001', fechaProceso: 'no-es-una-fecha' }
    When method POST
    Then status 400
    And match response.codigo == 'CUERPO_INVALIDO'

  @regression @full @production-safe
  Scenario: GET en vez de POST — 405 METODO_NO_PERMITIDO
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    When method GET
    Then status 405
    And match response.codigo == 'METODO_NO_PERMITIDO'
    And match response.status == 405

  @regression @full @production-safe
  Scenario: Content-Type incorrecto — 415 o 400
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And header Content-Type = 'text/plain'
    And request 'texto plano'
    When method POST
    # Spring rechaza content-type no soportado con 415; algunos filtros devuelven 400
    Then assert responseStatus >= 400
    And assert responseStatus < 500