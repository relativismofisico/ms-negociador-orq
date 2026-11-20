package com.negociadororq.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fase 28d (A2): lo que la creación de una negociación descontó exactamente de cada cupo para una factura, para
 * devolver exactamente eso (ni más ni menos, al centavo) en cualquier reversión. Se guarda como JSON en
 * {@code operacion_pendiente.datos} de la operación CREAR_NEGOCIACION ({@code negociacion|cufe}), en la misma
 * transacción MySQL que los descuentos. Los montos van como texto para conservar el {@link BigDecimal} sin redondeos.
 *
 * <p>Con el descuento va el contexto mínimo de la operación ({@link Contexto}: tipo, empresa que la inició, contraparte
 * y número de la factura), sin datos sensibles: lo necesita el correo de la reversión (plantilla A) cuando la
 * negociación no llegó a existir en Mongo.</p>
 *
 * @param descuentos cada cupo tocado y su monto
 * @param contexto   contexto de la operación, o null (registros sin contexto)
 */
public record DescuentoCupos(List<Descuento> descuentos, Contexto contexto) {

    /** Versión del formato JSON. */
    static final int VERSION = 1;

    private static final int GRUPO_TIPO = 1;
    private static final int GRUPO_A = 2;
    private static final int GRUPO_B = 3;
    private static final int GRUPO_MONTO = 4;
    private static final int GRUPO_CTX_TIPO = 1;
    private static final int GRUPO_CTX_EMPRESA = 2;
    private static final int GRUPO_CTX_CONTRAPARTE = 3;
    private static final int GRUPO_CTX_NUMERO = 4;

    private static final String TEXTO = "\"((?:[^\"\\\\]|\\\\.)*)\"";
    private static final Pattern ELEMENTO = Pattern.compile(
            "\\{\"tipo\":\"([A-Z_]+)\",\"a\":(\\d+|null),\"b\":(\\d+|null),\"monto\":\"(-?\\d+(?:\\.\\d+)?)\"\\}");
    private static final Pattern CABECERA = Pattern.compile(
            "^\\{\"v\":(\\d+),\"descuentos\":\\[(.*?)\\](?:,\"contexto\":(\\{.*\\}))?\\}$");
    private static final Pattern CONTEXTO = Pattern.compile("^\\{\"tipoOperacion\":" + TEXTO + ",\"rutEmpresa\":" + TEXTO
            + ",\"rutContraparte\":" + TEXTO + ",\"numeroFactura\":" + TEXTO + "\\}$");

    /** Qué cupo se tocó. */
    public enum Tipo {
        /** {@code user_quota} del operador que negoció ({@code a} = userId). */
        OPERADOR,
        /** {@code empresa_quota} de la empresa que inició la negociación ({@code a} = companyId). */
        EMPRESA,
        /** Asignación directa de FACTORING ({@code a} = funderId, {@code b} = supplierId). */
        FONDEADOR_FACTORING,
        /** Asignación de CONFIRMING ({@code a} = funderId, {@code b} = payerId). */
        FONDEADOR_CONFIRMING,
        /** Fila de consumo de la oferta indirecta de FACTORING ({@code a} = ofertaId, {@code b} = supplierId). */
        INDIRECTO
    }

    /**
     * Un descuento.
     *
     * @param tipo  cupo tocado
     * @param a     primer id (ver {@link Tipo})
     * @param b     segundo id, o null
     * @param monto monto descontado, exacto
     */
    public record Descuento(Tipo tipo, Long a, Long b, BigDecimal monto) {

        /**
         * @throws NullPointerException si falta el tipo, el primer id o el monto
         */
        public Descuento {
            Objects.requireNonNull(tipo, "tipo");
            Objects.requireNonNull(a, "a");
            Objects.requireNonNull(monto, "monto");
        }
    }

    /**
     * Contexto de la operación para el correo de la reversión.
     *
     * @param tipoOperacion  FACTORING o CONFIRMING
     * @param rutEmpresa     empresa que inició la negociación
     * @param rutContraparte su contraparte (el pagador en FACTORING, el proveedor en CONFIRMING)
     * @param numeroFactura  número de la factura (se muestra; la identidad es el CUFE de la clave)
     */
    public record Contexto(String tipoOperacion, String rutEmpresa, String rutContraparte, String numeroFactura) {
    }

    /**
     * @param descuentos cada cupo tocado y su monto
     * @param contexto   contexto de la operación, o null
     */
    public DescuentoCupos {
        descuentos = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(descuentos, "descuentos")));
    }

    /**
     * @param descuentos cada cupo tocado y su monto (sin contexto)
     */
    public DescuentoCupos(final List<Descuento> descuentos) {
        this(descuentos, null);
    }

    /**
     * @return el JSON de {@code operacion_pendiente.datos} (sin campos sensibles)
     */
    public String aJson() {
        final StringBuilder sb = new StringBuilder("{\"v\":").append(VERSION).append(",\"descuentos\":[");
        for (int i = 0; i < descuentos.size(); i++) {
            final Descuento d = descuentos.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"tipo\":\"").append(d.tipo().name()).append("\",\"a\":").append(d.a())
                    .append(",\"b\":").append(d.b()).append(",\"monto\":\"").append(d.monto().toPlainString())
                    .append("\"}");
        }
        sb.append(']');
        if (contexto != null) {
            sb.append(",\"contexto\":{\"tipoOperacion\":").append(texto(contexto.tipoOperacion()))
                    .append(",\"rutEmpresa\":").append(texto(contexto.rutEmpresa()))
                    .append(",\"rutContraparte\":").append(texto(contexto.rutContraparte()))
                    .append(",\"numeroFactura\":").append(texto(contexto.numeroFactura())).append('}');
        }
        return sb.append('}').toString();
    }

    /**
     * @param json lo que escribió {@link #aJson()}
     * @return el registro, exacto
     * @throws IllegalArgumentException si el JSON no tiene este formato (no se adivina nada)
     */
    public static DescuentoCupos deJson(final String json) {
        if (json == null) {
            throw new IllegalArgumentException("sin registro de lo descontado");
        }
        final Matcher cabecera = CABECERA.matcher(json.trim());
        if (!cabecera.matches() || Integer.parseInt(cabecera.group(1)) != VERSION) {
            throw new IllegalArgumentException("registro de lo descontado con otro formato");
        }
        final String cuerpo = cabecera.group(2);
        final List<Descuento> lista = new ArrayList<>();
        final Matcher m = ELEMENTO.matcher(cuerpo);
        int fin = 0;
        while (m.find()) {
            if (m.start() != fin && !",".equals(cuerpo.substring(fin, m.start()))) {
                throw new IllegalArgumentException("registro de lo descontado con otro formato");
            }
            lista.add(new Descuento(Tipo.valueOf(m.group(GRUPO_TIPO)), id(m.group(GRUPO_A)), id(m.group(GRUPO_B)),
                    new BigDecimal(m.group(GRUPO_MONTO))));
            fin = m.end();
        }
        if (fin != cuerpo.length()) {
            throw new IllegalArgumentException("registro de lo descontado con otro formato");
        }
        Contexto ctx = null;
        if (cabecera.group(3) != null) {
            final Matcher c = CONTEXTO.matcher(cabecera.group(3));
            if (!c.matches()) {
                throw new IllegalArgumentException("registro de lo descontado con otro formato");
            }
            ctx = new Contexto(sinEscape(c.group(GRUPO_CTX_TIPO)), sinEscape(c.group(GRUPO_CTX_EMPRESA)),
                    sinEscape(c.group(GRUPO_CTX_CONTRAPARTE)), sinEscape(c.group(GRUPO_CTX_NUMERO)));
        }
        return new DescuentoCupos(lista, ctx);
    }

    private static String texto(final String valor) {
        final String v = valor == null ? "" : valor;
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String sinEscape(final String valor) {
        return valor.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static Long id(final String texto) {
        if ("null".equals(texto)) {
            return null;
        }
        return Long.valueOf(texto);
    }
}
