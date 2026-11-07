package com.negociadororq.application.service;

import java.math.BigDecimal;
import java.util.Locale;

import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.EndosoPatchOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.port.output.ReversionCuposReclamoOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.enums.EstadoNegociacion;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Implementación de {@link ReversionCuposService}.
 *
 * <p>Idempotente frente a un {@code liquidacion-fallida} duplicado, concurrente o tardío:</p>
 * <ol>
 *   <li>Guarda de estado: una factura LIQUIDADA o con la firma ya RECHAZADA no se toca (ni cupos, ni firma, ni
 *       notificación).</li>
 *   <li>Reclamo y cupos en UNA transacción de MySQL: el reclamo ({@link ReversionCuposReclamoOutputPort}, clave
 *       única {@code (numero_factura, negociacion_id)}) y las cuatro reversiones de cupo se confirman o se revierten
 *       juntos. El reclamo vive en MySQL y no en la firma de la factura (Mongo) porque los cupos están en MySQL: un
 *       reclamo en Mongo no se podría confirmar junto con ellos.</li>
 *   <li>Solo quien gana el reclamo marca la firma y notifica, después del commit. Si la firma falla después, el
 *       reintento ve el reclamo, no vuelve a revertir y solo completa la firma (la notificación queda a lo sumo una
 *       vez).</li>
 * </ol>
 */
@Log
@RequiredArgsConstructor
public class ReversionCuposServiceImpl
        implements ReversionCuposService {

    private final NegociacionOutputPort negociacionPort;
    private final FacturaOutputPort facturaPort;
    private final FacturaPatchOutputPort facturaPatchPort;
    private final EndosoPatchOutputPort endosoPatchPort;

    private final CupoReversionService cupoReversionService;
    private final UserOutputPort userPort;
    private final CompanyOutputPort companyPort;

    private final NotificacionService notificacionService;

    private final ReversionCuposReclamoOutputPort reclamoPort;
    private final TransactionOperations transaccion;

    @Override
    public void revertirPorFactura(
            String numeroFactura,
            String actorId,
            String motivo) {

        // ==========================================
        // FACTURA
        // ==========================================

        Factura factura =
                facturaPort
                        .findByNumeroFactura(numeroFactura)
                        .orElseThrow(() ->
                                new RecursoNoEncontradoException(
                                        "Factura no encontrada"));

        // ==========================================
        // GUARDA DE ESTADO
        // ==========================================

        if (EstadoNegociacion.LIQUIDADA.name().equals(factura.getEstadoNegociacion())
                || EstadoFirma.RECHAZADA.name().equals(factura.getEstadoFirma())) {
            log.info(String.format("Reversión ignorada factura=%s: estado negociación=%s, firma=%s",
                    numeroFactura, factura.getEstadoNegociacion(), factura.getEstadoFirma()));
            return;
        }

        // ==========================================
        // NEGOCIACIÓN
        // ==========================================

        Negociacion negociacion =
                negociacionPort
                        .findByNumeroFactura(numeroFactura)
                        .orElseThrow(() ->
                                new RecursoNoEncontradoException(
                                        "Negociación no encontrada"));

        // ==========================================
        // RECLAMO + CUPOS (una transacción)
        // ==========================================

        Boolean reclamada = transaccion.execute(estado ->
                reclamoPort.reclamar(numeroFactura, negociacion.getId())
                        && revertirCupos(numeroFactura, actorId, factura, negociacion));

        if (!Boolean.TRUE.equals(reclamada)) {
            // Otra entrega ya revirtió los cupos de esta negociación. Solo se completa la firma por si aquella
            // falló después de confirmar; no se notifica otra vez.
            log.info(String.format("Reversión ya aplicada factura=%s negociación=%s: no se revierte otra vez",
                    numeroFactura, negociacion.getId()));
            facturaPatchPort.actualizarEstadoFirmaPorNoFactura(numeroFactura, EstadoFirma.RECHAZADA);
            return;
        }

        // ==========================================
        // ACTUALIZAR ESTADOS, LOG Y NOTIFICACIÓN
        // ==========================================

        finalizarReversion(numeroFactura, motivo, negociacion);
    }

    /**
     * Las cuatro reversiones, dentro de la transacción del reclamo: si una falla (p. ej. un fondeador que no
     * existe), ninguna queda confirmada y el reclamo tampoco.
     */
    private boolean revertirCupos(String numeroFactura, String actorId, Factura factura, Negociacion negociacion) {

        // ==========================================
        // FACTURA NEGOCIADA
        // ==========================================

        FacturaNegociada facturaNegociada =
                negociacion.getFacturas()
                        .stream()
                        .filter(f ->
                                f.getNumeroFactura()
                                        .equals(numeroFactura))
                        .findFirst()
                        .orElseThrow();

        // ==========================================
        // TIPO OPERACIÓN
        // ==========================================

        String tipoOperacion =
                negociacion.getTipoOperacion()
                        .getTipo()
                        .toUpperCase(Locale.ROOT);

        // ==========================================
        // VALOR
        // ==========================================

        BigDecimal valor =
                facturaNegociada.getValorFactura();

        // ==========================================
        // REVERSAR CUPO OPERADOR
        // ==========================================

        Long userId =
                userPort
                        .findByUsername(actorId)
                        .map(User::getIdeUser)
                        .orElseThrow(() ->
                                new RecursoNoEncontradoException(
                                        "Usuario no encontrado"));

        cupoReversionService.revertirCupoUsuario(
                userId,
                valor);

        // ==========================================
        // REVERSAR CUPO EMPRESA
        // ==========================================

        Long companyId =
                companyPort
                        .findByNit(negociacion.getRutEmpresa())
                        .map(Company::getIdeCompany)
                        .orElseThrow(() ->
                                new RecursoNoEncontradoException(
                                        "Empresa no encontrada"));

        // En la transacción del reclamo, no en una propia (REQUIRES_NEW): ver la javadoc de la clase.
        cupoReversionService.revertirCupoEmpresaEnTransaccionActual(
                companyId,
                valor);

        // ==========================================
        // REVERSAR CUPOS FONDEADORES
        // ==========================================

        revertirCuposFondeadores(facturaNegociada, factura, tipoOperacion, valor);

        return true;
    }

    private void revertirCuposFondeadores(
            FacturaNegociada facturaNegociada, Factura factura, String tipoOperacion, BigDecimal valor) {

        if (facturaNegociada.getFondeadores() == null) {
            return;
        }

        for (FondeadorAsignacion fon : facturaNegociada.getFondeadores()) {
            Long funderId = companyPort.findByNit(fon.getRutFondeador())
                    .map(Company::getIdeCompany)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Fondeador no encontrado"));

            if ("FACTORING".equals(tipoOperacion)) {
                Long supplierId = companyPort.findByNit(factura.getRutProveedor())
                        .map(Company::getIdeCompany)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Proveedor no encontrado"));
                cupoReversionService.revertirCupoFondeadorFactoring(funderId, supplierId, valor);
            }

            if ("CONFIRMING".equals(tipoOperacion)) {
                Long payerId = companyPort.findByNit(factura.getRutPagador())
                        .map(Company::getIdeCompany)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Pagador no encontrado"));
                cupoReversionService.revertirCupoFondeadorConfirming(funderId, payerId, valor);
            }
        }
    }

    private void finalizarReversion(String numeroFactura, String motivo, Negociacion negociacion) {
        facturaPatchPort.actualizarEstadoFirmaPorNoFactura(numeroFactura, EstadoFirma.RECHAZADA);
        notificacionService.notificarLiquidacionFallida(negociacion, numeroFactura, motivo);
    }
}
