package com.negociadororq.application.service.notificacion;

import com.negociadororq.domain.event.DestinatarioEmail;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.Negociacion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DestinatarioResolver {

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
        } else if ("CONFIRMING".equalsIgnoreCase(tipo)) {
            if (endoso.getRutProveedor() != null) {
                destinatarios.add(new DestinatarioEmail("PROVEEDOR", endoso.getRutProveedor(), "EMPRESA"));
            }
        }

        return destinatarios;
    }

    public List<DestinatarioEmail> resolverParaFirmaEndoso(Negociacion negociacion, String rutFirmante) {
        List<DestinatarioEmail> destinatarios = new ArrayList<>();
        String tipo = negociacion.getTipoOperacion().getTipo();

        if ("CONFIRMING".equalsIgnoreCase(tipo)) {
            if (negociacion.getRutPagador() != null) {
                destinatarios.add(new DestinatarioEmail("PAGADOR", negociacion.getRutPagador(), "EMPRESA"));
            }
        } else if ("FACTORING".equalsIgnoreCase(tipo)) {
            boolean esPagador = rutFirmante.equals(negociacion.getRutPagador());
            boolean esFondeador = negociacion.getRutFondeadores() != null &&
                    negociacion.getRutFondeadores().contains(rutFirmante);

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

