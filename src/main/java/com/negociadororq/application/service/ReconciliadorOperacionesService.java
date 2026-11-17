package com.negociadororq.application.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.OperacionPendienteOutputPort;
import com.negociadororq.domain.model.OperacionPendiente;
import com.negociadororq.domain.model.TipoOperacionPendiente;

/**
 * Fase 28d, opción A: reconciliador único de {@code operacion_pendiente}. En cada vuelta toma, con concesión, las
 * operaciones que siguen en un estado intermedio pasada la gracia, y las reanuda con el {@link ReanudadorOperacion} de
 * su tipo. Una que falla queda con su último error y se vuelve a tomar cuando vence la concesión. Corre en una sola
 * instancia (ShedLock en el scheduler).
 */
public class ReconciliadorOperacionesService {

    private static final Logger LOG = Logger.getLogger(ReconciliadorOperacionesService.class.getName());

    private final OperacionPendienteOutputPort operaciones;
    private final ClockPort clock;
    private final Map<TipoOperacionPendiente, ReanudadorOperacion> reanudadores;
    private final Duration gracia;
    private final Duration concesion;
    private final int lote;

    /**
     * @param operaciones  tabla de operaciones pendientes
     * @param clock        reloj
     * @param reanudadores un reanudador por tipo
     * @param gracia       tiempo sin cambios antes de considerar que el flujo en curso murió
     * @param concesion    tiempo que el reconciliador retiene cada operación tomada
     * @param lote         máximo de operaciones por vuelta
     */
    public ReconciliadorOperacionesService(final OperacionPendienteOutputPort operaciones, final ClockPort clock,
            final List<ReanudadorOperacion> reanudadores, final Duration gracia, final Duration concesion,
            final int lote) {
        this.operaciones = operaciones;
        this.clock = clock;
        this.reanudadores = reanudadores.stream()
                .collect(Collectors.toUnmodifiableMap(ReanudadorOperacion::tipo, Function.identity()));
        this.gracia = gracia;
        this.concesion = concesion;
        this.lote = lote;
    }

    /**
     * @return cuántas operaciones reanudó con éxito en esta vuelta
     */
    public int reconciliar() {
        final List<String> estados = reanudadores.values().stream()
                .flatMap(r -> r.estadosReanudables().stream()).distinct().toList();
        if (estados.isEmpty()) {
            return 0;
        }
        final var ahora = clock.now();
        int reanudadas = 0;
        for (final OperacionPendiente op : operaciones.tomarParaReanudar(estados, ahora.minus(gracia), ahora,
                ahora.plus(concesion), lote)) {
            final ReanudadorOperacion reanudador = reanudadores.get(op.tipo());
            if (reanudador == null || !reanudador.estadosReanudables().contains(op.estado())) {
                continue;
            }
            try {
                reanudador.reanudar(op);
                reanudadas++;
                LOG.info(() -> "[RECONCILIADOR-OPERACIONES] " + op.tipo() + " " + op.claveNatural() + " reanudada desde "
                        + op.estado() + " (intento " + op.intentos() + ")");
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "[RECONCILIADOR-OPERACIONES] " + op.tipo() + " " + op.claveNatural()
                        + " no se pudo reanudar desde " + op.estado() + ": " + e.getClass().getSimpleName());
                operaciones.registrarError(op.id(), e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        return reanudadas;
    }
}
