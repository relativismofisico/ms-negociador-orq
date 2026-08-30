@regression @full
Feature: PUT/PATCH /companies/{companyId}/cupo-propio-fondeo — Configurar cupo propio de autofondeo

  # PUT (definir), PATCH .../endoso-grupal, PATCH .../rentabilidad-minima, PATCH .../restablecer.
  # Rol permitido (security.rol.cupo-propio en application.yml): ADMINISTRADOR, EMPRESA.
  # Los 4 modifican estado real — igual que negociacion.feature/endoso.feature, NUNCA @production-safe
  # ni @smoke. Devuelven 404 si la empresa no está habilitada como fondeadora de sí misma; con
  # companyId de prueba (no necesariamente habilitada en el ambiente), el happy path acepta 200,
  # 404 o 422 según corresponda, mismo patrón que validacion.feature.

  Background:
    * url baseUrl
    * def companyId = 1
    * def basePath = '/companies/' + companyId + '/cupo-propio-fondeo'

  # ─── PUT — definir cupo propio ──────────────────────────────────
  @regression @full
  Scenario: ADMINISTRADOR define el cupo propio (tipoDescuento FIJO) — 200, 404 o 422 según datos reales
    Given path basePath
    And header Authorization = 'Bearer ' + tokenAdmin
    And request { cupoPropio: 50000000, tasa: 2.5, tipoDescuento: 'FIJO', vigenciaDescuento: 'TODO_EL_PLAZO', porcentajeReserva: 0 }
    When method PUT
    Then assert responseStatus != 500
    * match [200, 404, 422] contains responseStatus

  @regression @full
  Scenario: PUT sin tipoDescuento (campo obligatorio) — 400 de validación
    Given path basePath
    And header Authorization = 'Bearer ' + tokenAdmin
    And request { cupoPropio: 50000000, tasa: 2.5, vigenciaDescuento: 'TODO_EL_PLAZO', porcentajeReserva: 0 }
    When method PUT
    Then status 400

  @regression @full
  Scenario: PUT con cupoPropio negativo — 400 de validación
    Given path basePath
    And header Authorization = 'Bearer ' + tokenAdmin
    And request { cupoPropio: -1, tasa: 2.5, tipoDescuento: 'FIJO', vigenciaDescuento: 'TODO_EL_PLAZO', porcentajeReserva: 0 }
    When method PUT
    Then status 400

  @regression @full
  Scenario: OPERARIO no puede definir el cupo propio — rechazado
    Given path basePath
    And header Authorization = 'Bearer ' + tokenOperario
    And request { cupoPropio: 50000000, tasa: 2.5, tipoDescuento: 'FIJO', vigenciaDescuento: 'TODO_EL_PLAZO', porcentajeReserva: 0 }
    When method PUT
    Then assert responseStatus >= 400

  @regression @full
  Scenario: Definir cupo propio sin Authorization — 401 TOKEN_AUSENTE
    Given path basePath
    And request { cupoPropio: 50000000, tasa: 2.5, tipoDescuento: 'FIJO', vigenciaDescuento: 'TODO_EL_PLAZO', porcentajeReserva: 0 }
    When method PUT
    Then status 401
    And match response.codigo == 'TOKEN_AUSENTE'

  # ─── PATCH .../endoso-grupal ─────────────────────────────────────
  @regression @full
  Scenario: ADMINISTRADOR activa el endoso grupal — 200 o 404
    Given path basePath, 'endoso-grupal'
    And header Authorization = 'Bearer ' + tokenAdmin
    And request { endosoGrupal: true }
    When method PATCH
    Then assert responseStatus != 500
    * match [200, 404] contains responseStatus

  @regression @full
  Scenario: endoso-grupal sin el campo obligatorio — 400 de validación
    Given path basePath, 'endoso-grupal'
    And header Authorization = 'Bearer ' + tokenAdmin
    And request {}
    When method PATCH
    Then status 400

  # ─── PATCH .../rentabilidad-minima ───────────────────────────────
  @regression @full
  Scenario: ADMINISTRADOR activa la rentabilidad mínima con valor — 200 o 404
    Given path basePath, 'rentabilidad-minima'
    And header Authorization = 'Bearer ' + tokenAdmin
    And request { activa: true, valor: 12.5 }
    When method PATCH
    Then assert responseStatus != 500
    * match [200, 404] contains responseStatus

  @regression @full
  Scenario: Activar rentabilidad mínima sin indicar el valor — 422 (aunque la empresa esté habilitada)
    Given path basePath, 'rentabilidad-minima'
    And header Authorization = 'Bearer ' + tokenAdmin
    And request { activa: true }
    When method PATCH
    Then assert responseStatus != 500
    * match [404, 422] contains responseStatus

  @regression @full
  Scenario: Desactivar la rentabilidad mínima no exige valor — 200 o 404
    Given path basePath, 'rentabilidad-minima'
    And header Authorization = 'Bearer ' + tokenAdmin
    And request { activa: false }
    When method PATCH
    Then assert responseStatus != 500
    * match [200, 404] contains responseStatus

  # ─── PATCH .../restablecer ───────────────────────────────────────
  @regression @full
  Scenario: ADMINISTRADOR restablece el cupo disponible — 200 o 404
    Given path basePath, 'restablecer'
    And header Authorization = 'Bearer ' + tokenAdmin
    When method PATCH
    Then assert responseStatus != 500
    * match [200, 404] contains responseStatus

  @regression @full
  Scenario: FONDEADOR no puede restablecer el cupo propio de otra empresa — rechazado
    Given path basePath, 'restablecer'
    And header Authorization = 'Bearer ' + tokenFondeador
    When method PATCH
    Then assert responseStatus >= 400
