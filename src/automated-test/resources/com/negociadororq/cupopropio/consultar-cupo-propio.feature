@smoke @regression @full @production-safe
Feature: GET /companies/{companyId}/cupo-propio-fondeo — Consultar cupo propio de autofondeo

  # Rol permitido (security.rol.cupo-propio en application.yml): ADMINISTRADOR, EMPRESA
  # Solo lectura, sin efectos secundarios — seguro para production-safe.
  # Devuelve 404 si la empresa no está habilitada como fondeadora de sí misma o no tiene cupo
  # general asignado; con companyId de prueba (no necesariamente habilitada en el ambiente),
  # el happy path acepta 200 o 404, mismo patrón que validacion.feature.

  Background:
    * url baseUrl
    * def companyId = 1
    * def basePath = '/companies/' + companyId + '/cupo-propio-fondeo'

  @smoke @regression @full @production-safe
  Scenario: ADMINISTRADOR consulta el cupo propio — 200 o 404 si no está habilitada
    Given path basePath
    And header Authorization = 'Bearer ' + tokenAdmin
    When method GET
    Then assert responseStatus != 500
    * match [200, 404] contains responseStatus

  @regression @full @production-safe
  Scenario: EMPRESA puede consultar su propio cupo (rol permitido)
    Given path basePath
    And header Authorization = 'Bearer ' + tokenEmpresa
    When method GET
    Then assert responseStatus != 500
    * match [200, 404] contains responseStatus

  @smoke @regression @full @production-safe
  Scenario: Consultar cupo propio sin Authorization — 401 TOKEN_AUSENTE
    Given path basePath
    When method GET
    Then status 401
    And match response.codigo == 'TOKEN_AUSENTE'

  @regression @full @production-safe
  Scenario: OPERARIO consulta cupo propio — rechazado (rol no permitido)
    Given path basePath
    And header Authorization = 'Bearer ' + tokenOperario
    When method GET
    Then assert responseStatus >= 400
    And match response.codigo == '#string'
