@regression @full
Feature: GET /companies/{supplierId}/indirect-funder-offers/{funderId}/{payerId}/cupo — Cupo del proveedor

  # Rol (security.rol.negociacion): ADMINISTRADOR, EMPRESA, OPERARIO.
  # + CompanyOwnershipGuard.exigirParteDeOfertaIndirecta: el llamador debe ser una
  #   de las 3 partes (fondeador / pagador / proveedor); ADMINISTRADOR bypasa.
  # Seed (_e2e-infra): indirect_funder_offer 7360 (APROBADA, funder 7003 -> payer 7004),
  #   payer_funding_visibility 7004 habilitado. Proveedor de prueba: empresa 7006.
  #   Usuarios EMPRESA: KARATE-FUNDER (7003), KARATE-DESTINO (7004), KARATE-AJENA (7005),
  #   KARATE-PAYERB (7006). OPERARIO op-destino con company_ide 7004.

  Background:
    * url baseUrl
    * def JwtGen = Java.type('com.negociadororq.auth.JwtTestHelper')
    * def bearer = function(sub, rol){ return { Authorization: 'Bearer ' + JwtGen.generate(sub, rol, jwtSecret) } }
    * def authFunder = bearer('KARATE-FUNDER', 'EMPRESA')
    * def authPayer = bearer('KARATE-DESTINO', 'EMPRESA')
    * def authSupplier = bearer('KARATE-PAYERB', 'EMPRESA')
    * def authOperarioPayer = bearer('op-destino', 'OPERARIO')
    * def authAjena = bearer('KARATE-AJENA', 'EMPRESA')
    * def authAdmin = { Authorization: '#("Bearer " + tokenAdmin)' }
    * def path = '/companies/7006/indirect-funder-offers/7003/7004/cupo'

  @smoke @regression @full
  Scenario: El fondeador de la oferta consulta el cupo del proveedor (200)
    Given path path
    And headers authFunder
    When method GET
    Then status 200
    And match response.funderId == 7003
    And match response.payerId == 7004
    And match response.tasa == '#number'
    And match response.cupoDisponible == '#number'

  @regression @full
  Scenario: La pagadora de la oferta también puede consultarlo (200)
    Given path path
    And headers authPayer
    When method GET
    Then status 200

  @regression @full
  Scenario: El proveedor consulta su propio cupo (200)
    Given path path
    And headers authSupplier
    When method GET
    Then status 200

  @regression @full
  Scenario: El operario de una de las partes puede consultarlo (200)
    Given path path
    And headers authOperarioPayer
    When method GET
    Then status 200

  @regression @full
  Scenario: ADMINISTRADOR consulta cualquier triple (200, bypass del guard)
    Given path path
    And headers authAdmin
    When method GET
    Then status 200

  @regression @full
  Scenario: Una empresa ajena a la oferta no puede consultar el cupo (403 ACCION_NO_PERMITIDA)
    Given path path
    And headers authAjena
    When method GET
    Then status 403
    And match response.codigo == 'ACCION_NO_PERMITIDA'

  @regression @full
  Scenario: Sin oferta aprobada vigente para el par devuelve 200 con cuerpo vacío
    Given path '/companies/7006/indirect-funder-offers/7003/7005/cupo'
    And headers authFunder
    When method GET
    Then status 200

  @smoke @regression @full @production-safe
  Scenario: Consultar sin Authorization devuelve 401 TOKEN_AUSENTE
    Given path path
    When method GET
    Then status 401
    And match response.codigo == 'TOKEN_AUSENTE'

  @regression @full
  Scenario: FONDEADOR (rol) no está en el grupo negociacion — 403 ACCION_NO_PERMITIDA
    Given path path
    And header Authorization = 'Bearer ' + tokenFondeador
    When method GET
    Then status 403
    And match response.codigo == 'ACCION_NO_PERMITIDA'
