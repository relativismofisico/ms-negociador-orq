package com.negociadororq.infrastructure.persistence.adapter;

import java.util.Optional;

import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.application.port.output.OptimisticLockConflictException;
import com.negociadororq.domain.model.FondeadorQuota;
import com.negociadororq.infrastructure.persistence.entity.FondeadorQuotaEntity;
import com.negociadororq.infrastructure.persistence.repository.FondeadorQuotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link FondeadorQuotaOutputPort}.
 */
@Component
@RequiredArgsConstructor
public class FondeadorQuotaAdapter implements FondeadorQuotaOutputPort {

    private final FondeadorQuotaRepository repository;

    @Override
    public Optional<FondeadorQuota> findByFunderIdAndPayerId(Long funderId, Long payerId) {
        return repository.findByFunderIdAndPayerId(funderId, payerId).map(this::toDomain);
    }

    @Override
    public Optional<FondeadorQuota> findByFunderIdAndSupplierId(Long funderId, Long supplierId) {
        return repository.findByFunderIdAndSupplierId(funderId, supplierId).map(this::toDomain);
    }

    @Override
    public Optional<FondeadorQuota> findByFunderIdAndPayerIdActivo(Long funderId, Long payerId) {
        return repository.findByFunderIdAndPayerIdAndActivoTrue(funderId, payerId).map(this::toDomain);
    }

    @Override
    public Optional<FondeadorQuota> findByFunderIdAndSupplierIdActivo(Long funderId, Long supplierId) {
        return repository.findByFunderIdAndSupplierIdAndActivoTrue(funderId, supplierId).map(this::toDomain);
    }

    @Override
    public FondeadorQuota save(FondeadorQuota quota) {
        return toDomain(repository.save(toEntity(quota)));
    }

    @Override
    public FondeadorQuota saveAndFlush(FondeadorQuota quota) {
        try {
            return toDomain(repository.saveAndFlush(toEntity(quota)));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new OptimisticLockConflictException(
                    "Conflicto de optimistic locking al guardar FondeadorQuota id=" + quota.getId(), e);
        }
    }

    private FondeadorQuota toDomain(FondeadorQuotaEntity entity) {
        return FondeadorQuota.builder()
                .id(entity.getId())
                .funderId(entity.getFunderId())
                .supplierId(entity.getSupplierId())
                .payerId(entity.getPayerId())
                .payerQuota(entity.getPayerQuota())
                .supplierQuota(entity.getSupplierQuota())
                .payerCurrentQuota(entity.getPayerCurrentQuota())
                .supplierCurrentQuota(entity.getSupplierCurrentQuota())
                .payerRate(entity.getPayerRate())
                .supplierRate(entity.getSupplierRate())
                .estado(entity.getEstado())
                .origen(entity.getOrigen())
                .aprobadoPorFondeador(entity.getAprobadoPorFondeador())
                .activo(entity.getActivo())
                .endosoGrupal(entity.getEndosoGrupal())
                .rentabilidadMinimaActiva(entity.getRentabilidadMinimaActiva())
                .rentabilidadMinima(entity.getRentabilidadMinima())
                .tipoDescuento(entity.getTipoDescuento())
                .tipoTasa(entity.getTipoTasa())
                .tarifaPlena(entity.getTarifaPlena())
                .vigenciaDescuento(entity.getVigenciaDescuento())
                .diasVigencia(entity.getDiasVigencia())
                .porcentajeReserva(entity.getPorcentajeReserva())
                .version(entity.getVersion())
                .build();
    }

    private FondeadorQuotaEntity toEntity(FondeadorQuota quota) {
        return FondeadorQuotaEntity.builder()
                .id(quota.getId())
                .funderId(quota.getFunderId())
                .supplierId(quota.getSupplierId())
                .payerId(quota.getPayerId())
                .payerQuota(quota.getPayerQuota())
                .supplierQuota(quota.getSupplierQuota())
                .payerCurrentQuota(quota.getPayerCurrentQuota())
                .supplierCurrentQuota(quota.getSupplierCurrentQuota())
                .payerRate(quota.getPayerRate())
                .supplierRate(quota.getSupplierRate())
                .estado(quota.getEstado())
                .origen(quota.getOrigen())
                .aprobadoPorFondeador(quota.getAprobadoPorFondeador())
                .activo(quota.getActivo())
                .endosoGrupal(quota.getEndosoGrupal())
                .rentabilidadMinimaActiva(quota.getRentabilidadMinimaActiva())
                .rentabilidadMinima(quota.getRentabilidadMinima())
                .tipoDescuento(quota.getTipoDescuento())
                .tipoTasa(quota.getTipoTasa())
                .tarifaPlena(quota.getTarifaPlena())
                .vigenciaDescuento(quota.getVigenciaDescuento())
                .diasVigencia(quota.getDiasVigencia())
                .porcentajeReserva(quota.getPorcentajeReserva())
                .version(quota.getVersion())
                .build();
    }
}
