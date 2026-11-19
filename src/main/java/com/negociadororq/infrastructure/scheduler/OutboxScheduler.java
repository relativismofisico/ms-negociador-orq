package com.negociadororq.infrastructure.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.factoring.commons.auditoria.AuditoriaAccionEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.negociadororq.domain.event.LiquidacionSolicitadaEvent;
import com.negociadororq.domain.event.NotificacionEmailEvent;
import com.negociadororq.domain.event.OtpRequestEvent;
import com.negociadororq.domain.event.OtpValidateRequestEvent;
import com.negociadororq.domain.event.OtpValidateResponseEvent;
import com.negociadororq.infrastructure.kafka.producer.OutboxEventPublisher;
import com.negociadororq.infrastructure.persistence.entity.OutboxEventEntity;
import com.negociadororq.infrastructure.persistence.repository.OutboxEventRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica periódicamente los eventos pendientes del patrón Outbox de este servicio, reintentando hasta un máximo de
 * intentos. Lo que no se puede publicar (intentos agotados, topic no registrado) pasa a un estado terminal visible
 * ({@code fallido}), nunca a enviado: no hay pérdida silenciosa. Limpia a diario los enviados antiguos.
 *
 * <p>Fase 28d (2.4): {@code otp.validate} lleva el código tecleado. Desde la A1 el negociador ya no lo produce (el
 * código va por HTTP a security), pero puede quedar alguno de antes del despliegue. Para ese tipo: los enviados se
 * borran en una limpieza propia, cada 10 minutos ({@code outbox.limpieza.otp-validacion.*}); los fallidos NO se borran
 * (se conservan para revisión manual, con la alerta {@code [OUTBOX-FALLIDO]}), pero se les retira el código del
 * payload ({@code [OUTBOX-OTP-RETIRADO]}). Los demás tipos siguen con la retención diaria de siempre.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {

    private final OutboxEventRepository repository;
    private final OutboxEventPublisher publisher;
    private final ObjectMapper objectMapper;

    @Value("${topics.otp-request}")
    private String topicRequestOtp;

    @Value("${topics.otp-validate}")
    private String topicValidateOtp;

    @Value("${topics.otp-validate-response}")
    private String topicValidateResponse;

    @Value("${topics.liquidacion}")
    private String liquidacionFactura;

    @Value("${topics.notificacion-email}")
    private String notificacionEmail;

    @Value("${auditoria.topic:auditoria.accion}")
    private String topicAuditoria;

    // Propietario de este MS
    private static final String MS_PROPIETARIO = "ms-negociador-orq";

    private Map<String, Class<?>> eventMap;
    static final int MAX_INTENTOS = 5;

    /** Tamaño del lote, del más antiguo al más nuevo. */
    private static final PageRequest LOTE = PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "fechaCreacion"));

    /** Días que se conservan los eventos enviados ({@code outbox.limpieza.retencion-dias}). */
    // No final: Spring la asigna por @Value después del constructor; el valor inicial sirve a quien lo construya a mano.
    @SuppressWarnings("PMD.ImmutableField")
    @Value("${outbox.limpieza.retencion-dias:30}")
    private int retencionDias = RETENCION_DIAS_POR_DEFECTO;

    private static final int RETENCION_DIAS_POR_DEFECTO = 30;

    /** Fase 28d (2.4): campo del payload de {@code otp.validate} con el código. */
    static final String CAMPO_OTP = "otp";
    /** Fase 28d (2.4): marca en el payload de un fallido al que se le retiró el código. */
    static final String CAMPO_OTP_RETIRADO = "otpRetirado";

    /**
     * Fase 28d (2.4): antigüedad máxima de un {@code otp.validate} ya enviado
     * ({@code outbox.limpieza.otp-validacion.retencion}, 0 por defecto: se borra en la siguiente limpieza).
     */
    @SuppressWarnings("PMD.ImmutableField")
    @Value("${outbox.limpieza.otp-validacion.retencion:PT0S}")
    private Duration retencionValidacionOtp = Duration.ZERO;

    /**
     * Construye el mapa de topic → clase de evento usado para deserializar el payload guardado.
     */
    @PostConstruct
    public void init() {

        eventMap = new HashMap<>();

        eventMap.put(topicRequestOtp, OtpRequestEvent.class);
        eventMap.put(topicValidateOtp, OtpValidateRequestEvent.class);
        eventMap.put(topicValidateResponse, OtpValidateResponseEvent.class);
        eventMap.put(liquidacionFactura, LiquidacionSolicitadaEvent.class);
        eventMap.put(notificacionEmail, NotificacionEmailEvent.class);
        eventMap.put(topicAuditoria, AuditoriaAccionEvent.class);
        log.info("OutboxScheduler inicializado con eventos: {}", eventMap.keySet());
    }


    @Scheduled(fixedDelay = 5000)
    @SchedulerLock(name = "ms-negociador-orq_outboxScheduler_procesarOutbox",
            lockAtLeastFor = "PT4S", lockAtMostFor = "PT55S")
    @Transactional
    public void procesarOutbox() {

        log.info("Scheduler ejecutándose...");

        List<OutboxEventEntity> eventos = repository.findPendientes(MS_PROPIETARIO, LOTE);

        log.info("Eventos pendientes: {}", eventos.size());

        for (OutboxEventEntity evento : eventos) {
            // 🔹 Filtrar por propietario
            if (!MS_PROPIETARIO.equals(evento.getMsPropietario())) {
                log.info("Evento {} ignorado, pertenece a {}", evento.getId(), evento.getMsPropietario());
                continue;
            }

            try {

                if (evento.getIntentos() >= MAX_INTENTOS) {
                    marcarComoFallido(evento, "MAX_INTENTOS");
                    continue;
                }

                String tipoEvento = evento.getTipoEvento();

                if (tipoEvento == null) {

                    log.error("Evento {} no tiene tipoEvento", evento.getId());

                    incrementarIntentos(evento);
                    continue;
                }

                Class<?> eventClass = eventMap.get(tipoEvento);

                if (eventClass == null) {
                    marcarComoFallido(evento, "TOPIC_DESCONOCIDO");
                    continue;
                }

                Object event = objectMapper.readValue(
                        evento.getPayload(),
                        eventClass
                );

                publisher.publicar(event);

                marcarComoEnviado(evento);

                log.info("Evento {} publicado correctamente", evento.getId());

            } catch (Exception e) {

                incrementarIntentos(evento);

                log.error("Error publicando evento {}", evento.getId(), e);
            }
        }
    }

    private void marcarComoEnviado(OutboxEventEntity evento) {

        evento.setEnviado(true);
        evento.setFechaEnvio(LocalDateTime.now());

        repository.save(evento);
    }

    /**
     * Estado terminal visible: fuera de los pendientes y con un log de error que no incluye el payload (puede llevar
     * destinatarios y datos de un correo).
     */
    private void marcarComoFallido(OutboxEventEntity evento, String motivo) {

        retirarCodigo(evento);
        log.error("Evento {} del outbox pasa a FALLIDO: motivo={} tipoEvento={} intentos={} (no se publicará; revisar)",
                evento.getId(), motivo, evento.getTipoEvento(), evento.getIntentos());

        evento.setFallido(true);
        evento.setMotivoFallo(motivo);
        evento.setFechaFallo(LocalDateTime.now());

        repository.save(evento);
    }

    /**
     * Borra los eventos propios ya enviados con más de {@code outbox.limpieza.retencion-dias} días (30 por defecto). Una
     * vez al día y bajo ShedLock (una sola instancia). Nunca toca pendientes, fallidos ni eventos de otros servicios.
     */
    @Scheduled(cron = "${outbox.limpieza.cron:0 30 3 * * *}")
    @SchedulerLock(name = "ms-negociador-orq_outboxScheduler_limpiarEnviados",
            lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void limpiarEnviados() {
        long borrados = repository.deleteByMsPropietarioAndEnviadoTrueAndFechaEnvioBefore(MS_PROPIETARIO,
                LocalDateTime.now().minusDays(retencionDias));
        log.info("Limpieza del outbox: {} eventos enviados con más de {} días borrados", borrados, retencionDias);
    }

    /**
     * Fase 28d (2.4): limpieza de {@code otp.validate}. Borra los enviados con más de
     * {@code outbox.limpieza.otp-validacion.retencion}; a los fallidos de ese tipo les retira el código (sin borrarlos)
     * y deja la alerta {@code [OUTBOX-FALLIDO]} mientras haya fallidos del servicio sin revisar. Bajo ShedLock.
     */
    @Scheduled(cron = "${outbox.limpieza.otp-validacion.cron:0 */10 * * * *}")
    @SchedulerLock(name = "ms-negociador-orq_outboxScheduler_limpiarValidacionesOtp",
            lockAtLeastFor = "PT30S", lockAtMostFor = "PT9M")
    public void limpiarValidacionesOtp() {
        long borrados = repository.deleteByMsPropietarioAndTipoEventoAndEnviadoTrueAndFechaEnvioBefore(MS_PROPIETARIO,
                topicValidateOtp, LocalDateTime.now().minus(retencionValidacionOtp));
        int retirados = 0;
        for (OutboxEventEntity fallido : repository.findByMsPropietarioAndTipoEventoAndFallidoTrue(MS_PROPIETARIO,
                topicValidateOtp)) {
            if (retirarCodigo(fallido)) {
                repository.save(fallido);
                retirados++;
            }
        }
        if (borrados > 0 || retirados > 0) {
            log.info("Limpieza de otp.validate: {} enviados borrados, código retirado de {} fallidos", borrados, retirados);
        }
        long fallidos = repository.countByMsPropietarioAndFallidoTrue(MS_PROPIETARIO);
        if (fallidos > 0) {
            log.error("[OUTBOX-FALLIDO] el outbox de {} tiene {} evento(s) fallido(s) sin publicar: se conservan para "
                    + "revisión manual (no se borran)", MS_PROPIETARIO, fallidos);
        }
    }

    /**
     * Retira el código del payload de un {@code otp.validate} (el resto del evento se conserva). Otros tipos no cambian.
     *
     * @return si cambió el payload
     */
    boolean retirarCodigo(OutboxEventEntity evento) {
        if (topicValidateOtp == null || !topicValidateOtp.equals(evento.getTipoEvento()) || evento.getPayload() == null) {
            return false;
        }
        try {
            JsonNode nodo = objectMapper.readTree(evento.getPayload());
            if (!(nodo instanceof ObjectNode objeto) || objeto.path(CAMPO_OTP).isNull() && objeto.has(CAMPO_OTP_RETIRADO)) {
                return false;
            }
            objeto.putNull(CAMPO_OTP);
            objeto.put(CAMPO_OTP_RETIRADO, true);
            evento.setPayload(objectMapper.writeValueAsString(objeto));
        } catch (Exception e) {
            // Sin el JSON no se puede retirar solo el código: el evento se conserva y queda la alerta.
            log.error("[OUTBOX-OTP-RETIRADO] evento {}: no se pudo leer el payload para retirar el código ({})",
                    evento.getId(), e.getClass().getSimpleName());
            return false;
        }
        log.warn("[OUTBOX-OTP-RETIRADO] evento {} ({}): se retiró el código del payload; el evento se conserva",
                evento.getId(), evento.getTipoEvento());
        return true;
    }

    private void incrementarIntentos(OutboxEventEntity evento) {

        evento.setIntentos(evento.getIntentos() + 1);

        repository.save(evento);
    }


}

