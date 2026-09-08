@regression @full
Feature: GET /companies/{companyId}/cupo-propio-fondeo — Consultar cupo propio de autofondeo

  # Rol (security.rol.cupo-propio): ADMINISTRADOR, EMPRESA.
  # + CompanyOwnershipGuard.exigirEmpresaPropia: EMPRESA solo su propia empresa
  #   (resuelta por user.company_ide); ADMINISTRADOR bypasa.
  # Seed (_e2e-infra/seed/ms-negociador-orq.sql): empresa 7004 (KARATE-DESTINO)
  #   con autofondeo `funder` 7380 (funder_id==payer_id==7004) + parametrization;
  #   7005 (KARATE-AJENA) sin relación.

  Background:
    * url baseUrl
    * def JwtGen = Java.type('com.negociadororq.auth.JwtTestHelper')
    * def bearer = function(sub, rol){ return { Authorization: 'Bearer ' + JwtGen.generate(sub, rol, jwtSecret) } }
    * def authPropia = bearer('KARATE-DESTINO', 'EMPRESA')
    * def authOperarioPropio = bearer('op-destino', 'OPERARIO')
    * def authAjena = bearer('KARATE-AJENA', 'EMPRESA')
    * def authAdmin = { Authorization: '#("Bearer " + tokenAdmin)' }
    * def companyPropiaId = 7004
    * def basePath = '/companies/' + companyPropiaId + '/cupo-propio-fondeo'

  @smoke @regression @full
  Scenario: La empresa consulta su propio cupo de autofondeo (200)
    Given path basePath
    And headers authPropia
    When method GET
    Then status 200
    And match response.companyId == companyPropiaId
    And match response.cupoPropio == '#number'
    And match response.cupoMaximoAdministrador == '#number'

  @regression @full
  Scenario: ADMINISTRADOR consulta el cupo de cualquier empresa (200, bypass del guard)
    Given path basePath
    And headers authAdmin
    When method GET
    Then status 200
    And match response.companyId == companyPropiaId

  @regression @full
  Scenario: Una empresa ajena no puede consultar el cupo de otra (403)
    Given path basePath
    And headers authAjena
    When method GET
    Then status 403
    And match response.codigo == 'ACCION_NO_PERMITIDA'

  @regression @full
  Scenario: Consultar el cupo de una empresa sin autofondeo habilitado devuelve 404
    Given path '/companies/999999999/cupo-propio-fondeo'
    And headers authAdmin
    When method GET
    Then status 404
    And match response.codigo == '#string'

  @smoke @regression @full @production-safe
  Scenario: Consultar sin Authorization devuelve 401 TOKEN_AUSENTE
    Given path basePath
    When method GET
    Then status 401
    And match response.codigo == 'TOKEN_AUSENTE'

  @regression @full
  Scenario: OPERARIO no está en el grupo cupo-propio — 403 (antes del guard)
    Given path basePath
    And headers authOperarioPropio
    When method GET
    Then status 403
