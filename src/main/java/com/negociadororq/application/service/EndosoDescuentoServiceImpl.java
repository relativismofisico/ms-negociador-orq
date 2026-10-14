package com.negociadororq.application.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.negociadororq.application.dto.EndosoRequestDto;
import com.negociadororq.application.dto.EndosoResponseDto;
import com.negociadororq.application.exception.PeticionInvalidaException;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.EndosoOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.port.output.NegociacionPatchOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.application.util.VigenciaDescuentoValidator;
import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.enums.EstadoNegociacion;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.FondeadorQuota;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.transaction.annotation.Transactional;


/**
 * Implementación de {@link EndosoDescuentoService}.
 */
@RequiredArgsConstructor
@Log
public class EndosoDescuentoServiceImpl implements EndosoDescuentoService {

    private final NegociacionOutputPort negociacionPort;
    private final EndosoOutputPort endosoPort;
    private final FacturaOutputPort facturaPort;
    private final NotificacionService notificacionService;
    private final FacturaPatchOutputPort facturaPatchPort;
    private final DocumentoEndosoService documentoEndosoService;
    private final NegociacionPatchOutputPort negociacionPatchPort;
    private final CompanyOutputPort companyPort;
    private final FondeadorQuotaOutputPort fondeadorQuotaPort;
    private final UserOutputPort userPort;
    private final CupoReversionService cupoReversionService;
    private final ClockPort clock;
    private final AuditoriaNegociacionService auditoria;

    private record FacturaValidada(String numeroFactura, Factura factura, FacturaNegociada facturaNegociada) {
    }

    @Override
    @Transactional
    public EndosoResponseDto registrarEndoso(EndosoRequestDto request) {

        Negociacion negociacion = negociacionPort.findById(request.getIdNegociacion())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Negociación no encontrada: " + request.getIdNegociacion(), "NEGOCIACION_NO_ENCONTRADA"));

        if (request.getNumerosFactura() == null || request.getNumerosFactura().isEmpty()) {
            throw new PeticionInvalidaException("Debe indicar al menos una factura", "FACTURAS_REQUERIDAS");
        }

        String tipoOperacion = negociacion.getTipoOperacion().getTipo();

        List<String> facturasRechazadas = new ArrayList<>();
        List<FacturaValidada> facturasValidas = new ArrayList<>();

        for (String numeroFactura : request.getNumerosFactura()) {

            FacturaNegociada facturaNegociada = negociacion.getFacturas().stream()
                    .filter(facturaNegociadaPivote ->
                            facturaNegociadaPivote.getNumeroFactura().equals(numeroFactura)
                                    && esFacturaValidaParaOperacion(
                                            facturaNegociadaPivote,
                                            request.getRutActor(),
                                            tipoOperacion
                                    )
                    )
                    .findFirst()
                    .orElse(null);

            if (facturaNegociada == null) {
                String actor;
                if ("FACTORING".equalsIgnoreCase(tipoOperacion)) {
                    actor = "PROVEEDOR";
                } else {
                    actor = "PAGADOR";
                }

                facturasRechazadas.add(numeroFactura + " (no pertenece al " + actor + ")");
                continue;
            }

            Factura factura = facturaPort.findByNumeroFactura(numeroFactura)
                    .orElse(null);

            if (factura == null) {
                // Hueco preexistente (no introducido por vigenciaDescuento): la
                // negociación consumió cupos para esta factura, pero el documento
                // ya no está en BD — no hay factura que revertir a SIN_NEGOCIAR,
                // solo los cupos.
                revertirCupos(facturaNegociada.getValorFactura(), facturaNegociada.getFondeadores(), tipoOperacion,
                        request.getRutActor(), request.getRutOperador(),
                        facturaNegociada.getRutProveedor(), facturaNegociada.getRutPagador());
                facturasRechazadas.add(numeroFactura + " (factura no existe en BD)");
                continue;
            }

            boolean facturaYaEndosada = endosoPort.existsByNegociacionIdAndNumeroFactura(
                            request.getIdNegociacion(),
                            factura.getNumeroFactura());

            if (facturaYaEndosada) {
                facturasRechazadas.add(numeroFactura + " (ya tiene endoso)");
                continue;
            }

            if (!vigenciaPermiteEndosar(factura, facturaNegociada, tipoOperacion)) {
                BigDecimal valorFactura = Optional.ofNullable(factura.getValorNetoFactura())
                        .orElse(factura.getValorFactura());
                revertirCupos(valorFactura, facturaNegociada.getFondeadores(), tipoOperacion,
                        request.getRutActor(), request.getRutOperador(),
                        factura.getRutProveedor(), factura.getRutPagador());
                factura.setEstadoNegociacion("SIN_NEGOCIAR");
                facturaPort.save(factura);
                facturasRechazadas.add(numeroFactura + " (vigencia del descuento vencida)");
                continue;
            }

            facturasValidas.add(new FacturaValidada(numeroFactura, factura, facturaNegociada));
        }

        // Agrupa las facturas válidas por fondeador: cada grupo se convierte en un
        // solo Endoso (si ese fondeador permite agrupar, endosoGrupal=true) o en un
        // Endoso por factura (comportamiento por defecto, endosoGrupal=false).
        Map<String, List<FacturaValidada>> porFondeador = facturasValidas.stream()
                .collect(Collectors.groupingBy(
                        fv -> fv.facturaNegociada().getFondeadores().get(0).getRutFondeador(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<String> facturasCreadas = new ArrayList<>();
        List<String> cufesCreados = new ArrayList<>();
        List<Endoso> endososCreados = new ArrayList<>();

        for (Map.Entry<String, List<FacturaValidada>> entry : porFondeador.entrySet()) {
            String rutFondeador = entry.getKey();
            List<FacturaValidada> facturasDelFondeador = entry.getValue();

            boolean permiteAgrupar = permiteEndosoGrupal(rutFondeador, negociacion, tipoOperacion);

            List<List<FacturaValidada>> lotes;
            if (permiteAgrupar) {
                lotes = List.of(facturasDelFondeador);
            } else {
                lotes = facturasDelFondeador.stream().map(List::of).toList();
            }

            for (List<FacturaValidada> lote : lotes) {
                Endoso endoso = endosoPort.save(crearEndoso(request, negociacion, rutFondeador, lote));
                endososCreados.add(endoso);
                auditoria.endosoRegistrado(endoso, request);

                for (FacturaValidada fv : lote) {
                    facturasCreadas.add(fv.numeroFactura());
                    cufesCreados.add(fv.factura().getCufe());

                    facturaPatchPort.actualizarEstadoPorId(
                            fv.factura().getId(), EstadoFirma.EN_REVISION, EstadoNegociacion.ENDOSADA);

                    negociacionPatchPort.actualizarEstadoEndosoPorId(
                            negociacion.getId(), fv.numeroFactura(), true);
                }
            }
        }

        // Se notifica una sola vez por todos los endosos creados en esta solicitud: si
        // varios Endoso (o facturas dentro de un mismo Endoso agrupado) van al mismo
        // destinatario, se agrupan en un único correo con la lista de facturas en vez
        // de mandar un correo por factura.
        notificacionService.notificarEndosos(endososCreados);

        String estadoGlobal;
        if (facturasCreadas.isEmpty()) {
            estadoGlobal = "RECHAZADO";
        } else if (facturasRechazadas.isEmpty()) {
            estadoGlobal = "PENDIENTE";
        } else {
            estadoGlobal = "PARCIAL";
        }

        return EndosoResponseDto.builder()
                .idNegociacion(request.getIdNegociacion())
                .estadoEndoso(estadoGlobal)
                .facturasEndosadas(facturasCreadas)
                .cufesEndosadas(cufesCreados)
                .facturasRechazadas(facturasRechazadas)
                .build();
    }

    private Endoso crearEndoso(EndosoRequestDto request, Negociacion negociacion, String rutFondeador,
                                List<FacturaValidada> lote) {
        FacturaValidada primera = lote.get(0);

        Endoso endoso = Endoso.builder()
                .negociacionId(request.getIdNegociacion())
                .numerosFactura(lote.stream().map(FacturaValidada::numeroFactura).toList())
                // cufes: array paralelo a numerosFactura, mismo orden. La factura ya
                // está en memoria (fue validada arriba), así que no hay query nueva.
                .cufes(lote.stream().map(fv -> fv.factura().getCufe()).toList())
                .rutActorEndosa(request.getRutActor())
                .rutProveedor(primera.factura().getRutProveedor())
                .tipoOperacion(negociacion.getTipoOperacion())
                .rutPagador(primera.facturaNegociada().getRutPagador())
                .rutFondeador(rutFondeador)
                .estado(EstadoEndoso.EN_REVISION)
                .fechaCreacion(clock.now())
                .fechaActualizacion(clock.now())
                .build();

        // El firmante (usuario individual de la empresa proveedora) solo se conoce
        // aquí en Factoring, donde el Proveedor es quien solicita el endoso. En
        // Confirming quien solicita es el Pagador; el firmante real (Proveedor) se
        // resuelve más adelante, cuando aprueba, y el documento se regenera —
        // ver AprobacionEndosoDescuentoServiceImpl.
        String rutOperadorFirmante;
        if ("FACTORING".equalsIgnoreCase(negociacion.getTipoOperacion().getTipo())) {
            rutOperadorFirmante = request.getRutOperador();
        } else {
            rutOperadorFirmante = null;
        }

        documentoEndosoService.generarDocumentoFormal(
                endoso, negociacion,
                lote.stream().map(FacturaValidada::factura).toList(),
                lote.stream().map(FacturaValidada::facturaNegociada).toList(),
                rutOperadorFirmante);

        return endoso;
    }

    private boolean permiteEndosoGrupal(String rutFondeador, Negociacion negociacion, String tipoOperacion) {
        Long funderId = companyPort.findByNit(rutFondeador).map(Company::getIdeCompany).orElse(null);
        if (funderId == null) {
            return false;
        }

        Optional<FondeadorQuota> quota;
        if ("FACTORING".equalsIgnoreCase(tipoOperacion)) {
            Long supplierId = companyPort.findByNit(negociacion.getRutProveedor())
                    .map(Company::getIdeCompany).orElse(null);
            if (supplierId == null) {
                quota = Optional.empty();
            } else {
                quota = fondeadorQuotaPort.findByFunderIdAndSupplierId(funderId, supplierId);
            }
        } else {
            Long payerId = companyPort.findByNit(negociacion.getRutPagador())
                    .map(Company::getIdeCompany).orElse(null);
            if (payerId == null) {
                quota = Optional.empty();
            } else {
                quota = fondeadorQuotaPort.findByFunderIdAndPayerId(funderId, payerId);
            }
        }

        return quota.map(FondeadorQuota::getEndosoGrupal).map(Boolean.TRUE::equals).orElse(false);
    }

    private boolean esFacturaValidaParaOperacion(FacturaNegociada factura, String rutSolicitante, String tipoOperacion) {

        if ("FACTORING".equalsIgnoreCase(tipoOperacion)) {
            return factura.getRutProveedor().equals(rutSolicitante);
        }

        return "CONFIRMING".equalsIgnoreCase(tipoOperacion) && factura.getRutPagador().equals(rutSolicitante);
    }

    // El chequeo real de vigenciaDescuento ocurre acá (a diferencia de la
    // negociación, donde solo DIAS_ANTES_VENCIMIENTO puede bloquear): la
    // factura ya tiene fechaNegociacionFactura persistida, así que
    // PRIMEROS_DIAS también puede bloquear en este punto. Si no hay
    // FondeadorQuota resuelta para el primer fondeador de la factura (mismo
    // criterio que permiteEndosoGrupal), no hay vigencia que validar.
    private boolean vigenciaPermiteEndosar(Factura factura, FacturaNegociada facturaNegociada, String tipoOperacion) {
        if (facturaNegociada.getFondeadores() == null || facturaNegociada.getFondeadores().isEmpty()) {
            return true;
        }

        String rutFondeador = facturaNegociada.getFondeadores().get(0).getRutFondeador();
        return resolverFondeadorQuota(rutFondeador, factura, tipoOperacion)
                .map(quota -> VigenciaDescuentoValidator.dentroDeVigencia(
                        clock.now().toLocalDate(), factura.getFechaNegociacionFactura(), factura.getFechavencimientoFactura(),
                        quota.getVigenciaDescuento(), quota.getDiasVigencia()))
                .orElse(true);
    }

    private Optional<FondeadorQuota> resolverFondeadorQuota(String rutFondeador, Factura factura, String tipoOperacion) {
        Long funderId = companyPort.findByNit(rutFondeador).map(Company::getIdeCompany).orElse(null);
        if (funderId == null) {
            return Optional.empty();
        }

        if ("FACTORING".equalsIgnoreCase(tipoOperacion)) {
            Long supplierId = companyPort.findByNit(factura.getRutProveedor()).map(Company::getIdeCompany).orElse(null);
            if (supplierId == null) {
                return Optional.empty();
            }
            return fondeadorQuotaPort.findByFunderIdAndSupplierId(funderId, supplierId);
        }

        Long payerId = companyPort.findByNit(factura.getRutPagador()).map(Company::getIdeCompany).orElse(null);
        if (payerId == null) {
            return Optional.empty();
        }
        return fondeadorQuotaPort.findByFunderIdAndPayerId(funderId, payerId);
    }

    // Reversión de cupos para facturas rechazadas al endosar (vigencia vencida,
    // o el documento ya no existe en BD): mismo bloque CupoReversionService que
    // ya usa AprobacionEndosoDescuentoServiceImpl.revertirCupos para el rechazo
    // por OTP, replicado acá porque ese método no está expuesto por ninguna
    // interfaz inyectable (vive solo en esa clase, llamado internamente).
    private void revertirCupos(BigDecimal valorFactura, List<FondeadorAsignacion> fondeadores, String tipoOperacion,
            String rutEmpresa, String rutOperador, String rutProveedor, String rutPagador) {

        BigDecimal valor = Optional.ofNullable(valorFactura).orElse(BigDecimal.ZERO);

        userPort.findByUsername(rutOperador).map(User::getIdeUser)
                .ifPresent(userId -> cupoReversionService.revertirCupoUsuario(userId, valor));

        companyPort.findByNit(rutEmpresa).map(Company::getIdeCompany)
                .ifPresent(companyId -> cupoReversionService.revertirCupoEmpresa(companyId, valor));

        if (fondeadores == null) {
            return;
        }

        for (FondeadorAsignacion fon : fondeadores) {
            Long funderId = companyPort.findByNit(fon.getRutFondeador()).map(Company::getIdeCompany).orElse(null);
            if (funderId == null) {
                continue;
            }

            if ("FACTORING".equalsIgnoreCase(tipoOperacion)) {
                companyPort.findByNit(rutProveedor).map(Company::getIdeCompany)
                        .ifPresent(supplierId ->
                                cupoReversionService.revertirCupoFondeadorFactoring(funderId, supplierId, valor));
            } else if ("CONFIRMING".equalsIgnoreCase(tipoOperacion)) {
                companyPort.findByNit(rutPagador).map(Company::getIdeCompany)
                        .ifPresent(payerId ->
                                cupoReversionService.revertirCupoFondeadorConfirming(funderId, payerId, valor));
            }
        }
    }

}
