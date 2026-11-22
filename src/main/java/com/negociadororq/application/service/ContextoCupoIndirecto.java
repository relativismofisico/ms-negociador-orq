package com.negociadororq.application.service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Fase 28d (decisión de John, validación acumulada): por cada trío (fondeador, pagador, proveedor) de cupo indirecto de
 * una petición, la suma de sus facturas y el disponible al empezar. Se calcula una vez, antes de procesar la primera
 * factura; las facturas se procesan después en el orden de la petición y cada una se compara contra lo que quedaba al
 * llegar a ella (por eso el orden de la petición decide qué entra). Inmutable tras construirse; vive solo en la petición.
 */
public final class ContextoCupoIndirecto {

    /** Suma de la petición y disponible inicial de un trío. */
    public record Acumulado(BigDecimal sumaPeticion, BigDecimal disponibleInicial) {
    }

    private static final ContextoCupoIndirecto VACIO = new ContextoCupoIndirecto(Collections.emptyMap());

    private final Map<String, Acumulado> porTrio;

    private ContextoCupoIndirecto(Map<String, Acumulado> porTrio) {
        this.porTrio = porTrio;
    }

    /** @return contexto sin tríos (peticiones sin cupo indirecto, o llamadas de una sola factura) */
    public static ContextoCupoIndirecto vacio() {
        return VACIO;
    }

    /**
     * @param funderId fondeador
     * @param payerId pagador
     * @param supplierId proveedor
     * @return la clave del trío
     */
    public static String clave(Long funderId, Long payerId, Long supplierId) {
        return funderId + "|" + payerId + "|" + supplierId;
    }

    /**
     * @param clave trío
     * @return su suma y su disponible inicial, si la petición tiene facturas de ese trío
     */
    public Optional<Acumulado> de(String clave) {
        return Optional.ofNullable(porTrio.get(clave));
    }

    /** Construye el contexto acumulando factura a factura. */
    public static final class Constructor {
        private final Map<String, Acumulado> porTrio = new HashMap<>();

        /**
         * Suma una factura a su trío; el disponible inicial es el de la primera vez que aparece.
         *
         * @param clave trío
         * @param valor valor de la factura
         * @param disponibleInicial disponible del trío al empezar
         * @return este constructor
         */
        public Constructor sumar(String clave, BigDecimal valor, BigDecimal disponibleInicial) {
            porTrio.merge(clave, new Acumulado(valor, disponibleInicial),
                    (a, b) -> new Acumulado(a.sumaPeticion().add(b.sumaPeticion()), a.disponibleInicial()));
            return this;
        }

        /** @return el contexto */
        public ContextoCupoIndirecto construir() {
            return new ContextoCupoIndirecto(Map.copyOf(porTrio));
        }
    }
}
