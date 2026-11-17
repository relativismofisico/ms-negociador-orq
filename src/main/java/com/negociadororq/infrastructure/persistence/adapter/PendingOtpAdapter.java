package com.negociadororq.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.negociadororq.application.port.output.PendingOtpOutputPort;
import com.negociadororq.application.port.output.ResultadoSolicitudOtp;
import com.negociadororq.domain.model.EstadoFirmaOtp;
import com.negociadororq.domain.model.PendingOtp;
import com.negociadororq.infrastructure.persistence.entity.PendingOtpDocument;
import com.negociadororq.infrastructure.persistence.repository.PendingOtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link PendingOtpOutputPort}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingOtpAdapter implements PendingOtpOutputPort {

    private static final String CAMPO_ESTADO = "estado";
    private static final int INTENTOS_ALTA = 5;

    private final PendingOtpRepository repository;
    private final MongoTemplate mongoTemplate;

    @Override
    public PendingOtp save(PendingOtp pendingOtp) {
        return toDomain(repository.save(toDocument(pendingOtp)));
    }

    @Override
    public Optional<PendingOtp> findByClave(String idNegociacion, String rutEmpresa, String rutOperador) {
        if (rutEmpresa != null && rutOperador != null) {
            PendingOtpDocument exacto = mongoTemplate.findOne(clave(idNegociacion, rutEmpresa, rutOperador),
                    PendingOtpDocument.class);
            if (exacto != null) {
                return Optional.of(toDomain(exacto));
            }
        }
        // Respaldo para pendientes anteriores a la 2.9 (sin rutOperador) o eventos que no traen al actor: solo si hay
        // exactamente una solicitud activa que encaje; con varias no se adivina.
        Criteria criterio = Criteria.where("idNegociacion").is(idNegociacion);
        if (rutEmpresa != null) {
            criterio = criterio.and("rutEmpresa").is(rutEmpresa);
        }
        if (rutOperador != null) {
            criterio = criterio.and("rutOperador").exists(false);
        }
        List<PendingOtpDocument> candidatos = mongoTemplate.find(Query.query(criterio), PendingOtpDocument.class)
                .stream().filter(doc -> EstadoFirmaOtp.activa(doc.getEstado())).toList();
        if (candidatos.size() == 1) {
            return Optional.of(toDomain(candidatos.get(0)));
        }
        if (candidatos.size() > 1) {
            log.warn("[OTP-AMBIGUO] negociación {}: {} solicitudes activas sin operador identificable; no se elige ninguna.",
                    idNegociacion, candidatos.size());
        }
        return Optional.empty();
    }

    @Override
    public ResultadoSolicitudOtp registrarSolicitud(PendingOtp solicitud) {
        Update refresco = new Update()
                .set("rolActor", solicitud.getRolActor())
                .set("request", solicitud.getRequest())
                .set("validadoEnOrigen", solicitud.getValidadoEnOrigen())
                .set("actorTipo", solicitud.getActorTipo())
                .set("actorId", solicitud.getActorId())
                .set("actorRol", solicitud.getActorRol())
                .set("actorEmpresaId", solicitud.getActorEmpresaId())
                .set("requestId", solicitud.getRequestId())
                .set(CAMPO_ESTADO, EstadoFirmaOtp.PENDIENTE_OTP.name())
                .set("fechaActualizacion", solicitud.getFechaActualizacion());
        Update reemplazo = Update.fromDocument(refresco.getUpdateObject())
                .unset("otp").unset("otpId").unset("ip").unset("fechaValidacion").unset("efectosEmitidos")
                .unset("intentosFirma").unset("ultimoError").unset("aprobado");
        FindAndModifyOptions devolverNuevo = FindAndModifyOptions.options().returnNew(true);
        for (int intento = 0; intento < INTENTOS_ALTA; intento++) {
            Query esperando = clave(solicitud.getIdNegociacion(), solicitud.getRutEmpresa(), solicitud.getRutOperador())
                    .addCriteria(Criteria.where(CAMPO_ESTADO).in(EstadoFirmaOtp.PENDIENTE_OTP.name(), null));
            PendingOtpDocument refrescado = mongoTemplate.findAndModify(esperando, refresco, devolverNuevo,
                    PendingOtpDocument.class);
            if (refrescado != null) {
                return new ResultadoSolicitudOtp(ResultadoSolicitudOtp.Tipo.REFRESCADA, toDomain(refrescado), null);
            }
            Query terminada = clave(solicitud.getIdNegociacion(), solicitud.getRutEmpresa(), solicitud.getRutOperador())
                    .addCriteria(Criteria.where(CAMPO_ESTADO).in(EstadoFirmaOtp.FALLO_FIRMA.name(),
                            EstadoFirmaOtp.ROL_NO_PERMITIDO.name()));
            PendingOtpDocument anterior = mongoTemplate.findAndModify(terminada, reemplazo,
                    FindAndModifyOptions.options().returnNew(false), PendingOtpDocument.class);
            if (anterior != null) {
                PendingOtpDocument actual = mongoTemplate.findById(anterior.getId(), PendingOtpDocument.class);
                if (actual == null) {
                    continue;
                }
                return new ResultadoSolicitudOtp(ResultadoSolicitudOtp.Tipo.REEMPLAZADA, toDomain(actual),
                        anterior.getEstado());
            }
            PendingOtpDocument existente = mongoTemplate.findOne(
                    clave(solicitud.getIdNegociacion(), solicitud.getRutEmpresa(), solicitud.getRutOperador()),
                    PendingOtpDocument.class);
            if (existente != null) {
                if (EstadoFirmaOtp.FIRMADO.name().equals(existente.getEstado())) {
                    return new ResultadoSolicitudOtp(ResultadoSolicitudOtp.Tipo.YA_FIRMADA, toDomain(existente), null);
                }
                if (EstadoFirmaOtp.OTP_VALIDADO.name().equals(existente.getEstado())) {
                    return new ResultadoSolicitudOtp(ResultadoSolicitudOtp.Tipo.FIRMA_EN_CURSO, toDomain(existente), null);
                }
                // Apareció (o cambió) entre los pasos anteriores por otra solicitud simultánea: se reintenta.
                continue;
            }
            try {
                PendingOtpDocument doc = toDocument(solicitud);
                doc.setId(null);
                return new ResultadoSolicitudOtp(ResultadoSolicitudOtp.Tipo.NUEVA, toDomain(mongoTemplate.insert(doc)), null);
            } catch (DuplicateKeyException e) {
                // Otra solicitud simultánea del mismo actor ganó el alta (índice único): se reintenta como actualización.
                log.info("[OTP-ALTA-CONCURRENTE] negociación {}: otra solicitud del mismo actor ganó el alta; se reintenta.",
                        solicitud.getIdNegociacion());
            }
        }
        throw new IllegalStateException("No se pudo registrar la solicitud de OTP de la negociación "
                + solicitud.getIdNegociacion());
    }

    @Override
    public boolean existeFirmaDeOtroOperador(String idNegociacion, String rutEmpresa, String rutOperador) {
        Query query = Query.query(Criteria.where("idNegociacion").is(idNegociacion).and("rutEmpresa").is(rutEmpresa)
                .and("rutOperador").ne(rutOperador)
                .and(CAMPO_ESTADO).in(EstadoFirmaOtp.OTP_VALIDADO.name(), EstadoFirmaOtp.FIRMADO.name()));
        return mongoTemplate.exists(query, PendingOtpDocument.class);
    }

    private static Query clave(String idNegociacion, String rutEmpresa, String rutOperador) {
        return Query.query(Criteria.where("idNegociacion").is(idNegociacion).and("rutEmpresa").is(rutEmpresa)
                .and("rutOperador").is(rutOperador));
    }

    @Override
    public void deleteById(String id) {
        repository.deleteById(id);
    }

    @Override
    public List<PendingOtp> findByEstadoAndFechaActualizacionBefore(String estado, LocalDateTime limite) {
        return repository.findByEstadoAndFechaActualizacionBefore(estado, limite).stream()
                .map(this::toDomain)
                .toList();
    }

    private PendingOtp toDomain(PendingOtpDocument doc) {
        return PendingOtp.builder()
                .id(doc.getId())
                .idNegociacion(doc.getIdNegociacion())
                .rutEmpresa(doc.getRutEmpresa())
                .rutOperador(doc.getRutOperador())
                .rolActor(doc.getRolActor())
                .ip(doc.getIp())
                .otp(doc.getOtp())
                .aprobado(doc.getAprobado())
                .request(doc.getRequest())
                .validadoEnOrigen(doc.getValidadoEnOrigen())
                .actorTipo(doc.getActorTipo())
                .actorId(doc.getActorId())
                .actorRol(doc.getActorRol())
                .actorEmpresaId(doc.getActorEmpresaId())
                .requestId(doc.getRequestId())
                .estado(doc.getEstado())
                .otpId(doc.getOtpId())
                .fechaValidacion(doc.getFechaValidacion())
                .intentosFirma(doc.getIntentosFirma())
                .ultimoError(doc.getUltimoError())
                .fechaActualizacion(doc.getFechaActualizacion())
                .efectosEmitidos(doc.getEfectosEmitidos())
                .build();
    }

    private PendingOtpDocument toDocument(PendingOtp p) {
        return PendingOtpDocument.builder()
                .id(p.getId())
                .idNegociacion(p.getIdNegociacion())
                .rutEmpresa(p.getRutEmpresa())
                .rutOperador(p.getRutOperador())
                .rolActor(p.getRolActor())
                .ip(p.getIp())
                .otp(p.getOtp())
                .aprobado(p.getAprobado())
                .request(p.getRequest())
                .validadoEnOrigen(p.getValidadoEnOrigen())
                .actorTipo(p.getActorTipo())
                .actorId(p.getActorId())
                .actorRol(p.getActorRol())
                .actorEmpresaId(p.getActorEmpresaId())
                .requestId(p.getRequestId())
                .estado(p.getEstado())
                .otpId(p.getOtpId())
                .fechaValidacion(p.getFechaValidacion())
                .intentosFirma(p.getIntentosFirma())
                .ultimoError(p.getUltimoError())
                .fechaActualizacion(p.getFechaActualizacion())
                .efectosEmitidos(p.getEfectosEmitidos())
                .build();
    }
}
