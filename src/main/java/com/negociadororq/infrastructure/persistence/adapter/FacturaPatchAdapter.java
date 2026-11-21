package com.negociadororq.infrastructure.persistence.adapter;

import java.util.List;

import com.negociadororq.application.exception.ConflictoException;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.service.ResolutorFactura;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.enums.EstadoNegociacion;
import com.negociadororq.domain.model.ActorFactura;
import com.negociadororq.domain.model.ReferenciaFactura;
import com.negociadororq.infrastructure.persistence.repository.FacturaPatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Implementación de {@link FacturaPatchOutputPort}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FacturaPatchAdapter implements FacturaPatchOutputPort {

    private final FacturaPatchRepository repository;

    @Override
    public void actualizarEstadoPorId(String facturaId, EstadoFirma estadoFirma, EstadoNegociacion estadoNegociacion) {
        repository.actualizarEstadoPorId(facturaId, estadoFirma, estadoNegociacion);
    }

    @Override
    public void marcarComoNegociada(String facturaId, EstadoFirma estadoFirma, List<ActorFactura> actores) {
        repository.marcarComoNegociada(facturaId, estadoFirma, actores);
    }

    @Override
    public void actualizarEstadoFirmaPorCufe(ReferenciaFactura ref, EstadoFirma estadoFirma) {
        exigirCoincidencia(repository.actualizarEstadoFirmaPorCufe(ref, estadoFirma), ref);
    }

    @Override
    public void actualizarEstadosPorCufe(ReferenciaFactura ref, EstadoFirma estadoFirma,
                                         EstadoNegociacion estadoNegociacion) {
        exigirCoincidencia(repository.actualizarEstadosPorCufe(ref, estadoFirma, estadoNegociacion), ref);
    }

    @Override
    public boolean actualizarEstadosSiNoVencida(ReferenciaFactura ref, EstadoFirma estadoFirma,
                                                EstadoNegociacion estadoNegociacion) {
        FacturaPatchRepository.Resultado r = repository.actualizarEstadosSiNoVencida(ref, estadoFirma, estadoNegociacion);
        exigirCoincidencia(r != FacturaPatchRepository.Resultado.NO_COINCIDE, ref);
        return r == FacturaPatchRepository.Resultado.ACTUALIZADA;
    }

    @Override
    public boolean marcarFirmaFinalConSolicitud(ReferenciaFactura ref, EstadoFirma firmaFinal, String idEvento) {
        return repository.marcarFirmaFinalConSolicitud(ref, firmaFinal, idEvento);
    }

    @Override
    public boolean marcarVencidaSiCoincide(String facturaId, String estadoLeido, String firmaLeida,
                                           boolean reversionPendiente) {
        return repository.marcarVencidaSiCoincide(facturaId, estadoLeido, firmaLeida, reversionPendiente);
    }

    @Override
    public void quitarReversionPendiente(String facturaId) {
        repository.quitarReversionPendiente(facturaId);
    }

    @Override
    public void quitarSeguimientoLiquidacionPendiente(ReferenciaFactura ref) {
        repository.quitarSeguimientoLiquidacionPendiente(ref);
    }

    @Override
    public void actualizarNegociacionIdPorCufe(ReferenciaFactura ref, String negociacionId) {
        exigirCoincidencia(repository.actualizarNegociacionIdPorCufe(ref, negociacionId), ref);
    }

    /** Fase 28d (C): exactamente una factura con ese CUFE y esas empresas (el CUFE es único). */
    private static void exigirCoincidencia(boolean actualizada, ReferenciaFactura ref) {
        if (!actualizada) {
            log.warn("[FACTURA-NO-COINCIDE] ninguna factura con el CUFE {} y las empresas de la negociación", ref.cufe());
            throw new ConflictoException("La factura de la negociación no coincide con la base (CUFE y empresas).",
                    ResolutorFactura.FACTURA_NO_COINCIDE);
        }
    }
}
