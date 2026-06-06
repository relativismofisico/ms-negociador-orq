package com.negociadororq.infrastructure.persistence.adapter;

import com.negociadororq.application.port.output.PendingOtpOutputPort;
import com.negociadororq.domain.model.PendingOtp;
import com.negociadororq.infrastructure.persistence.entity.PendingOtpDocument;
import com.negociadororq.infrastructure.persistence.repository.PendingOtpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

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
        return repository.findByIdNegociacion(idNegociacion).map(this::toDomain);
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
                .build();
    }
}
