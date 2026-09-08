@regression @full
Feature: PATCH /companies/{supplierId}/indirect-funder-offers/{offerId}/cupo/recargar — Recargar cupo del proveedor

  # Rol (security.rol.recarga-cupo-indirecto): ADMINISTRADOR, EMPRESA.
  # + CompanyOwnershipGuard.exigirFondeadorDeOferta: solo la empresa fondeadora de la
  #   oferta (o ADMINISTRADOR) puede disparar la recarga.
  # Modifica estado real -> NUNCA @production-safe ni @smoke.
  # Seed (_e2e-infra): indirect_funder_offer 7360 (APROBADA, funder 7003). No hay fila
  #   indirect_funder_offer_supplier_quota para (7360, 7006) -> 404 "no negoció" tras
  #   pasar el guard (determinista, no necesita más datos).

  Background:
    * url baseUrl
    * def JwtGen = Java.type('com.negociadororq.auth.JwtTestHelper')
    * def bearer = function(sub, rol){ return { Authorization: 'Bearer ' + JwtGen.generate(sub, rol, jwtSecret) } }
    * def authFondeadorOferta = bearer('KARATE-FUNDER', 'EMPRESA')
    * def authPagadora = bearer('KARATE-DESTINO', 'EMPRESA')
    * def authAjena = bearer('KARATE-AJENA', 'EMPRESA')
    * def authOperario = bearer('op-destino', 'OPERARIO')
    * def authAdmin = { Authorization: '#("Bearer " + tokenAdmin)' }
    * def path = '/companies/7006/indirect-funder-offers/7360/cupo/recargar'

  @regression @full
  Scenario: El fondeador de la oferta pasa el guard; 404 porque el proveedor nunca negoció
    Given path path
    And headers authFondeadorOferta
    When method PATCH
    Then status 404
    And match response.codigo == 'RECURSO_NO_ENCONTRADO'

  @regression @full
  Scenario: ADMINISTRADOR pasa el guard (bypass); 404 porque el proveedor nunca negoció
    Given path path
    And headers authAdmin
    When method PATCH
    Then status 404
    And match response.codigo == 'RECURSO_NO_ENCONTRADO'

  @regression @full
  Scenario: La pagadora de la oferta no es la fondeadora — 403 ACCION_NO_PERMITIDA
    Given path path
    And headers authPagadora
    When method PATCH
    Then status 403
    And match response.codigo == 'ACCION_NO_PERMITIDA'

  @regression @full
  Scenario: Una empresa ajena a la oferta no puede recargar — 403 ACCION_NO_PERMITIDA
    Given path path
    And headers authAjena
    When method PATCH
    Then status 403
    And match response.codigo == 'ACCION_NO_PERMITIDA'

  @regression @full
  Scenario: Oferta inexistente — 404 OFERTA_INDIRECTA_NO_ENCONTRADA (el guard la resuelve)
    Given path '/companies/7006/indirect-funder-offers/999999/cupo/recargar'
    And headers authFondeadorOferta
    When method PATCH
    Then status 404
    And match response.codigo == 'OFERTA_INDIRECTA_NO_ENCONTRADA'

  @regression @full
  Scenario: Recargar sin Authorization — 401 TOKEN_AUSENTE
    Given path path
    When method PATCH
    Then status 401
    And match response.codigo == 'TOKEN_AUSENTE'

  @regression @full
  Scenario: OPERARIO no está en el grupo recarga-cupo-indirecto — 403 ACCION_NO_PERMITIDA
    Given path path
    And headers authOperario
    When method PATCH
    Then status 403
    And match response.codigo == 'ACCION_NO_PERMITIDA'

  @regression @full
  Scenario: FONDEADOR (rol) no está en el grupo recarga-cupo-indirecto — 403 ACCION_NO_PERMITIDA
    Given path path
    And header Authorization = 'Bearer ' + tokenFondeador
    When method PATCH
    Then status 403
    And match response.codigo == 'ACCION_NO_PERMITIDA'

  @regression @full
  Scenario: GET en vez de PATCH — 405 METODO_NO_PERMITIDO
    Given path path
    And headers authFondeadorOferta
    When method GET
    Then status 405
    And match response.codigo == 'METODO_NO_PERMITIDO'
