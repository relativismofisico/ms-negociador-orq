package com.negociadororq.application.port.output;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.negociadororq.domain.model.PendingOtp;

/**
 * Puerto de persistencia de solicitudes OTP pendientes de aprobación.
 */
public interface PendingOtpOutputPort {

    /**
     * @param pendingOtp solicitud OTP a persistir
     * @return la solicitud persistida
     */
    PendingOtp save(PendingOtp pendingOtp);

    /**
     * Fase 28d (2.9): la solicitud por su clave natural. Para solicitudes anteriores sin operador (o eventos que no
     * lo traen), solo si hay exactamente una activa que encaje; con varias, log {@code [OTP-AMBIGUO]} y vacío.
     *
     * @param idNegociacion negociación
     * @param rutEmpresa    empresa del actor (puede ser null en eventos antiguos)
     * @param rutOperador   operador (puede ser null en eventos antiguos)
     * @return la solicitud, si se identifica sin ambigüedad
     */
    Optional<PendingOtp> findByClave(String idNegociacion, String rutEmpresa, String rutOperador);

    /**
     * Fase 28d (2.9): alta atómica por la clave natural: refresca la que espera código, reemplaza una terminada sin
     * firma, y no toca una con firma en curso o ya firmada.
     *
     * @param solicitud solicitud nueva (con negociación, empresa y operador)
     * @return qué pasó y cómo quedó
     */
    ResultadoSolicitudOtp registrarSolicitud(PendingOtp solicitud);

    /**
     * Fase 28d (firma única por empresa): si otro operador de la misma empresa ya tiene el OTP validado (firma en
     * curso) o ya firmó en la negociación.
     *
     * @param idNegociacion negociación
     * @param rutEmpresa    empresa
     * @param rutOperador   operador que pide
     * @return si existe
     */
    boolean existeFirmaDeOtroOperador(String idNegociacion, String rutEmpresa, String rutOperador);

    /**
     * @param id id de la solicitud a descartar
     */
    void deleteById(String id);

    /**
     * @param estado estado de la firma (Fase 28c)
     * @param limite última transición anterior a este instante
     * @return las solicitudes en ese estado sin cambios desde el límite
     */
    List<PendingOtp> findByEstadoAndFechaActualizacionBefore(String estado, LocalDateTime limite);

    /**
     * Fase 28d (H): cuenta una respuesta definitiva de security para la clave {@code ...|vistas} (CAS: de
     * {@code vistas} a {@code vistas + 1}). Una respuesta repetida o concurrente del mismo intento no la cuenta dos
     * veces.
     *
     * @param id     id del pendiente
     * @param vistas el {@code n} con el que se armó la clave
     * @return {@code true} si avanzó; {@code false} si otro intento ya lo había avanzado
     */
    boolean registrarRespuestaOtp(String id, int vistas);
}
