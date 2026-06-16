package com.negociadororq.application.exception

import org.springframework.http.HttpStatus
import spock.lang.Specification

class ExceptionsSpec extends Specification {

    // ─── NegociadorException (abstracta vía subclases) ───────────────────────

    def "RecursoNoEncontradoException con mensaje y codigo"() {
        when:
        def ex = new RecursoNoEncontradoException("Factura no encontrada", "FACTURA_NO_ENCONTRADA")

        then:
        ex.message == "Factura no encontrada"
        ex.codigo == "FACTURA_NO_ENCONTRADA"
        ex.status == HttpStatus.NOT_FOUND
        ex instanceof RuntimeException
    }

    def "RecursoNoEncontradoException con mensaje simple usa codigo por defecto"() {
        when:
        def ex = new RecursoNoEncontradoException("Not found")

        then:
        ex.message == "Not found"
        ex.codigo == "RECURSO_NO_ENCONTRADO"
        ex.status == HttpStatus.NOT_FOUND
    }

    def "ConflictoException con mensaje y codigo"() {
        when:
        def ex = new ConflictoException("Endoso duplicado", "ENDOSO_DUPLICADO")

        then:
        ex.message == "Endoso duplicado"
        ex.codigo == "ENDOSO_DUPLICADO"
        ex.status == HttpStatus.CONFLICT
    }

    def "ConflictoException con mensaje simple usa codigo por defecto"() {
        when:
        def ex = new ConflictoException("Conflicto")

        then:
        ex.message == "Conflicto"
        ex.codigo == "CONFLICTO"
        ex.status == HttpStatus.CONFLICT
    }

    def "ReglaDeNegocioException con mensaje y codigo"() {
        when:
        def ex = new ReglaDeNegocioException("Cupo insuficiente", "CUPO_INSUFICIENTE")

        then:
        ex.message == "Cupo insuficiente"
        ex.codigo == "CUPO_INSUFICIENTE"
        ex.status == HttpStatus.UNPROCESSABLE_ENTITY
    }

    def "ReglaDeNegocioException con mensaje simple usa codigo por defecto"() {
        when:
        def ex = new ReglaDeNegocioException("Regla violada")

        then:
        ex.message == "Regla violada"
        ex.codigo == "REGLA_DE_NEGOCIO_VIOLADA"
        ex.status == HttpStatus.UNPROCESSABLE_ENTITY
    }

    def "PeticionInvalidaException con mensaje y codigo"() {
        when:
        def ex = new PeticionInvalidaException("Facturas requeridas", "FACTURAS_REQUERIDAS")

        then:
        ex.message == "Facturas requeridas"
        ex.codigo == "FACTURAS_REQUERIDAS"
        ex.status == HttpStatus.BAD_REQUEST
    }

    def "PeticionInvalidaException con mensaje simple usa codigo por defecto"() {
        when:
        def ex = new PeticionInvalidaException("Peticion invalida")

        then:
        ex.message == "Peticion invalida"
        ex.codigo == "PETICION_INVALIDA"
        ex.status == HttpStatus.BAD_REQUEST
    }

    def "AccionNoPermitidaException con mensaje y codigo"() {
        when:
        def ex = new AccionNoPermitidaException("No permitido", "SIN_PERMISO")

        then:
        ex.message == "No permitido"
        ex.codigo == "SIN_PERMISO"
        ex.status == HttpStatus.FORBIDDEN
    }

    def "AccionNoPermitidaException con mensaje simple usa codigo por defecto"() {
        when:
        def ex = new AccionNoPermitidaException("Accion no permitida")

        then:
        ex.message == "Accion no permitida"
        ex.codigo == "ACCION_NO_PERMITIDA"
        ex.status == HttpStatus.FORBIDDEN
    }
}