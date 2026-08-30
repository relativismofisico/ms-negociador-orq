@smoke @regression @full @production-safe
Feature: POST /tasas/conversion — Conversión de tasas

  # Rol permitido (security.rol.conversion-tasa en application.yml): ADMINISTRADOR, OPERARIO, EMPRESA, FONDEADOR
  # Operación de solo lectura / cálculo puro, sin efectos secundarios ni dependencia de datos en BD —
  # a diferencia del resto de los endpoints financieros de este repo, el happy path es 100% determinista.

  Background:
    * url baseUrl
    * def endpoint = '/tasas/conversion'
    * def schemaConversion =
      """
      {
        valorOriginal: '#number',
        tipoTasaOriginal: '#string',
        tasaEfectivaAnual: '#number',
        tasaMesVencido: '#number',
        tasaMesAnticipado: '#number'
      }
      """

  @smoke @regression @full @production-safe
  Scenario: Convertir una Tasa Mes Vencido — 200 con las 3 equivalencias, el original se conserva
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request { valor: 2.1, tipoTasa: 'TASA_MES_VENCIDO' }
    When method POST
    Then status 200
    And match response == schemaConversion
    And match response.valorOriginal == 2.1
    And match response.tipoTasaOriginal == 'TASA_MES_VENCIDO'
    # Convertir una tasa a su propia unidad es la identidad
    And match response.tasaMesVencido == 2.1

  @regression @full @production-safe
  Scenario: Convertir una Tasa Efectiva Anual — la identidad se cumple en esa unidad
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request { valor: 28.3244, tipoTasa: 'TASA_EFECTIVA_ANUAL' }
    When method POST
    Then status 200
    And match response.tasaEfectivaAnual == 28.3244

  @regression @full @production-safe
  Scenario: Convertir una Tasa Mes Anticipado — la identidad se cumple en esa unidad
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request { valor: 2.0568, tipoTasa: 'TASA_MES_ANTICIPADO' }
    When method POST
    Then status 200
    And match response.tasaMesAnticipado == 2.0568

  @regression @full @production-safe
  Scenario: OPERARIO puede convertir tasas (rol permitido)
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenOperario
    And request { valor: 2.1, tipoTasa: 'TASA_MES_VENCIDO' }
    When method POST
    Then status 200

  @regression @full @production-safe
  Scenario: FONDEADOR puede convertir tasas (rol permitido)
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenFondeador
    And request { valor: 2.1, tipoTasa: 'TASA_MES_VENCIDO' }
    When method POST
    Then status 200

  @smoke @regression @full @production-safe
  Scenario: tipoTasa con un valor no soportado — 400
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request { valor: 2.1, tipoTasa: 'TASA_INVENTADA' }
    When method POST
    Then status 400

  @regression @full @production-safe
  Scenario: Body sin valor — 400
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenAdmin
    And request { tipoTasa: 'TASA_MES_VENCIDO' }
    When method POST
    Then status 400

  @smoke @regression @full @production-safe
  Scenario: Convertir tasas sin Authorization — 401 TOKEN_AUSENTE
    Given path endpoint
    And request { valor: 2.1, tipoTasa: 'TASA_MES_VENCIDO' }
    When method POST
    Then status 401
    And match response.codigo == 'TOKEN_AUSENTE'

  @regression @full
  # No @production-safe: requiere tokenBadRol (generado con JWT_SECRET, solo dev/qa)
  Scenario: Rol no reconocido por la plataforma — rechazado
    Given path endpoint
    And header Authorization = 'Bearer ' + tokenBadRol
    And request { valor: 2.1, tipoTasa: 'TASA_MES_VENCIDO' }
    When method POST
    Then assert responseStatus >= 400
