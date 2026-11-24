@regression @full
Feature: Fase 28d (punto 1.2, decisión de John) — SIN_CUPO_INDIRECTO con disponible, requerido y faltante exactos

  # Seed:
  # - _e2e-infra/seed/ms-negociador-orq.mongo.js: FE-KARATE-SIN-CUPO-IND, del proveedor KARATE-PAYERD (7008, operador
  #   op-e2e-prov) con el pagador KARATE-DESTINO (7004), neto 2.000.000,20, SIN_NEGOCIAR.
  # - ms-negociador-orq.sql: sin asignación directa del fondeador KARATE-FUNDER (7003) al proveedor 7008, así que va por
  #   el cupo indirecto de la oferta 7360 (7003 -> 7004), con 1.500.000,55 disponibles para 7008.
  # El rechazo no descuenta nada: el escenario se puede repetir y el cupo sigue igual (centavos.feature lo lee).

  Background:
    * url baseUrl
    * def cuerpo =
      """
      {
        rutEmpresa: 'KARATE-PAYERD', rolEmpresa: 'PROVEEDOR',
        tipoOperacion: { tipo: 'FACTORING', clase: 'CON_RESPONSABILIDAD' },
        rutOperador: 'op-e2e-prov',
        facturas: [ { numeroFactura: 'FE-KARATE-SIN-CUPO-IND', cufe: 'CUFE-FE-KARATE-SIN-CUPO-IND',
                      rutProveedor: 'KARATE-PAYERD', rutPagador: 'KARATE-DESTINO', valorFactura: 2000000.20,
                      fondeadores: [ { rutFondeador: 'KARATE-FUNDER', nombreFondeador: 'Karate Fondeador' } ] } ]
      }
      """

  Scenario: la factura que no cabe en el cupo indirecto queda fuera con los montos exactos y los nombres
    Given path '/negociar/factura'
    And header Authorization = 'Bearer ' + tokenAdmin
    And request cuerpo
    When method POST
    Then status 201
    And match response.estado == 'RECHAZADA'
    And match response.facturas == '#[0]'
    And match response.rechazos == '#[1]'
    * def r = response.rechazos[0]
    And match r.numeroFactura == 'FE-KARATE-SIN-CUPO-IND'
    And match r.codigo == 'SIN_CUPO_INDIRECTO'
    # Montos exactos al centavo con BigDecimal.compareTo: el cuerpo de la petición pasa por un número de JavaScript, que
    # envía 2000000.20 como 2000000.2 (mismo valor, otra escala); comparar el texto fallaba aunque el monto es el exacto.
    * def igual = function(a, b){ return new java.math.BigDecimal(a + '').compareTo(new java.math.BigDecimal(b)) == 0 }
    * assert igual(r.cupoIndirecto.disponible, '1500000.55')
    * assert igual(r.cupoIndirecto.requerido, '2000000.20')
    * assert igual(r.cupoIndirecto.faltante, '499999.65')
    * assert igual(r.cupoIndirecto.sumaPeticion, '2000000.20')
    * assert igual(r.cupoIndirecto.disponibleInicial, '1500000.55')
    And match r.cupoIndirecto.fondeador == 'Karate Fondeador'
    And match r.cupoIndirecto.pagador == 'Karate Destino'
    And match r.mensaje == 'La factura FE-KARATE-SIN-CUPO-IND por $2.000.000,20 no se incluyó: el cupo de factoring que Karate Fondeador ofrece a los proveedores de Karate Destino tiene $1.500.000,55 disponibles para ti; faltan $499.999,65. Puedes pedirle a Karate Fondeador que recargue o amplíe tu cupo.'
    And match response.errores == [ '#(r.mensaje)' ]

  Scenario: el rechazo no tocó el cupo del proveedor indirecto
    Given path '/companies/7008/indirect-funder-offers/7003/7004/cupo'
    And header Authorization = 'Bearer ' + tokenAdmin
    When method GET
    Then status 200
    And match response.cupoDisponible == 1500000.55
