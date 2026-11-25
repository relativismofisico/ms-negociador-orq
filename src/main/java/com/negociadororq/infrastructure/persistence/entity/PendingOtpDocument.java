package com.negociadororq.infrastructure.persistence.entity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import com.negociadororq.domain.model.AprobacionRequest;
import com.negociadororq.domain.util.Sensible;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Persistencia MongoDB de un {@link com.negociadororq.domain.model.PendingOtp}: la solicitud
 * de aprobación de un actor a la espera de que confirme con el OTP enviado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "pending_otp")
public class PendingOtpDocument {

    @Id
    private String id;
    private String idNegociacion;
    private String rutEmpresa;
    private String rutOperador;
    private String rolActor;
    private String ip;
    @ToString.Exclude
    private String otp;
    private Boolean aprobado;
    private AprobacionRequest request;
    private Boolean validadoEnOrigen;

    /** Actor (autenticado) y requestId de la solicitud del OTP: la aprobación se audita con ellos, en otro hilo. */
    private String actorTipo;
    private String actorId;
    private String actorRol;
    private Long actorEmpresaId;
    private String requestId;
    /** Fase 28c: estado de la firma (EstadoFirmaOtp); null = creada antes (cuenta como PENDIENTE_OTP). */
    private String estado;
    /** Fase 28c: referencia del OTP en ms-security-util (llega con la validación). */
    private String otpId;
    /** Fase 28c: instante de la validación en security. */
    private Instant fechaValidacion;

    /** Fase 28d (P-09): traza del reemplazo de un operador inactivo. */
    private String reemplazaA;
    /** Fase 28c: intentos de firma fallidos por causas transitorias. */
    private Integer intentosFirma;
    /** Fase 28c: último error de firma (clase y mensaje, sin datos del OTP). */
    private String ultimoError;
    /** Fase 28c: última transición de estado. */
    private LocalDateTime fechaActualizacion;
    /**
     * Fase 28c: efectos de la aprobación ya escritos en el outbox (solicitud de liquidación y correos) por esta
     * solicitud. Un reintento (reconciliador o reentrega de Kafka) no los vuelve a escribir.
     */
    private List<String> efectosEmitidos;
    /**
     * Fase 28d (H): respuestas definitivas de security a la validación por HTTP de este pendiente; es el {@code n} de la
     * clave de idempotencia {@code idNegociacion|rutEmpresa|rutOperador|n}. Solo crece (nunca se reinicia al refrescar
     * o reemplazar la solicitud). Un timeout, un 5xx o un 503 no lo avanzan. null = 0.
     */
    private Integer respuestasOtp;

    /** En el toString() el valor sale como {@code ***}: nunca en logs. Lo invoca el toString() que genera Lombok. */
    @ToString.Include(name = "otp")
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private String otpParaLog() {
        return Sensible.ocultar(otp);
    }
}
