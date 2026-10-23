@regression
Feature: Mensajes de error sin detalle interno (relativismofisico/ms-security-util#55)

  # El 401 lleva un mensaje único; el código conserva la categoría. El motivo concreto (texto de jjwt o de Spring
  # Security) queda en el log.

  Background:
    * url baseUrl

  Scenario: Token malformado — 401 TOKEN_INVALIDO con el mensaje único, sin el detalle de jjwt
    Given path '/validaciones/facturas/vencimiento'
    And header Authorization = 'Bearer no.es.un-jwt'
    And request { numeroFactura: 'FAC-X', fechaProceso: '2024-01-15' }
    When method POST
    Then status 401
    And match response.codigo == 'TOKEN_INVALIDO'
    And match response.mensaje == 'Token inválido o expirado.'
    * def texto = karate.toString(response)
    And match texto !contains 'Malformed'
    And match texto !contains 'io.jsonwebtoken'

  Scenario: Sin Authorization — 401 TOKEN_AUSENTE con el mismo mensaje único
    Given path '/validaciones/facturas/vencimiento'
    And request { numeroFactura: 'FAC-X', fechaProceso: '2024-01-15' }
    When method POST
    Then status 401
    And match response.codigo == 'TOKEN_AUSENTE'
    And match response.mensaje == 'Token inválido o expirado.'
    * def texto = karate.toString(response)
    And match texto !contains 'Full authentication'
