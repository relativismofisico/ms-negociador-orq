package com.negociadororq.application.service;

import com.negociadororq.application.controller.dto.NegociacionRequestDto;
import com.negociadororq.application.controller.dto.NegociacionResponseDto;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.domain.model.*;
import com.negociadororq.enums.EstadoNegociacion;
import com.negociadororq.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NegociacionFactoringComfirmingServiceImpl implements NegociacionFactoringComfirmingService {

    private final FacturaRepository facturaRepository;
    private final NegociacionRepository negociacionRepository;
    private final UserRepository userRepository;
    private final NotificacionService notificacionService;
    private final ProcesarFacturaNegociacionService procesarFacturaNegociacionService;


    /**
     * Crea una negociación procesando factura a factura:
     * - valida cupo operador, empresa y cada fondeador-relación
     * - si una factura no tiene cupo con algún actor, se marca como NO_NEGOCIADA (no se toca su estado en BD)
     * - las facturas que si pasan, se actualizan y se descuentan cupos correspondientes
     * - devuelve en errores la lista de facturas que no se negociaron y la razón
     */
    @Override
    @Transactional
    public NegociacionResponseDto crearNegociacion(NegociacionRequestDto request) {


        List<String> errores = new ArrayList<>();
        List<FacturaNegociada> facturasAceptadas = new ArrayList<>();
        List<String> facturasRechazadas = new ArrayList<>();

        // validar que el operador existe (lanzará excepción si no existe)
        userRepository.findByUsername(request.getRutOperador())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe operador con username " + request.getRutOperador(), "OPERADOR_NO_ENCONTRADO"));

        /* ======================================================
           1. Agrupar facturas por pagador SOLO en CONFIRMING
        ====================================================== */
        Map<String, List<FacturaNegociada>> facturasPorPagador = new HashMap<>();

        for (FacturaNegociada fn : request.getFacturas()) {

            Factura factura = facturaRepository.findByNumeroFactura(fn.getNumeroFactura())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Factura no encontrada: " + fn.getNumeroFactura(), "FACTURA_NO_ENCONTRADA"));

            facturasPorPagador
                    .computeIfAbsent(factura.getRutPagador(), k -> new ArrayList<>())
                    .add(fn);
        }

        for (FacturaNegociada facturaReq : request.getFacturas()) {

            try {
                FacturaNegociada procesada = procesarFacturaNegociacionService.procesarFactura(request, facturaReq);
                facturasAceptadas.add(procesada);

            } catch (Exception e) {
                log.error("Error procesando factura {}", facturaReq.getNumeroFactura(), e);

                errores.add(e.getMessage());
                facturasRechazadas.add(facturaReq.getNumeroFactura() + " -> " + e.getMessage());
            }
        }


        Set<String> pagadores = facturasPorPagador.keySet();

        // Construir negociación resumen con estado según resultados
        Negociacion negociacion = new Negociacion();
        negociacion.setTipoOperacion(request.getTipoOperacion());
        negociacion.setRutEmpresa(request.getRutEmpresa());

        if (pagadores.size() == 1) {
            negociacion.setRutPagador(pagadores.iterator().next());
            negociacion.setRutPagadores(null);
        } else {
            negociacion.setRutPagador(null);
            negociacion.setRutPagadores(new ArrayList<>(pagadores));
        }

        String rutProveedor;

        if ("PROVEEDOR".equalsIgnoreCase(request.getRolEmpresa())) {
            rutProveedor = request.getRutEmpresa();
        } else {
            rutProveedor = facturasAceptadas.isEmpty()
                    ? null
                    : facturasAceptadas.get(0).getRutProveedor();
        }
        negociacion.setRutProveedor(rutProveedor);
        negociacion.setFacturas(facturasAceptadas);
        negociacion.setRutFondeadores(facturasAceptadas.stream()
                .flatMap(f -> Optional.ofNullable(f.getFondeadores()).orElse(Collections.emptyList()).stream())
                .map(FondeadorAsignacion::getRutFondeador)
                .distinct()
                .collect(Collectors.toList()));

        // total negociado = suma aceptadas
        BigDecimal totalAceptado = facturasAceptadas.stream()
                .map(f -> Optional.ofNullable(f.getValorFactura()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        negociacion.setTotalNegociado(totalAceptado);

        String estado;
        if (facturasAceptadas.isEmpty()) estado = EstadoNegociacion.RECHAZADA.name();
        else if (!facturasRechazadas.isEmpty()) estado = EstadoNegociacion.PENDIENTE.name();
        else estado =EstadoNegociacion.PENDIENTE.name();

        negociacion.setEstado(estado);
        List<String> allErrores = new ArrayList<>(errores);

        // añadir motivos concretos de facturas rechazadas
        negociacion.setErrores(allErrores);
        negociacion.setFechaCreacion(LocalDateTime.now());
        negociacion.setFechaUltimaActualizacion(LocalDateTime.now());
        negociacionRepository.save(negociacion);

        notificacionService.notificarCreacionNegociacion(negociacion);

        // preparar respuesta
        NegociacionResponseDto response = new NegociacionResponseDto();
        response.setIdNegociacion(negociacion.getId());
        response.setRutEmpresa(request.getRutEmpresa());
        response.setTipoOperacion(request.getTipoOperacion());
        response.setFacturas(facturasAceptadas);
        response.setFondeadoresAsignados(negociacion.getRutFondeadores().stream()
                .map(r -> FondeadorAsignacion.builder().rutFondeador(r).nombreFondeador("DESCONOCIDO").build())
                .collect(Collectors.toList()));
        response.setTotalNegociado(totalAceptado);
        response.setEstado(estado);
        response.setErrores(allErrores);

        return response;
    }

}

