package com.negociadororq.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.negociadororq.application.port.output.PendingOtpOutputPort;
import com.negociadororq.domain.model.EstadoFirmaOtp;
import com.negociadororq.domain.model.PendingOtp;
import com.negociadororq.infrastructure.persistence.entity.PendingOtpDocument;
import com.negociadororq.infrastructure.persistence.repository.PendingOtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link PendingOtpOutputPort}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingOtpAdapter implements PendingOtpOutputPort {

    private final PendingOtpRepository repository;

    @Override
    public PendingOtp save(PendingOtp pendingOtp) {
        return toDomain(repository.save(toDocument(pendingOtp)));
    }

    @Override
    public Optional<PendingOtp> findByIdNegociacion(String idNegociacion) {
        // Fase 28c: solo las solicitudes activas; FIRMADO y FALLO_FIRMA quedan como historial y no se descartan.
        List<PendingOtpDocument> documentos = repository.findByIdNegociacion(idNegociacion).stream()
                .filter(doc -> EstadoFirmaOtp.activa(doc.getEstado()))
                .toList();

        if (documentos.isEmpty()) {
            return Optional.empty();
        }

        // El id de Mongo (ObjectId) es cronológicamente ordenable como texto:
        // ante solicitudes duplicadas para la misma negociación (no debería
        // ocurrir, pero ya ocurrió), se toma la más reciente y se descartan
        // las demás, en vez de fallar de forma permanente.
        PendingOtpDocument masReciente = documentos.stream()
                .max(Comparator.comparing(PendingOtpDocument::getId))
                .orElseThrow();

        if (documentos.size() > 1) {
            log.warn("Se encontraron {} solicitudes de OTP pendientes para la negociación {}; "
                            + "se usa la más reciente ({}) y se descartan las demás.",
                    documentos.size(), idNegociacion, masReciente.getId());

            documentos.stream()
                    .filter(doc -> !doc.getId().equals(masReciente.getId()))
                    .forEach(doc -> repository.deleteById(doc.getId()));
        }

        return Optional.of(toDomain(masReciente));
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

    @Override
    public void deleteByIdNegociacion(String idNegociacion) {
        repository.deleteByIdNegociacion(idNegociacion);
    }

    private PendingOtp toDomain(PendingOtpDocument doc) {
        return PendingOtp.builder()
                .id(doc.getId())
                .idNegociacion(doc.getIdNegociacion())
                .rutEmpresa(doc.getRutEmpresa())
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
