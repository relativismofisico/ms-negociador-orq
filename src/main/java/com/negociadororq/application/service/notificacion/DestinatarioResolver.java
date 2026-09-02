package com.negociadororq.application.service.notificacion;

import java.util.ArrayList;
import java.util.List;

import com.negociadororq.domain.event.DestinatarioEmail;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;

/**
 * Resuelve, para cada evento notificable del ciclo de vida de una
 * negociación (creación, endoso, firma, liquidación fallida), la lista de
 * destinatarios (actor + RUT) a los que debe llegar la notificación, según
 * el tipo de operación (FACTORING o CONFIRMING).
 */
public class DestinatarioResolver {

    /**
     * @param negociacion negociación recién creada
     * @return destinatarios a notificar de su creación
     */
    public List<DestinatarioEmail> resolverParaCreacionNegociacion(Negociacion negociacion) {
        List<DestinatarioEmail> destinatarios = new ArrayList<>();
        String tipo = negociacion.getTipoOperacion().getTipo();

        if ("FACTORING".equalsIgnoreCase(tipo)) {
            if (negociacion.getRutPagador() != null) {
                destinatarios.add(new DestinatarioEmail("PAGADOR", negociacion.getRutPagador(), "EMPRESA"));
            }
            if (negociacion.getRutFondeadores() != null) {
                negociacion.getRutFondeadores().forEach(rut ->
                        destinatarios.add(new DestinatarioEmail("FONDEADOR", rut, "EMPRESA")));
            }
        } else if ("CONFIRMING".equalsIgnoreCase(tipo)) {
            negociacion.getFacturas().stream()
                    .map(FacturaNegociada::getRutProveedor)
                    .distinct()
                    .forEach(rut -> destinatarios.add(new DestinatarioEmail("PROVEEDOR", rut, "EMPRESA")));
        }

        return destinatarios;
    }

    /**
     * @param endoso endoso recién creado
     * @return destinatarios a notificar de su creación
     */
    public List<DestinatarioEmail> resolverParaEndoso(Endoso endoso) {
        List<DestinatarioEmail> destinatarios = new ArrayList<>();
        String tipo = endoso.getTipoOperacion().getTipo();

        if ("FACTORING".equalsIgnoreCase(tipo)) {
            if (endoso.getRutPagador() != null) {
                destinatarios.add(new DestinatarioEmail("PAGADOR", endoso.getRutPagador(), "EMPRESA"));
            }
            if (endoso.getRutFondeador() != null) {
                destinatarios.add(new DestinatarioEmail("FONDEADOR", endoso.getRutFondeador(), "EMPRESA"));
            }
        } else if ("CONFIRMING".equalsIgnoreCase(tipo) && endoso.getRutProveedor() != null) {
            destinatarios.add(new DestinatarioEmail("PROVEEDOR", endoso.getRutProveedor(), "EMPRESA"));
        }

        return destinatarios;
    }

    /**
     * @param negociacion negociación asociada al endoso firmado
     * @param rutFirmante RUT de la empresa que firmó (o rechazó) el endoso
     * @return destinatarios a notificar de la firma
     */
    public List<DestinatarioEmail> resolverParaFirmaEndoso(Negociacion negociacion, String rutFirmante) {
        List<DestinatarioEmail> destinatarios = new ArrayList<>();
        String tipo = negociacion.getTipoOperacion().getTipo();

        if ("CONFIRMING".equalsIgnoreCase(tipo)) {
            if (negociacion.getRutPagador() != null) {
                destinatarios.add(new DestinatarioEmail("PAGADOR", negociacion.getRutPagador(), "EMPRESA"));
            }
        } else if ("FACTORING".equalsIgnoreCase(tipo)) {
            boolean esPagador = rutFirmante.equals(negociacion.getRutPagador());
            boolean esFondeador = negociacion.getRutFondeadores() != null
                    && negociacion.getRutFondeadores().contains(rutFirmante);

            if (esPagador) {
                if (negociacion.getRutFondeadores() != null) {
                    negociacion.getRutFondeadores().forEach(rut ->
                            destinatarios.add(new DestinatarioEmail("FONDEADOR", rut, "EMPRESA")));
                }
                negociacion.getFacturas().stream()
                        .map(FacturaNegociada::getRutProveedor)
                        .distinct()
                        .forEach(rut -> destinatarios.add(new DestinatarioEmail("PROVEEDOR", rut, "EMPRESA")));
            } else if (esFondeador) {
                if (negociacion.getRutPagador() != null) {
                    destinatarios.add(new DestinatarioEmail("PAGADOR", negociacion.getRutPagador(), "EMPRESA"));
                }
                negociacion.getFacturas().stream()
                        .map(FacturaNegociada::getRutProveedor)
                        .distinct()
                        .forEach(rut -> destinatarios.add(new DestinatarioEmail("PROVEEDOR", rut, "EMPRESA")));
            }
        }

        return destinatarios;
    }

    /**
     * @param negociacion negociación cuya liquidación falló
     * @return destinatarios a notificar de la falla
     */
    public List<DestinatarioEmail> resolverParaLiquidacionFallida(Negociacion negociacion) {
        List<DestinatarioEmail> destinatarios = new ArrayList<>();
        String tipo = negociacion.getTipoOperacion().getTipo();

        if ("FACTORING".equalsIgnoreCase(tipo)) {
            if (negociacion.getRutPagador() != null) {
                destinatarios.add(new DestinatarioEmail("PAGADOR", negociacion.getRutPagador(), "EMPRESA"));
            }
            negociacion.getFacturas().stream()
                    .map(FacturaNegociada::getRutProveedor)
                    .distinct()
                    .forEach(rut -> destinatarios.add(new DestinatarioEmail("PROVEEDOR", rut, "EMPRESA")));
            if (negociacion.getRutFondeadores() != null) {
                negociacion.getRutFondeadores().forEach(rut ->
                        destinatarios.add(new DestinatarioEmail("FONDEADOR", rut, "EMPRESA")));
            }
        } else if ("CONFIRMING".equalsIgnoreCase(tipo)) {
            negociacion.getFacturas().stream()
                    .map(FacturaNegociada::getRutProveedor)
                    .distinct()
                    .forEach(rut -> destinatarios.add(new DestinatarioEmail("PROVEEDOR", rut, "EMPRESA")));
            if (negociacion.getRutPagador() != null) {
                destinatarios.add(new DestinatarioEmail("PAGADOR", negociacion.getRutPagador(), "EMPRESA"));
            }
        }

        return destinatarios;
    }
}

