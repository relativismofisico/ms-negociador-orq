package com.negociadororq.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import spock.lang.Specification

/**
 * Fase 28d (J-04, decisión de John): toda columna de cupo (nombre con "quota") de una entidad JPA declara DECIMAL(19,2)
 * (precision = 19, scale = 2). Sin precisión, ddl-auto=update de DEV llevaba la columna a DECIMAL(38,2) al arrancar.
 */
class ColumnasCupoEscalaSpec extends Specification {

    def "todas las columnas de cupo de las entidades declaran precision 19 y scale 2"() {
        given:
        def proveedor = new ClassPathScanningCandidateComponentProvider(false)
        proveedor.addIncludeFilter(new AnnotationTypeFilter(Entity))
        def columnas = proveedor.findCandidateComponents("com.negociadororq").collectMany { bd ->
            Class.forName(bd.beanClassName).declaredFields.findAll { it.getAnnotation(Column)?.name()?.contains("quota") }
                    .collect { [bd.beanClassName.tokenize(".").last() + "." + it.name, it.getAnnotation(Column)] }
        }

        expect:
        !columnas.isEmpty()
        columnas.findAll { n, c -> c.precision() != 19 || c.scale() != 2 }.collect { it[0] } == []
    }
}
