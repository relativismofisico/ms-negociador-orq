package com.negociadororq.application.service;

import com.negociadororq.application.dto.EndosoRequestDto;
import com.negociadororq.application.dto.EndosoResponseDto;
import com.negociadororq.application.exception.PeticionInvalidaException;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorQuota;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.enums.EstadoNegociacion;
import com.negociadororq.application.port.output.EndosoOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.NegociacionPatchOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
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
                            facturaNegociadaPivote.getNumeroFactura().equals(numeroFactura) &&
                                    esFacturaValidaParaOperacion(
                                            facturaNegociadaPivote,
                                            request.getRutActor(),
                                            tipoOperacion
                                    )
                    )
                    .findFirst()
                    .orElse(null);

            if (facturaNegociada == null) {
                String actor = "FACTORING".equalsIgnoreCase(tipoOperacion)
                        ? "PROVEEDOR"
                        : "PAGADOR";

                facturasRechazadas.add(numeroFactura + " (no pertenece al " + actor + ")");
                continue;
            }

            Factura factura = facturaPort.findByNumeroFactura(numeroFactura)
                    .orElse(null);

            if (factura == null) {
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
        List<Endoso> endososCreados = new ArrayList<>();

        for (Map.Entry<String, List<FacturaValidada>> entry : porFondeador.entrySet()) {
            String rutFondeador = entry.getKey();
            List<FacturaValidada> facturasDelFondeador = entry.getValue();

            boolean permiteAgrupar = permiteEndosoGrupal(rutFondeador, negociacion, tipoOperacion);

            List<List<FacturaValidada>> lotes = permiteAgrupar
                    ? List.of(facturasDelFondeador)
                    : facturasDelFondeador.stream().map(List::of).toList();

            for (List<FacturaValidada> lote : lotes) {
                Endoso endoso = crearEndoso(request, negociacion, rutFondeador, lote);
                endosoPort.save(endoso);
                endososCreados.add(endoso);

                for (FacturaValidada fv : lote) {
                    facturasCreadas.add(fv.numeroFactura());

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

        String estadoGlobal =
                facturasCreadas.isEmpty() ? "RECHAZADO"
                        : facturasRechazadas.isEmpty() ? "PENDIENTE"
                        : "PARCIAL";

        return EndosoResponseDto.builder()
                .idNegociacion(request.getIdNegociacion())
                .estadoEndoso(estadoGlobal)
                .facturasEndosadas(facturasCreadas)
                .facturasRechazadas(facturasRechazadas)
                .build();
    }

    private Endoso crearEndoso(EndosoRequestDto request, Negociacion negociacion, String rutFondeador,
                                List<FacturaValidada> lote) {
        FacturaValidada primera = lote.get(0);

        Endoso endoso = Endoso.builder()
                .negociacionId(request.getIdNegociacion())
                .numerosFactura(lote.stream().map(FacturaValidada::numeroFactura).toList())
                .rutActorEndosa(request.getRutActor())
                .rutProveedor(primera.factura().getRutProveedor())
                .tipoOperacion(negociacion.getTipoOperacion())
                .rutPagador(primera.facturaNegociada().getRutPagador())
                .rutFondeador(rutFondeador)
                .estado(EstadoEndoso.EN_REVISION)
                .fechaCreacion(LocalDateTime.now())
                .fechaActualizacion(LocalDateTime.now())
                .build();

        // El firmante (usuario individual de la empresa proveedora) solo se conoce
        // aquí en Factoring, donde el Proveedor es quien solicita el endoso. En
        // Confirming quien solicita es el Pagador; el firmante real (Proveedor) se
        // resuelve más adelante, cuando aprueba, y el documento se regenera —
        // ver AprobacionEndosoDescuentoServiceImpl.
        String rutOperadorFirmante = "FACTORING".equalsIgnoreCase(negociacion.getTipoOperacion().getTipo())
                ? request.getRutOperador()
                : null;

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
            quota = supplierId == null
                    ? Optional.empty()
                    : fondeadorQuotaPort.findByFunderIdAndSupplierId(funderId, supplierId);
        } else {
            Long payerId = companyPort.findByNit(negociacion.getRutPagador())
                    .map(Company::getIdeCompany).orElse(null);
            quota = payerId == null
                    ? Optional.empty()
                    : fondeadorQuotaPort.findByFunderIdAndPayerId(funderId, payerId);
        }

        return quota.map(FondeadorQuota::getEndosoGrupal).map(Boolean.TRUE::equals).orElse(false);
    }

    private boolean esFacturaValidaParaOperacion(FacturaNegociada factura, String rutSolicitante, String tipoOperacion) {

        if ("FACTORING".equalsIgnoreCase(tipoOperacion)) {
            return factura.getRutProveedor().equals(rutSolicitante);
        }

        if ("CONFIRMING".equalsIgnoreCase(tipoOperacion)) {
            return factura.getRutPagador().equals(rutSolicitante);
        }

        return false;
    }

}
