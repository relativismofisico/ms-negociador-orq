@smoke @regression @full
Feature: Ruta inexistente — 404 con mensaje fijo (relativismofisico/ms-security-util#59)

  Background:
    * url baseUrl

  Scenario: GET a una ruta que no existe responde 404, no 500, sin el detalle de la excepción
    Given path 'ruta-inexistente-karate'
    * def JwtGen = Java.type('com.negociadororq.auth.JwtTestHelper')
    And header Authorization = 'Bearer ' + JwtGen.generate('KARATE-FUNDER', 'EMPRESA', jwtSecret)
    When method get
    Then status 404
    * def texto = karate.toString(response)
    And match texto contains 'El recurso solicitado no existe.'
    And match texto !contains 'No static resource'
    And match texto !contains 'Error interno'
