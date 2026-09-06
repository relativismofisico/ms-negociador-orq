@regression @full
Feature: PUT/PATCH /companies/{companyId}/cupo-propio-fondeo — Configurar cupo propio de autofondeo

  # PUT (definir) + PATCH .../endoso-grupal, .../rentabilidad-minima, .../restablecer.
  # Rol (security.rol.cupo-propio): ADMINISTRADOR, EMPRESA. + CompanyOwnershipGuard
  #   (EMPRESA solo su propia empresa). Los 4 mutan estado real: NUNCA @production-safe.
  # Seed: empresa 7004 con autofondeo `funder` 7380 + parametrization quota 1e9.

  Background:
    * url baseUrl
    * def JwtGen = Java.type('com.negociadororq.auth.JwtTestHelper')
    * def bearer = function(sub, rol){ return { Authorization: 'Bearer ' + JwtGen.generate(sub, rol, jwtSecret) } }
    * def authPropia = bearer('KARATE-DESTINO', 'EMPRESA')
    * def authAjena = bearer('KARATE-AJENA', 'EMPRESA')
    * def companyPropiaId = 7004
    * def basePath = '/companies/' + companyPropiaId + '/cupo-propio-fondeo'
    * def politicaFija = { cupoPropio: 400000, tasa: 2.5, tipoDescuento: 'FIJO', vigenciaDescuento: 'TODO_EL_PLAZO', porcentajeReserva: 0 }

  # ─── PUT — definir el cupo propio ──────────────────────────────

  @regression @full
  Scenario: La empresa define su cupo propio de autofondeo (200)
    Given path basePath
    And headers authPropia
    And request politicaFija
    When method PUT
    Then status 200
    And match response.cupoPropio == 400000
    And match response.tipoDescuento == 'FIJO'

  @regression @full
  Scenario: PUT sin tipoDescuento (obligatorio) devuelve 400
    Given path basePath
    And headers authPropia
    And request { cupoPropio: 400000, tasa: 2.5, vigenciaDescuento: 'TODO_EL_PLAZO', porcentajeReserva: 0 }
    When method PUT
    Then status 400

  # ── Bean Validation del body (@Valid + @NotNull/@PositiveOrZero del DTO) ──
  # Un caso por constraint real. Antes de agregar spring-boot-starter-validation
  # estas 5 pasaban con 200 (o 500 en el caso null); ahora dan 400 VALIDACION_FALLIDA.

  @regression @full
  Scenario: PUT con cupoPropio negativo devuelve 400 VALIDACION_FALLIDA
    Given path basePath
    And headers authPropia
    And request { cupoPropio: -1, tasa: 2.5, tipoDescuento: 'FIJO', vigenciaDescuento: 'TODO_EL_PLAZO', porcentajeReserva: 0 }
    When method PUT
    Then status 400
    And match response.codigo == 'VALIDACION_FALLIDA'

  @regression @full
  Scenario: PUT con cupoPropio nulo devuelve 400 VALIDACION_FALLIDA (antes 500)
    Given path basePath
    And headers authPropia
    And request { tasa: 2.5, tipoDescuento: 'FIJO', vigenciaDescuento: 'TODO_EL_PLAZO', porcentajeReserva: 0 }
    When method PUT
    Then status 400
    And match response.codigo == 'VALIDACION_FALLIDA'

  @regression @full
  Scenario: PUT con tasa negativa devuelve 400 VALIDACION_FALLIDA
    Given path basePath
    And headers authPropia
    And request { cupoPropio: 400000, tasa: -5, tipoDescuento: 'FIJO', vigenciaDescuento: 'TODO_EL_PLAZO', porcentajeReserva: 0 }
    When method PUT
    Then status 400
    And match response.codigo == 'VALIDACION_FALLIDA'

  @regression @full
  Scenario: PUT con porcentajeReserva negativo devuelve 400 VALIDACION_FALLIDA
    Given path basePath
    And headers authPropia
    And request { cupoPropio: 400000, tasa: 2.5, tipoDescuento: 'FIJO', vigenciaDescuento: 'TODO_EL_PLAZO', porcentajeReserva: -1 }
    When method PUT
    Then status 400
    And match response.codigo == 'VALIDACION_FALLIDA'

  @regression @full
  Scenario: PUT con diasVigencia negativo devuelve 400 VALIDACION_FALLIDA
    Given path basePath
    And headers authPropia
    And request { cupoPropio: 400000, tasa: 2.5, tipoDescuento: 'FIJO', vigenciaDescuento: 'PRIMEROS_DIAS', diasVigencia: -3, porcentajeReserva: 0 }
    When method PUT
    Then status 400
    And match response.codigo == 'VALIDACION_FALLIDA'

  @regression @full
  Scenario: PUT con tipoDescuento FINANCIERO sin tipoTasa devuelve 422
    Given path basePath
    And headers authPropia
    And request { cupoPropio: 400000, tasa: 2.5, tipoDescuento: 'FINANCIERO', vigenciaDescuento: 'TODO_EL_PLAZO', porcentajeReserva: 0 }
    When method PUT
    Then status 422

  @regression @full
  Scenario: PUT con cupoPropio por encima del cupo del administrador devuelve 422
    Given path basePath
    And headers authPropia
    And request { cupoPropio: 9999999999, tasa: 2.5, tipoDescuento: 'FIJO', vigenciaDescuento: 'TODO_EL_PLAZO', porcentajeReserva: 0 }
    When method PUT
    Then status 422

  @regression @full
  Scenario: Una empresa ajena no puede definir el cupo propio de otra (403)
    Given path basePath
    And headers authAjena
    And request politicaFija
    When method PUT
    Then status 403
    And match response.codigo == 'ACCION_NO_PERMITIDA'

  @regression @full
  Scenario: Definir el cupo propio sin Authorization devuelve 401 TOKEN_AUSENTE
    Given path basePath
    And request politicaFija
    When method PUT
    Then status 401
    And match response.codigo == 'TOKEN_AUSENTE'

  # ─── PATCH .../endoso-grupal ───────────────────────────────────

  @regression @full
  Scenario: La empresa activa el endoso grupal de su autofondeo (200)
    Given path basePath, 'endoso-grupal'
    And headers authPropia
    And request { endosoGrupal: true }
    When method PATCH
    Then status 200
    And match response.endosoGrupal == true

  @regression @full
  Scenario: endoso-grupal de otra empresa devuelve 403
    Given path basePath, 'endoso-grupal'
    And headers authAjena
    And request { endosoGrupal: true }
    When method PATCH
    Then status 403

  @regression @full
  Scenario: PATCH endoso-grupal sin el campo endosoGrupal devuelve 400 VALIDACION_FALLIDA
    Given path basePath, 'endoso-grupal'
    And headers authPropia
    And request {}
    When method PATCH
    Then status 400
    And match response.codigo == 'VALIDACION_FALLIDA'

  # ─── PATCH .../rentabilidad-minima ─────────────────────────────

  @regression @full
  Scenario: La empresa activa la rentabilidad mínima con valor (200)
    Given path basePath, 'rentabilidad-minima'
    And headers authPropia
    And request { activa: true, valor: 12.5 }
    When method PATCH
    Then status 200
    And match response.rentabilidadMinimaActiva == true

  @regression @full
  Scenario: Activar la rentabilidad mínima sin el valor devuelve 422
    Given path basePath, 'rentabilidad-minima'
    And headers authPropia
    And request { activa: true }
    When method PATCH
    Then status 422

  @regression @full
  Scenario: PATCH rentabilidad-minima sin el campo activa devuelve 400 VALIDACION_FALLIDA
    Given path basePath, 'rentabilidad-minima'
    And headers authPropia
    And request { valor: 12.5 }
    When method PATCH
    Then status 400
    And match response.codigo == 'VALIDACION_FALLIDA'

  @regression @full
  Scenario: Desactivar la rentabilidad mínima no exige valor (200)
    Given path basePath, 'rentabilidad-minima'
    And headers authPropia
    And request { activa: false }
    When method PATCH
    Then status 200
    And match response.rentabilidadMinimaActiva == false

  @regression @full
  Scenario: rentabilidad-minima de otra empresa devuelve 403
    Given path basePath, 'rentabilidad-minima'
    And headers authAjena
    And request { activa: false }
    When method PATCH
    Then status 403

  # ─── PATCH .../restablecer ─────────────────────────────────────

  @regression @full
  Scenario: La empresa restablece su cupo disponible (200)
    Given path basePath, 'restablecer'
    And headers authPropia
    When method PATCH
    Then status 200
    And match response.cupoDisponible == response.cupoPropio

  @regression @full
  Scenario: restablecer el cupo de otra empresa devuelve 403
    Given path basePath, 'restablecer'
    And headers authAjena
    When method PATCH
    Then status 403
