package com.negociadororq.application.service;

import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;

import java.util.List;

public interface DocumentoEndosoService {

    /**
     * Genera el documento formal (HTML + PDF) del endoso, cubriendo una o varias
     * facturas: con endosoGrupal habilitado en el fondeador, un mismo Endoso puede
     * agrupar varias facturas del mismo fondeador en una sola solicitud.
     *
     * @param facturas           facturas cubiertas por este endoso, en el mismo orden que facturasNegociadas
     * @param facturasNegociadas copia embebida (en la negociación) de cada factura de facturas, mismo orden
     * @param rutOperadorFirmante username del usuario que firma el documento en representación de la
     *                            empresa proveedora, o null si todavía no se conoce (Confirming: se
     *                            resuelve más adelante, cuando el Proveedor aprueba y el documento se
     *                            regenera — ver AprobacionEndosoDescuentoServiceImpl)
     */
    void generarDocumentoFormal(Endoso endoso, Negociacion negociacion, List<Factura> facturas,
                                 List<FacturaNegociada> facturasNegociadas, String rutOperadorFirmante);
}
