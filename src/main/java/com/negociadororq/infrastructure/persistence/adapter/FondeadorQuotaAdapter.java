package com.negociadororq.infrastructure.persistence.adapter;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.application.port.output.OptimisticLockConflictException;
import com.negociadororq.domain.model.FondeadorQuota;
import com.negociadororq.infrastructure.persistence.entity.FondeadorQuotaEntity;
import com.negociadororq.infrastructure.persistence.repository.FondeadorQuotaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link FondeadorQuotaOutputPort}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FondeadorQuotaAdapter implements FondeadorQuotaOutputPort {

    private static final String ESTADO_APROBADO = "APROBADO";

    private final FondeadorQuotaRepository repository;

    // La tabla funder admite varias filas por par (una RECHAZADA o PENDIENTE junto a la APROBADA, o
    // una asignación nueva tras desactivar la anterior). Mismo criterio que ms-liquidador-neg
    // (FunderRepositoryAdapter) y ms-registroempresa-neg: la asignación efectiva es la APROBADO; si
    // hay más de una, la más reciente (id mayor). Sin ninguna APROBADO se devuelve la fila más
    // reciente, para que el llamador siga distinguiendo "existe pero no está aprobada"
    // (validarFondeadorAprobado), igual que cuando el par tenía una sola fila.

    @Override
    public Optional<FondeadorQuota> findByFunderIdAndPayerId(Long funderId, Long payerId) {
        return elegir(repository.findByFunderIdAndPayerIdOrderByIdDesc(funderId, payerId), false,
                "payerId", funderId, payerId).map(this::toDomain);
    }

    @Override
    public Optional<FondeadorQuota> findByFunderIdAndSupplierId(Long funderId, Long supplierId) {
        return elegir(repository.findByFunderIdAndSupplierIdOrderByIdDesc(funderId, supplierId), false,
                "supplierId", funderId, supplierId).map(this::toDomain);
    }

    @Override
    public Optional<FondeadorQuota> findByFunderIdAndPayerIdActivo(Long funderId, Long payerId) {
        return elegir(repository.findByFunderIdAndPayerIdOrderByIdDesc(funderId, payerId), true,
                "payerId", funderId, payerId).map(this::toDomain);
    }

    @Override
    public Optional<FondeadorQuota> findByFunderIdAndSupplierIdActivo(Long funderId, Long supplierId) {
        return elegir(repository.findByFunderIdAndSupplierIdOrderByIdDesc(funderId, supplierId), true,
                "supplierId", funderId, supplierId).map(this::toDomain);
    }

    /**
     * Elige la asignación efectiva de un par entre sus filas (ordenadas de la más reciente a la más
     * antigua).
     *
     * @param filas        todas las filas del par, por id descendente
     * @param soloActivas  {@code true} al validar una negociación NUEVA: una asignación desactivada no
     *                     cuenta. {@code false} en reversiones y descuentos de una negociación ya en
     *                     curso, que deben encontrar la fila aunque se haya desactivado después (entre
     *                     las APROBADO se prefiere la activa)
     * @param campoPar     nombre del segundo id del par, solo para el log
     * @param funderId     id de la empresa fondeadora, solo para el log
     * @param otroId       id de la empresa pagadora o proveedora, solo para el log
     * @return la APROBADO elegida; si no hay ninguna, la fila más reciente; vacío si no hay filas
     */
    private Optional<FondeadorQuotaEntity> elegir(List<FondeadorQuotaEntity> filas, boolean soloActivas,
            String campoPar, Long funderId, Long otroId) {
        List<FondeadorQuotaEntity> candidatas = filas;
        if (soloActivas) {
            candidatas = filas.stream().filter(f -> Boolean.TRUE.equals(f.getActivo())).toList();
        }
        List<FondeadorQuotaEntity> aprobadas = candidatas.stream()
                .filter(f -> ESTADO_APROBADO.equalsIgnoreCase(f.getEstado()))
                .sorted(Comparator.comparing((FondeadorQuotaEntity f) -> !Boolean.TRUE.equals(f.getActivo()))
                        .thenComparing(FondeadorQuotaEntity::getId, Comparator.reverseOrder()))
                .toList();
        if (aprobadas.isEmpty()) {
            return candidatas.stream().findFirst();
        }
        if (aprobadas.size() > 1) {
            log.warn("[FondeadorQuotaAdapter] {} asignaciones APROBADO para funderId={} {}={}; se usa id={}",
                    aprobadas.size(), funderId, campoPar, otroId, aprobadas.get(0).getId());
        }
        return Optional.of(aprobadas.get(0));
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
