package com.negociadororq.application.port.output;

/** Alertas operativas de la firma por OTP (Fase 28c). */
public interface AlertaFirmaOutputPort {

    /** Registra una firma fallida (log {@code [ALERTA][FIRMA-FALLIDA]}, para el filtro de métrica de CloudWatch). */
    void firmaFallida();

    /**
     * Registra un endoso duplicado: un OTP distinto sobre una aprobación que el actor ya había hecho (log de etiqueta
     * fija {@code [ENDOSO-DUPLICADO]}, para el filtro de métrica de CloudWatch). Nunca con datos del OTP.
     *
     * @param idNegociacion negociación de la solicitud descartada
     */
    void endosoDuplicado(String idNegociacion);

    /**
     * Fase 28d (2.10): una validación de OTP de un rol que no aprueba el tipo de operación (log de etiqueta fija
     * {@code [ROL-NO-PERMITIDO]} con contador). Nunca con datos del OTP ni de otras empresas.
     *
     * @param idNegociacion negociación
     * @param rutEmpresa    empresa del actor
     * @param tipoOperacion tipo de operación de la negociación
     * @param rol           rol resuelto por el servidor
     */
    void rolNoPermitido(String idNegociacion, String rutEmpresa, String tipoOperacion, String rol);
}
