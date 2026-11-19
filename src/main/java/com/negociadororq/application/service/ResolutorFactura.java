package com.negociadororq.application.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

import com.negociadororq.application.exception.AccionNoPermitidaException;
import com.negociadororq.application.exception.ConflictoException;
import com.negociadororq.application.exception.PeticionInvalidaException;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.ReferenciaFactura;

/**
 * Fase 28d (C): identifica una factura por CUFE, o por número + RUT del proveedor + RUT del pagador, nunca solo por
 * el número (que es el consecutivo de cada emisor). Exactamente una; si no:
 *
 * <ul>
 *   <li>{@value #FACTURA_NO_ENCONTRADA} (404): ninguna coincide;</li>
 *   <li>{@value #FACTURA_AMBIGUA} (409): más de una coincide;</li>
 *   <li>{@value #FACTURA_DE_OTRA_EMPRESA} (403): la factura no es de la empresa que negocia (proveedor en FACTORING,
 *       pagador en CONFIRMING);</li>
 *   <li>{@value #CUFE_NO_COINCIDE} (400): el CUFE enviado no corresponde al número o a las empresas enviadas;</li>
 *   <li>{@value #FACTURA_NO_COINCIDE} (409): la factura guardada en la negociación ya no coincide con la base.</li>
 * </ul>
 */
public class ResolutorFactura {

    public static final String FACTURA_NO_ENCONTRADA = "FACTURA_NO_ENCONTRADA";
    public static final String FACTURA_AMBIGUA = "FACTURA_AMBIGUA";
    public static final String FACTURA_DE_OTRA_EMPRESA = "FACTURA_DE_OTRA_EMPRESA";
    public static final String CUFE_NO_COINCIDE = "CUFE_NO_COINCIDE";
    public static final String FACTURA_NO_COINCIDE = "FACTURA_NO_COINCIDE";

    private static final Logger LOG = Logger.getLogger(ResolutorFactura.class.getName());
    private static final String FACTORING = "FACTORING";
    private static final String LA_FACTURA = "La factura ";

    private final FacturaOutputPort facturas;

    /**
     * @param facturas facturas
     */
    public ResolutorFactura(final FacturaOutputPort facturas) {
        this.facturas = facturas;
    }

    /**
     * Resuelve una factura de una solicitud de negociación.
     *
     * @param solicitada    número, y opcionalmente CUFE y RUT de proveedor y pagador, tal como los mandó el cliente
     * @param tipoOperacion FACTORING o CONFIRMING
     * @param rutEmpresa    empresa que negocia
     * @return la factura de la base
     */
    public Factura resolverSolicitud(final FacturaNegociada solicitada, final String tipoOperacion,
            final String rutEmpresa) {
        final String numero = solicitada.getNumeroFactura();
        final Factura factura;
        if (presente(solicitada.getCufe())) {
            factura = facturas.findByCufe(solicitada.getCufe()).orElseThrow(() -> new RecursoNoEncontradoException(
                    "No existe una factura con el CUFE enviado para la factura " + numero + ".", FACTURA_NO_ENCONTRADA));
            if (!igual(factura.getNumeroFactura(), numero)
                    || presente(solicitada.getRutProveedor())
                    && !mismoNit(factura.getRutProveedor(), solicitada.getRutProveedor())
                    || presente(solicitada.getRutPagador())
                    && !mismoNit(factura.getRutPagador(), solicitada.getRutPagador())) {
                throw new PeticionInvalidaException("El CUFE enviado no corresponde a la factura " + numero
                        + " de esas empresas.", CUFE_NO_COINCIDE);
            }
        } else {
            factura = porNumeroYEmpresas(solicitada, tipoOperacion, rutEmpresa);
        }
        if (!esDeLaEmpresa(factura, tipoOperacion, rutEmpresa)) {
            throw new AccionNoPermitidaException(LA_FACTURA + numero + " no pertenece a la empresa que negocia.",
                    FACTURA_DE_OTRA_EMPRESA);
        }
        return factura;
    }

    /**
     * Resuelve una factura por número y, si vienen, CUFE y RUT (consulta puntual, p. ej. la validación de
     * vencimiento). Exactamente una.
     *
     * @param numeroFactura número
     * @param cufe          CUFE, o null
     * @param rutProveedor  RUT del proveedor, o null
     * @param rutPagador    RUT del pagador, o null
     * @return la factura
     */
    public Factura resolverConsulta(final String numeroFactura, final String cufe, final String rutProveedor,
            final String rutPagador) {
        if (presente(cufe)) {
            final Factura factura = facturas.findByCufe(cufe).orElseThrow(() -> new RecursoNoEncontradoException(
                    "No existe una factura con el CUFE enviado.", FACTURA_NO_ENCONTRADA));
            if (presente(numeroFactura) && !igual(factura.getNumeroFactura(), numeroFactura)
                    || presente(rutProveedor) && !mismoNit(factura.getRutProveedor(), rutProveedor)
                    || presente(rutPagador) && !mismoNit(factura.getRutPagador(), rutPagador)) {
                throw new PeticionInvalidaException("El CUFE enviado no corresponde al número o a las empresas "
                        + "enviadas.", CUFE_NO_COINCIDE);
            }
            return factura;
        }
        return unica(numeroFactura, facturas.findAllByNumeroFactura(numeroFactura).stream()
                .filter(f -> !presente(rutProveedor) || mismoNit(f.getRutProveedor(), rutProveedor))
                .filter(f -> !presente(rutPagador) || mismoNit(f.getRutPagador(), rutPagador))
                .toList());
    }

    /**
     * La factura de una negociación, por el CUFE y los RUT guardados en ella.
     *
     * @param enNegociacion factura de la negociación
     * @return la factura de la base
     * @throws ConflictoException {@value #FACTURA_NO_COINCIDE} si no tiene CUFE o la base ya no coincide
     */
    public Factura deLaNegociacion(final FacturaNegociada enNegociacion) {
        return buscarDeLaNegociacion(enNegociacion).orElseThrow(() -> new ConflictoException(LA_FACTURA
                + enNegociacion.getNumeroFactura() + " de la negociación no coincide con la base (CUFE y empresas).",
                FACTURA_NO_COINCIDE));
    }

    /**
     * Como {@link #deLaNegociacion}, vacío en vez de excepción.
     *
     * @param enNegociacion factura de la negociación
     * @return la factura de la base, si coincide
     */
    public Optional<Factura> buscarDeLaNegociacion(final FacturaNegociada enNegociacion) {
        if (!presente(enNegociacion.getCufe())) {
            LOG.warning(() -> "[FACTURA-SIN-CUFE] la factura " + enNegociacion.getNumeroFactura()
                    + " de la negociación no tiene CUFE: no se identifica por número.");
            return Optional.empty();
        }
        return facturas.findByCufe(enNegociacion.getCufe())
                .filter(f -> igual(f.getNumeroFactura(), enNegociacion.getNumeroFactura()))
                .filter(f -> mismoNit(f.getRutProveedor(), enNegociacion.getRutProveedor()))
                .filter(f -> mismoNit(f.getRutPagador(), enNegociacion.getRutPagador()));
    }

    /**
     * La factura de una solicitud ya resuelta ({@link #resolverSolicitud} le copió el CUFE y los RUT de la base): por
     * CUFE y número.
     *
     * @param resuelta factura de la solicitud, con CUFE
     * @return la factura de la base
     * @throws ConflictoException {@value #FACTURA_NO_COINCIDE} si no tiene CUFE o el número no coincide
     */
    public Factura resuelta(final FacturaNegociada resuelta) {
        if (!presente(resuelta.getCufe())) {
            throw new ConflictoException(LA_FACTURA + resuelta.getNumeroFactura() + " no tiene CUFE.",
                    FACTURA_NO_COINCIDE);
        }
        return facturas.findByCufe(resuelta.getCufe())
                .filter(f -> igual(f.getNumeroFactura(), resuelta.getNumeroFactura()))
                .orElseThrow(() -> new ConflictoException(LA_FACTURA + resuelta.getNumeroFactura()
                        + " no coincide con su CUFE.", FACTURA_NO_COINCIDE));
    }

    /**
     * @param enNegociacion factura de la negociación
     * @return su referencia exacta (CUFE y RUT de la base)
     * @throws ConflictoException {@value #FACTURA_NO_COINCIDE} si no tiene CUFE
     */
    public static ReferenciaFactura referencia(final FacturaNegociada enNegociacion) {
        if (!presente(enNegociacion.getCufe())) {
            throw new ConflictoException(LA_FACTURA + enNegociacion.getNumeroFactura()
                    + " de la negociación no tiene CUFE.", FACTURA_NO_COINCIDE);
        }
        return new ReferenciaFactura(enNegociacion.getCufe(), enNegociacion.getRutProveedor(),
                enNegociacion.getRutPagador());
    }

    /**
     * @param nit NIT
     * @return sin puntos, guiones ni espacios, en mayúsculas
     */
    public static String normalizarNit(final String nit) {
        if (nit == null) {
            return "";
        }
        return nit.replace(".", "").replace("-", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
    }

    private Factura porNumeroYEmpresas(final FacturaNegociada solicitada, final String tipoOperacion,
            final String rutEmpresa) {
        String proveedor = solicitada.getRutProveedor();
        String pagador = solicitada.getRutPagador();
        // Si la solicitud no trae el RUT del lado de la empresa que negocia (proveedor en FACTORING, pagador en
        // CONFIRMING), es el de ella misma.
        if (FACTORING.equalsIgnoreCase(tipoOperacion)) {
            if (!presente(proveedor)) {
                proveedor = rutEmpresa;
            }
        } else if (!presente(pagador)) {
            pagador = rutEmpresa;
        }
        final String rutProveedor = proveedor;
        final String rutPagador = pagador;
        return unica(solicitada.getNumeroFactura(), facturas.findAllByNumeroFactura(solicitada.getNumeroFactura())
                .stream()
                .filter(f -> !presente(rutProveedor) || mismoNit(f.getRutProveedor(), rutProveedor))
                .filter(f -> !presente(rutPagador) || mismoNit(f.getRutPagador(), rutPagador))
                .toList());
    }

    private static Factura unica(final String numero, final List<Factura> candidatas) {
        if (candidatas.isEmpty()) {
            throw new RecursoNoEncontradoException("Factura no encontrada: " + numero, FACTURA_NO_ENCONTRADA);
        }
        if (candidatas.size() > 1) {
            LOG.warning(() -> "[FACTURA-AMBIGUA] " + candidatas.size() + " facturas con el número " + numero
                    + " y las mismas empresas.");
            throw new ConflictoException(LA_FACTURA + numero + " no se puede identificar sin ambigüedad: envíe el "
                    + "CUFE.", FACTURA_AMBIGUA);
        }
        return candidatas.get(0);
    }

    private static boolean esDeLaEmpresa(final Factura factura, final String tipoOperacion, final String rutEmpresa) {
        if (FACTORING.equalsIgnoreCase(tipoOperacion)) {
            return mismoNit(factura.getRutProveedor(), rutEmpresa);
        }
        return mismoNit(factura.getRutPagador(), rutEmpresa);
    }

    private static boolean mismoNit(final String a, final String b) {
        return presente(a) && normalizarNit(a).equals(normalizarNit(b));
    }

    private static boolean igual(final String a, final String b) {
        return a != null && a.equals(b);
    }

    private static boolean presente(final String valor) {
        return valor != null && !valor.isBlank();
    }
}
