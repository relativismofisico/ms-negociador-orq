package com.negociadororq.infrastructure.persistence.adapter;

import java.math.BigDecimal;
import java.util.Optional;

import com.negociadororq.application.exception.ConflictoException;
import com.negociadororq.application.port.output.IndirectFunderOfferSupplierQuotaOutputPort;
import com.negociadororq.domain.model.IndirectFunderOfferSupplierQuota;
import com.negociadororq.infrastructure.persistence.entity.IndirectFunderOfferSupplierQuotaEntity;
import com.negociadororq.infrastructure.persistence.repository.IndirectFunderOfferSupplierQuotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link IndirectFunderOfferSupplierQuotaOutputPort}.
 */
@Component
@RequiredArgsConstructor
public class IndirectFunderOfferSupplierQuotaAdapter implements IndirectFunderOfferSupplierQuotaOutputPort {

    private final IndirectFunderOfferSupplierQuotaRepository repository;

    @Override
    public Optional<IndirectFunderOfferSupplierQuota> find(Long offerId, Long supplierId) {
        return repository.findByOfferIdAndSupplierId(offerId, supplierId).map(this::toDomain);
    }

    /**
     * Fase 28d (P-19): la fila nace con la plantilla y la versión de la oferta leídas en la misma sentencia (con bloqueo
     * compartido de la oferta), no con el valor que la negociación leyó antes: así queda alineada con el último cambio
     * confirmado y un cambio en curso la alcanza con su evento. {@code quota} (el valor que leyó quien llama) se usa
     * solo si la oferta ya no está en la tabla.
     */
    @Override
    public IndirectFunderOfferSupplierQuota crear(Long offerId, Long supplierId, BigDecimal rate, BigDecimal quota) {
        try {
            if (repository.crearDesdeOferta(offerId, supplierId, rate) == 0) {
                return toDomain(repository.save(IndirectFunderOfferSupplierQuotaEntity.builder()
                        .offerId(offerId).supplierId(supplierId).rate(rate)
                        .quota(quota).currentQuota(quota).originalQuota(quota).build()));
            }
            return find(offerId, supplierId).orElseThrow();
        } catch (DataIntegrityViolationException e) {
            // El UNIQUE (offer_id, supplier_id) de la tabla lo cubrió otra
            // negociación concurrente entre nuestro chequeo y este INSERT. Se
            // traduce a un error de negocio legible, sin filtrar tabla/constraint.
            throw new ConflictoException(
                    "El proveedor ya tiene un cupo abierto contra esta oferta de fondeo indirecta "
                            + "(creación concurrente). Vuelve a intentar.",
                    "CUPO_PROVEEDOR_CONCURRENCIA");
        }
    }

    // Fase 28d (P-19): cada escritura del cupo indirecto bloquea primero la oferta (compartido) y después la fila, en la
    // misma transacción (la del caso de uso, o una propia si no hay).
    @Override
    @Transactional
    public int descontarCupo(Long offerId, Long supplierId, BigDecimal valor) {
        repository.bloquearOferta(offerId);
        return repository.descontarCupo(offerId, supplierId, valor);
    }

    @Override
    @Transactional
    public int devolverCupo(Long offerId, Long supplierId, BigDecimal valor) {
        repository.bloquearOferta(offerId);
        return repository.devolverCupo(offerId, supplierId, valor);
    }

    @Override
    @Transactional
    public int recargar(Long offerId, Long supplierId) {
        repository.bloquearOferta(offerId);
        return repository.recargar(offerId, supplierId);
    }

    @Override
    @Transactional
    public Propagacion propagarCupoOferta(Long offerId, long version, BigDecimal nuevo) {
        repository.bloquearOferta(offerId);
        repository.completarPlantilla(offerId);
        int ajustadas = repository.propagarCupoOferta(offerId, version, nuevo);
        // Fase 28d (P-19): el exceso de las filas recién ajustadas queda persistido en cupo_oferta_exceso (V7)
        if (ajustadas > 0) {
            repository.registrarExceso(offerId, version, nuevo);
        }
        return new Propagacion(ajustadas, repository.contarPorEncimaDe(offerId, nuevo));
    }

    @Override
    public IndirectFunderOfferSupplierQuota save(IndirectFunderOfferSupplierQuota quota) {
        return toDomain(repository.save(toEntity(quota)));
    }

    private IndirectFunderOfferSupplierQuota toDomain(IndirectFunderOfferSupplierQuotaEntity entity) {
        return IndirectFunderOfferSupplierQuota.builder()
                .id(entity.getId())
                .offerId(entity.getOfferId())
                .supplierId(entity.getSupplierId())
                .rate(entity.getRate())
                .quota(entity.getQuota())
                .currentQuota(entity.getCurrentQuota())
                .originalQuota(entity.getOriginalQuota())
                .build();
    }

    private IndirectFunderOfferSupplierQuotaEntity toEntity(IndirectFunderOfferSupplierQuota quota) {
        return IndirectFunderOfferSupplierQuotaEntity.builder()
                .id(quota.getId())
                .offerId(quota.getOfferId())
                .supplierId(quota.getSupplierId())
                .rate(quota.getRate())
                .quota(quota.getQuota())
                .currentQuota(quota.getCurrentQuota())
                .originalQuota(quota.getOriginalQuota())
                .build();
    }
}
