package com.negociadororq.application.exception;

/**
 * Fase 28d (2.10): el rol real del actor en la negociación (resuelto en el servidor por su RUT sobre las facturas) no
 * es uno de los que aprueban ese tipo de operación (FACTORING: Pagador y Fondeador; CONFIRMING: Proveedor). El mensaje
 * no lleva datos de otras empresas.
 */
public class RolNoPermitidoException extends ReglaDeNegocioException {

    /** Código de error de la respuesta. */
    public static final String CODIGO = "ROL_NO_PERMITIDO_PARA_OPERACION";

    private static final long serialVersionUID = 1L;

    private final String tipoOperacion;
    private final String rol;

    /**
     * @param tipoOperacion tipo de operación de la negociación
     * @param rol           rol resuelto por el servidor
     */
    public RolNoPermitidoException(final String tipoOperacion, final String rol) {
        super("Tu empresa participa en esta negociación como " + rol + ", y ese rol no aprueba operaciones de "
                + tipoOperacion + ".", CODIGO);
        this.tipoOperacion = tipoOperacion;
        this.rol = rol;
    }

    /**
     * @return tipo de operación de la negociación
     */
    public String getTipoOperacion() {
        return tipoOperacion;
    }

    /**
     * @return rol resuelto por el servidor
     */
    public String getRol() {
        return rol;
    }
}
