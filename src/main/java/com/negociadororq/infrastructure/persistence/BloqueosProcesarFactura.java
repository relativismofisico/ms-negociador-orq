package com.negociadororq.infrastructure.persistence;

import java.util.List;

import com.negociadororq.application.dto.NegociacionRequestDto;
import com.negociadororq.application.exception.ConflictoException;
import com.negociadororq.application.service.ContextoCupoIndirecto;
import com.negociadororq.application.service.ProcesarFacturaNegociacionService;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;

/**
 * Fase 28d (P-19, condición de John): un deadlock o un lock wait timeout de MySQL al procesar una factura (cupo de la
 * empresa, del operador, del fondeador o indirecto) no termina en un 500. Envuelve al servicio por FUERA de su
 * transacción (REQUIRES_NEW por factura, que MySQL ya deshizo entera) para atrapar también el fallo en el commit, y lo
 * traduce a {@code CUPO_BLOQUEO_REINTENTAR}: la negociación aborta, devuelve exacto lo de las facturas anteriores y
 * responde 409 {@code CUPO_CONCURRENCIA_REINTENTAR}, igual que con el bloqueo optimista.
 */
@Slf4j
@RequiredArgsConstructor
public class BloqueosProcesarFactura implements ProcesarFacturaNegociacionService {

    /** Código de la traducción (está en los códigos de concurrencia de la negociación). */
    public static final String CUPO_BLOQUEO_REINTENTAR = "CUPO_BLOQUEO_REINTENTAR";

    private final ProcesarFacturaNegociacionService delegado;

    @Override
    public FacturaNegociada procesarFactura(NegociacionRequestDto request, FacturaNegociada facturaRequest,
            String negociacionId) {
        try {
            return delegado.procesarFactura(request, facturaRequest, negociacionId);
        } catch (PessimisticLockingFailureException e) {
            throw traducir(facturaRequest, e);
        }
    }

    @Override
    public FacturaNegociada procesarFactura(NegociacionRequestDto request, FacturaNegociada facturaRequest,
            String negociacionId, ContextoCupoIndirecto contexto) {
        try {
            return delegado.procesarFactura(request, facturaRequest, negociacionId, contexto);
        } catch (PessimisticLockingFailureException e) {
            throw traducir(facturaRequest, e);
        }
    }

    @Override
    public ContextoCupoIndirecto prepararCupoIndirecto(NegociacionRequestDto request, List<Factura> facturasBd) {
        return delegado.prepararCupoIndirecto(request, facturasBd);
    }

    @Override
    public void marcarComoNegociada(NegociacionRequestDto request, FacturaNegociada procesada) {
        delegado.marcarComoNegociada(request, procesada);
    }

    private static ConflictoException traducir(FacturaNegociada factura, PessimisticLockingFailureException e) {
        String numero = "-";
        if (factura != null) {
            numero = factura.getNumeroFactura();
        }
        log.warn("[CUPO-BLOQUEO-REINTENTAR] factura {}: {} al descontar cupos; la negociación se aborta y se pide reintentar",
                numero, e.getClass().getSimpleName());
        return new ConflictoException("Otra operación tenía bloqueado el cupo; vuelve a intentar.", CUPO_BLOQUEO_REINTENTAR);
    }
}
