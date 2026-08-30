package com.negociadororq.application.mapper;

import com.negociadororq.domain.enums.EstadoFactura;
import com.negociadororq.domain.enums.EstadoNegociacion;
import org.springframework.stereotype.Component;

/**
 * Traduce entre el vocabulario de estado de {@link EstadoNegociacion} (interno a la
 * agregación Negociacion) y el de {@link EstadoFactura} (expuesto en el documento Factura).
 */
@Component
public class EstadoFacturaMapper {

    /**
     * El campo "estadoNegociacion" del documento Factura usa el vocabulario de
     * {@link EstadoFactura}, no el de {@link EstadoNegociacion} (interno a la
     * agregación Negociacion/FacturaNegociada, usado para calcular el estado
     * global de la negociación). Sin esta traducción, ms-liquidador-neg recibe
     * valores como "APROBADA" que no reconoce y rechaza la liquidación.
     *
     * <p>EN_APROBACION significa que la factura ya entró al proceso de firma (al
     * menos un actor requerido ya aprobó): para la pantalla de listado eso es
     * "Negociada", no "Sin negociar" — el detalle de la firma en curso se sigue
     * viendo en el campo "estadoFirma" (EstadoFirma.EN_APROBACION), que no pasa
     * por este mapeo.
     *
     * @param estadoNegociacion estado interno de la negociación
     * @return estado equivalente en el vocabulario de Factura
     */
    public EstadoFactura aEstadoFactura(EstadoNegociacion estadoNegociacion) {
        return switch (estadoNegociacion) {
            case APROBADA, ENDOSADA, LIQUIDADA, EN_APROBACION -> EstadoFactura.NEGOCIADA;
            case RECHAZADA -> EstadoFactura.RECHAZADA;
            case PENDIENTE, PARCIAL -> EstadoFactura.SIN_NEGOCIAR;
        };
    }
}
