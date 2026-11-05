package com.negociadororq;

/**
 * Hace que Hikari valide cada conexión al prestarla del pool. Por defecto no revalida una conexión usada hace menos de
 * 500 ms ({@code aliveBypassWindowMs}): con la BD sin responder y sin cerrar la conexión (caída de red, BD congelada),
 * la primera consulta esperaba el {@code socketTimeout} completo (30 s) antes del 503. Con 0, la validación (ping del
 * driver, acotado por {@code validation-timeout}) descarta la conexión muerta y la petición responde 503 en el plazo
 * de {@code connection-timeout} (≈6 s medido en el e2e local con la MySQL pausada). No cambia el {@code socketTimeout}
 * de las consultas reales ({@code DB_SOCKET_TIMEOUT_MS}).
 *
 * <p>Es una propiedad de sistema que Hikari lee al crear el pool, no una propiedad de Spring: se fija en {@code main},
 * antes de arrancar el contexto. Un {@code -Dcom.zaxxer.hikari.aliveBypassWindowMs} explícito en la JVM se respeta.
 */
final class ValidacionConexionesBd {

    /** Propiedad de sistema de HikariCP con la ventana (ms) en la que no se revalida una conexión al prestarla. */
    static final String PROPIEDAD = "com.zaxxer.hikari.aliveBypassWindowMs";

    private ValidacionConexionesBd() {
    }

    /** Fija la ventana a 0 (validar siempre) salvo que la JVM ya traiga un valor explícito. */
    static void validarSiempreAlPrestar() {
        if (System.getProperty(PROPIEDAD) == null) {
            System.setProperty(PROPIEDAD, "0");
        }
    }
}
