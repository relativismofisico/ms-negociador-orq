package com.negociadororq.web.exception;

import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Reconoce un fallo de infraestructura de datos (base caída o sin conexión, timeout) en la cadena de causas de una
 * excepción, para responder 503 en lugar de 500. Los errores de datos normales (violación de integridad, resultado
 * no único, bloqueo optimista) no son fallos de infraestructura y siguen en sus handlers.
 */
public final class FalloInfraestructuraDatos {

    /** Mensaje fijo de la respuesta 503: sin detalle interno. */
    public static final String MENSAJE = "Servicio no disponible temporalmente. Intente de nuevo en unos momentos.";

    /** Tope de causas a recorrer, por si una cadena fuera cíclica. */
    private static final int PROFUNDIDAD_MAXIMA = 20;

    private FalloInfraestructuraDatos() {
    }

    /**
     * Indica si la excepción o alguna de sus causas es un fallo de infraestructura de datos.
     *
     * @param ex excepción a revisar
     * @return {@code true} si la base no estaba disponible o no respondió a tiempo
     */
    public static boolean es(final Throwable ex) {
        return causa(ex) != null;
    }

    /**
     * Devuelve la primera excepción de la cadena que es un fallo de infraestructura de datos.
     *
     * @param ex excepción a revisar
     * @return la causa de infraestructura, o {@code null} si no la hay
     */
    public static Throwable causa(final Throwable ex) {
        Throwable actual = ex;
        for (int i = 0; actual != null && i < PROFUNDIDAD_MAXIMA; i++) {
            if (esDeInfraestructura(actual)) {
                return actual;
            }
            final Throwable siguiente = actual.getCause();
            if (siguiente == actual) {
                return null;
            }
            actual = siguiente;
        }
        return null;
    }

    /**
     * Nombre simple de la causa de infraestructura, para el log (sin mensaje: puede traer SQL o nombres de tablas).
     *
     * @param ex excepción a revisar
     * @return nombre simple de la causa, o el de la propia excepción si no la hay
     */
    public static String nombreCausa(final Throwable ex) {
        Throwable causa = causa(ex);
        if (causa == null) {
            causa = ex;
        }
        return causa.getClass().getSimpleName();
    }

    private static boolean esDeInfraestructura(final Throwable t) {
        return t instanceof DataAccessResourceFailureException
                || t instanceof CannotCreateTransactionException
                || t instanceof QueryTimeoutException
                || t instanceof TransientDataAccessResourceException
                || t instanceof RecoverableDataAccessException
                || t instanceof SQLTransientConnectionException
                || t instanceof SQLNonTransientConnectionException
                || t instanceof SQLRecoverableException
                || t instanceof SQLTimeoutException;
    }
}
