package com.negociadororq.application.mapper

import com.negociadororq.domain.enums.EstadoFactura
import com.negociadororq.domain.enums.EstadoNegociacion
import spock.lang.Specification
import spock.lang.Unroll

class EstadoFacturaMapperSpec extends Specification {

    def mapper = new EstadoFacturaMapper()

    @Unroll
    def "aEstadoFactura(#estadoNegociacion) mapea a #esperado"() {
        expect:
        mapper.aEstadoFactura(estadoNegociacion) == esperado

        where:
        estadoNegociacion                 || esperado
        EstadoNegociacion.APROBADA        || EstadoFactura.NEGOCIADA
        EstadoNegociacion.ENDOSADA        || EstadoFactura.NEGOCIADA
        EstadoNegociacion.LIQUIDADA       || EstadoFactura.NEGOCIADA
        EstadoNegociacion.EN_APROBACION   || EstadoFactura.NEGOCIADA
        EstadoNegociacion.RECHAZADA       || EstadoFactura.RECHAZADA
        EstadoNegociacion.PENDIENTE       || EstadoFactura.SIN_NEGOCIAR
        EstadoNegociacion.PARCIAL         || EstadoFactura.SIN_NEGOCIAR
    }

    def "una factura con al menos un actor requerido aprobado (EN_APROBACION) ya no debe verse como 'sin negociar'"() {
        // Regresión: FECI3644 tenía una aprobación de FONDEADOR registrada (EstadoNegociacion.EN_APROBACION)
        // pero el listado de facturas seguía mostrándola como SIN_NEGOCIAR porque el mapeo viejo
        // colapsaba EN_APROBACION al mismo valor que PENDIENTE.
        expect:
        mapper.aEstadoFactura(EstadoNegociacion.EN_APROBACION) != EstadoFactura.SIN_NEGOCIAR
        mapper.aEstadoFactura(EstadoNegociacion.EN_APROBACION) == EstadoFactura.NEGOCIADA
    }
}
