@regression @full
Feature: Fase 28d (A5b) — una factura con una negociación viva no se negocia otra vez: FACTURA_CON_NEGOCIACION_VIVA

  # Invariante de John: una factura con una negociación viva nunca está SIN_NEGOCIAR. Seed
  # _e2e-infra/seed/ms-negociador-orq.mongo.js: FE-KARATE-A5B-VIVA (NEGOCIADA) del proveedor KARATE-PAYERD (7008,
  # operador op-e2e-prov, FACTORING habilitado) y el pagador KARATE-PAYERB (7006). Negociarla otra vez responde 201 con la
  # factura rechazada, la negociación RECHAZADA y el código en el motivo.

  Background:
    * url baseUrl
    * def endpoint = '/negociar/factura'
    * def cuerpo =
      """
      {
        rutEmpresa: 'KARATE-PAYERD', rolEmpresa: 'PROVEEDOR',
        tipoOperacion: { tipo: 'FACTORING', clase: 'CON_RESPONSABILIDAD' },
        rutOperador: 'op-e2e-prov',
        facturas: [ { numeroFactura: 'FE-KARATE-A5B-VIVA', cufe: 'CUFE-FE-KARATE-A5B-VIVA',
                      rutProveedor: 'KARATE-PAYERD', rutPagador: 'KARATE-PAYERB', valorFactura: 1000000.55,
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
