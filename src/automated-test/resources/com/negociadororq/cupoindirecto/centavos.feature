@regression @full
Feature: Cupo indirecto del proveedor con centavos — lectura exacta (Fase 28d, punto 5)

  # Seed (_e2e-infra/seed/ms-negociador-orq.sql, bloque "centavos"): oferta 7360 (funder 7003 -> payer 7004) con el
  # proveedor 7008 en cupo disponible 500000.55 (techo 1000000.00 = plantilla de la oferta, como lo deja la V9; Fase 28d H).
  # Crear y revertir una negociación con centavos NO se prueba aquí: el negociador no expone por API el cupo del
  # operador ni el de la empresa (solo este del proveedor indirecto). Lo cubre el tramo e2e de A2 (e2e_28d_a2.sh), que
  # lee los cupos en MySQL: 1000.10 baja y se devuelve exacto en operador, empresa, fondeo directo e indirecto.

  Background:
    * url baseUrl
    * def authAdmin = { Authorization: '#("Bearer " + tokenAdmin)' }

  Scenario: El cupo disponible del proveedor indirecto se lee exacto al centavo
    Given path '/companies/7008/indirect-funder-offers/7003/7004/cupo'
    And headers authAdmin
    When method GET
    Then status 200
    And match response.funderId == 7003
    And match response.payerId == 7004
    And match response.cupoDisponible == 500000.55
