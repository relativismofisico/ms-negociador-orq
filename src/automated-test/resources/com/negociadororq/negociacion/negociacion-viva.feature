@regression @full
Feature: Fase 28d (A5b) — una factura con una negociación viva no se negocia otra vez: FACTURA_CON_NEGOCIACION_VIVA

  # Invariante de John: una factura con una negociación viva nunca está SIN_NEGOCIAR. Seed
  # _e2e-infra/seed/ms-negociador-orq.mongo.js: FE-END-CUFE-1 (NEGOCIADA o, tras el Karate de endoso, ENDOSADA) en la
  # negociación NEG-END-CUFE del proveedor KARATE-CF-PROV. Negociarla otra vez responde 201 con la factura rechazada, la
  # negociación RECHAZADA y el código en el motivo.

  Background:
    * url baseUrl
    * def endpoint = '/negociar/factura'
    * def cuerpo =
      """
      {
        rutEmpresa: 'KARATE-CF-PROV', rolEmpresa: 'PROVEEDOR',
        tipoOperacion: { tipo: 'FACTORING', clase: 'NORMAL' },
        rutOperador: '#(testData.rutOperador)',
        facturas: [ { numeroFactura: 'FE-END-CUFE-1', cufe: 'CUFE-END-CUFE-0001-abcdef0123456789',
                      rutProveedor: 'KARATE-CF-PROV', rutPagador: 'KARATE-CF-PAG', valorFactura: 7000000,
                      fondeadores: [ { rutFondeador: 'KARATE-CF-FON', nombreFondeador: 'Karate CF Fondeador' } ] } ]
      }
      """

  Scenario: negociar otra vez una factura con negociación viva: 201 RECHAZADA con FACTURA_CON_NEGOCIACION_VIVA
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request cuerpo
    When method POST
    Then status 201
    And match response.estado == 'RECHAZADA'
    And match response.facturas == '#[0]'
    * def motivos = karate.toString(response.errores)
    And match motivos contains 'FACTURA_CON_NEGOCIACION_VIVA'
    And match motivos !contains 'Exception'
