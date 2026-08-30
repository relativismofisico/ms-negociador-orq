@smoke @regression @full @production-safe
Feature: GET /companies/{supplierId}/indirect-funder-offers/{funderId}/{payerId}/cupo — Consultar cupo del proveedor

  # Rol permitido (security.rol.negociacion en application.yml): ADMINISTRADOR, EMPRESA, OPERARIO.
  # Solo lectura, sin efectos secundarios — seguro para production-safe. Documentado: devuelve 200
  # con cuerpo vacío si no hay oferta aprobada vigente — no requiere datos reales pre-cargados,
  # el happy path es determinista con IDs cualquiera.

  Background:
    * url baseUrl
    * def path = '/companies/1/indirect-funder-offers/2/3/cupo'

  @smoke @regression @full @production-safe
  Scenario: ADMINISTRADOR consulta el cupo del proveedor — 200 (vacío si no hay oferta vigente)
    Given path path
    And header Authorization = 'Bearer ' + tokenAdmin
    When method GET
    Then status 200

  @regression @full @production-safe
  Scenario: EMPRESA consulta el cupo del proveedor (rol permitido) — 200
    Given path path
    And header Authorization = 'Bearer ' + tokenEmpresa
    When method GET
    Then status 200

  @regression @full @production-safe
  Scenario: OPERARIO consulta el cupo del proveedor (rol permitido) — 200
    Given path path
    And header Authorization = 'Bearer ' + tokenOperario
    When method GET
    Then status 200

  @smoke @regression @full @production-safe
  Scenario: Consultar cupo sin Authorization — 401 TOKEN_AUSENTE
    Given path path
    When method GET
    Then status 401
    And match response.codigo == 'TOKEN_AUSENTE'

  @regression @full @production-safe
  Scenario: FONDEADOR consulta el cupo del proveedor — rechazado (rol no permitido)
    Given path path
    And header Authorization = 'Bearer ' + tokenFondeador
    When method GET
    Then assert responseStatus >= 400
