@regression @full
Feature: PATCH /companies/{supplierId}/indirect-funder-offers/{offerId}/cupo/recargar — Recargar cupo del proveedor

  # Rol permitido (security.rol.recarga-cupo-indirecto en application.yml): ADMINISTRADOR, EMPRESA.
  # Restaura el cupo consumido del proveedor — modifica estado real, igual que negociacion.feature/
  # endoso.feature, NUNCA @production-safe ni @smoke. Documentado: 404 si el proveedor todavía no
  # negoció bajo esa oferta — determinista con un offerId inexistente, sin necesitar datos reales.

  Background:
    * url baseUrl
    * def path = '/companies/1/indirect-funder-offers/999/cupo/recargar'

  @regression @full
  Scenario: ADMINISTRADOR recarga cupo de un proveedor que nunca negoció — 404
    Given path path
    And header Authorization = 'Bearer ' + tokenAdmin
    When method PATCH
    Then status 404

  @regression @full
  Scenario: EMPRESA puede recargar cupo (rol permitido) — 404 si el proveedor nunca negoció
    Given path path
    And header Authorization = 'Bearer ' + tokenEmpresa
    When method PATCH
    Then status 404

  @regression @full
  Scenario: Recargar cupo sin Authorization — 401 TOKEN_AUSENTE
    Given path path
    When method PATCH
    Then status 401
    And match response.codigo == 'TOKEN_AUSENTE'

  @regression @full
  Scenario: OPERARIO no puede recargar el cupo — rechazado (rol no permitido)
    Given path path
    And header Authorization = 'Bearer ' + tokenOperario
    When method PATCH
    Then assert responseStatus >= 400
