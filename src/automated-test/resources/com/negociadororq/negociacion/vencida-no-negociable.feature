@regression @full
Feature: Fase 28d (A5) — una factura VENCIDA o RECHAZADA no se vuelve a negociar

  # Decisión de John: ninguna factura vuelve sola a SIN_NEGOCIAR; la vencida (por el job nocturno o por el endoso
  # rechazado por vigencia) y la rechazada quedan fuera. Solo se negocia desde SIN_NEGOCIAR.
  # Seed _e2e-infra/seed/ms-negociador-orq.mongo.js: FE-KARATE-A5-VENC (VENCIDA) y FE-KARATE-A5-RECH (RECHAZADA), del
  # proveedor KARATE-CF-PROV. La respuesta es 201 con la factura en los rechazos y la negociación RECHAZADA.

  Background:
    * url baseUrl
    * def endpoint = '/negociar/factura'
    * def cuerpo =
      """
      function(numero) {
        return {
          rutEmpresa: 'KARATE-CF-PROV', rolEmpresa: 'PROVEEDOR',
          tipoOperacion: { tipo: 'FACTORING', clase: 'NORMAL' },
          rutOperador: testData.rutOperador,
          facturas: [ { numeroFactura: numero, cufe: 'CUFE-' + numero, rutProveedor: 'KARATE-CF-PROV',
                        rutPagador: 'KARATE-CF-PAG', valorFactura: 1000000.55,
                        fondeadores: [ { rutFondeador: 'KARATE-CF-FON', nombreFondeador: 'Karate CF Fondeador' } ] } ]
        };
      }
      """

  Scenario Outline: la factura <estado> no se negocia: 201 RECHAZADA y el motivo "no negociable"
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request cuerpo('<numero>')
    When method POST
    Then status 201
    And match response.estado == 'RECHAZADA'
    * def motivos = karate.toString(response.errores)
    And match response.errores == '#[1]'
    And match motivos contains 'no negociable'
    And match motivos !contains 'Exception'

    Examples:
      | numero            | estado    |
      | FE-KARATE-A5-VENC | VENCIDA   |
      | FE-KARATE-A5-RECH | RECHAZADA |
