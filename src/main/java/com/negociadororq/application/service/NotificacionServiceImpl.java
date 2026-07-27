package com.negociadororq.application.service;

import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.service.notificacion.CuerpoNotificacionFactory;
import com.negociadororq.application.service.notificacion.DestinatarioResolver;
import com.negociadororq.domain.event.DestinatarioEmail;
import com.negociadororq.domain.event.NotificacionEmailEvent;
import com.negociadororq.domain.model.AprobacionFacturaRequest;
import com.negociadororq.domain.model.AprobacionRequest;
import com.negociadororq.domain.model.Company;
import com.negociadororq.domain.model.Endoso;
import com.negociadororq.domain.model.Factura;
import com.negociadororq.domain.model.FacturaNegociada;
import com.negociadororq.domain.model.FondeadorAsignacion;
import com.negociadororq.domain.model.Negociacion;
import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.domain.enums.TipoNotificacion;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificacionServiceImpl implements NotificacionService {

    private static final String SIN_DATO = "N/A";
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final OutboxService outboxService;
    private final CuerpoNotificacionFactory cuerpoFactory;
    private final NegociacionOutputPort negociacionPort;
    private final DestinatarioResolver destinatarioResolver;
    private final CompanyOutputPort companyPort;
    private final FacturaOutputPort facturaPort;

    @Value("${topics.notificacion-email}")
    private String topicNotificacionEmail;

    @Value("${platform.operador.url:http://localhost:4800}")
    private String urlPlataformaOperador;

    @Override
    public void notificarCreacionNegociacion(Negociacion negociacion) {
        List<DestinatarioEmail> destinatarios =
                destinatarioResolver.resolverParaCreacionNegociacion(negociacion);

        Map<String, Object> data = safeMap(
                "tipoOperacion", negociacion.getTipoOperacion() != null
                        ? negociacion.getTipoOperacion().getTipo() : null,
                "claseOperacion", negociacion.getTipoOperacion() != null
                        ? negociacion.getTipoOperacion().getClase() : null,
                "nombrePagador", nombreCompania(negociacion.getRutPagador()),
                "nombreProveedor", nombreCompania(negociacion.getRutProveedor()),
                "nombresFondeadores", nombresFondeadoresNegociacion(negociacion),
                "totalNegociado", formatearMonto(negociacion.getTotalNegociado()),
                "fechaCreacion", formatearFecha(negociacion.getFechaCreacion()),
                "facturas", detalleFacturas(negociacion)
        );

        NotificacionEmailEvent event = NotificacionEmailEvent.builder()
                .idNegociacion(negociacion.getId())
                .destinatarios(destinatarios)
                .asunto("NEGOCIACION")
                .data(data)
                .tipoNotificacion(TipoNotificacion.NEGOCIACION_CREADA.name())
                .fecha(LocalDateTime.now())
                .build();

        outboxService.guardarEvento(negociacion.getId(), topicNotificacionEmail, event);
    }

    // ------------------------------------------------------------
    // Helpers para el correo de creación de negociación: la plantilla
    // debe mostrar nombres (no ruts), la clase de operación, el total
    // formateado sin notación científica y el detalle de cada factura.
    // ------------------------------------------------------------

    private String nombreCompania(String nit) {
        if (nit == null) {
            return SIN_DATO;
        }
        return companyPort.findByNit(nit)
                .map(Company::getNameCompany)
                .orElse(nit);
    }

    private String nombresFondeadoresNegociacion(Negociacion negociacion) {
        List<FacturaNegociada> facturas = negociacion.getFacturas();
        if (facturas == null) {
            return SIN_DATO;
        }
        String nombres = facturas.stream()
                .flatMap(factura -> nombresFondeadoresFactura(factura).stream())
                .distinct()
                .collect(Collectors.joining(", "));

        return nombres.isEmpty() ? SIN_DATO : nombres;
    }

    private List<String> nombresFondeadoresFactura(FacturaNegociada factura) {
        List<FondeadorAsignacion> fondeadores = factura.getFondeadores();
        if (fondeadores == null) {
            return Collections.emptyList();
        }
        return fondeadores.stream()
                .map(FondeadorAsignacion::getNombreFondeador)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Map<String, Object>> detalleFacturas(Negociacion negociacion) {
        List<FacturaNegociada> facturas = negociacion.getFacturas();
        if (facturas == null) {
            return Collections.emptyList();
        }
        return facturas.stream()
                .map(this::detalleFactura)
                .toList();
    }

    private Map<String, Object> detalleFactura(FacturaNegociada factura) {
        String nombresFondeadores = String.join(", ", nombresFondeadoresFactura(factura));

        return safeMap(
                "numeroFactura", factura.getNumeroFactura(),
                "valorFactura", formatearMonto(factura.getValorFactura()),
                "nombrePagador", nombreCompania(factura.getRutPagador()),
                "nombreFondeador", nombresFondeadores.isEmpty() ? SIN_DATO : nombresFondeadores
        );
    }

    private String formatearMonto(BigDecimal monto) {
        if (monto == null) {
            return SIN_DATO;
        }
        BigDecimal redondeado = monto.setScale(2, RoundingMode.HALF_UP);
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols(new Locale("es", "CO"));
        DecimalFormat formato = new DecimalFormat("#,##0.00", simbolos);
        return formato.format(redondeado);
    }

    private String formatearFecha(LocalDateTime fecha) {
        return fecha != null ? fecha.format(FORMATO_FECHA) : SIN_DATO;
    }

    @Override
    public void notificarEndoso(Endoso endoso) {
        List<DestinatarioEmail> destinatarios =
                destinatarioResolver.resolverParaEndoso(endoso);

        boolean esConfirming = endoso.getTipoOperacion() != null
                && "CONFIRMING".equalsIgnoreCase(endoso.getTipoOperacion().getTipo());

        String tituloAccion = esConfirming ? "Solicitud de Descuento" : "Endoso";
        String tituloAccionCorta = esConfirming ? "Descuento" : "Endoso";
        String mensajeIntro = esConfirming
                ? "Se ha generado una solicitud de descuento que requiere tu aprobación."
                : "Se ha generado un endoso que requiere tu aprobación.";
        String textoBoton = esConfirming ? "Aprobar Solicitud de Descuento" : "Aprobar Endoso";

        List<String> numerosFactura = Optional.ofNullable(endoso.getNumerosFactura()).orElse(List.of());
        String estadoAmigable = estadoEndosoAmigable(endoso.getEstado());

        List<Map<String, Object>> facturas = numerosFactura.stream()
                .map(numeroFactura -> safeMap(
                        "numeroFactura", numeroFactura,
                        "valorFactura", formatearMonto(valorFactura(numeroFactura)),
                        "estadoAmigable", estadoAmigable
                ))
                .toList();

        BigDecimal valorTotal = sumarValores(numerosFactura);

        Map<String, Object> data = safeMap(
                "idEndoso", endoso.getId(),
                "numeroFactura", String.join(", ", numerosFactura),
                "valorFactura", formatearMonto(valorTotal),
                "tipoOperacion", endoso.getTipoOperacion().getTipo(),
                "rutProveedor", endoso.getRutProveedor(),
                "rutPagador", endoso.getRutPagador(),
                "rutFondeador", endoso.getRutFondeador(),
                "estado", endoso.getEstado().name(),
                "estadoAmigable", estadoAmigable,
                "nombreSolicitante", nombreCompania(endoso.getRutActorEndosa()),
                "documento", endoso.getDocumentoPdf(),
                "cantidadFacturas", numerosFactura.size(),
                "valorTotalFacturas", formatearMonto(valorTotal),
                "facturas", facturas,
                "tituloAccion", tituloAccion,
                "tituloAccionCorta", tituloAccionCorta,
                "mensajeIntro", mensajeIntro,
                "textoBoton", textoBoton,
                "urlAprobacion", urlPlataformaOperador,
                "anio", String.valueOf(LocalDateTime.now().getYear())
        );

        NotificacionEmailEvent event = NotificacionEmailEvent.builder()
                .idNegociacion(endoso.getNegociacionId())
                .destinatarios(destinatarios)
                .asunto("ENDOSO")
                .data(data)
                .tipoNotificacion(TipoNotificacion.ENDOSO_CREADO.name())
                .fecha(LocalDateTime.now())
                .build();

        outboxService.guardarEvento(endoso.getNegociacionId(), topicNotificacionEmail, event);
    }

    @Override
    public void notificarEndosos(List<Endoso> endosos) {
        if (endosos == null || endosos.isEmpty()) {
            return;
        }

        if (endosos.size() == 1) {
            notificarEndoso(endosos.get(0));
            return;
        }

        Map<String, List<Endoso>> gruposPorDestinatario = endosos.stream()
                .collect(Collectors.groupingBy(this::claveDestinatario, LinkedHashMap::new, Collectors.toList()));

        gruposPorDestinatario.values().forEach(this::notificarGrupoEndosos);
    }

    private String claveDestinatario(Endoso endoso) {
        return String.join("|",
                endoso.getTipoOperacion() != null ? endoso.getTipoOperacion().getTipo() : SIN_DATO,
                Objects.toString(endoso.getRutProveedor(), SIN_DATO),
                Objects.toString(endoso.getRutPagador(), SIN_DATO),
                Objects.toString(endoso.getRutFondeador(), SIN_DATO));
    }

    private void notificarGrupoEndosos(List<Endoso> grupo) {
        if (grupo.size() == 1) {
            notificarEndoso(grupo.get(0));
            return;
        }

        Endoso representante = grupo.get(0);
        List<DestinatarioEmail> destinatarios = destinatarioResolver.resolverParaEndoso(representante);

        boolean esConfirming = representante.getTipoOperacion() != null
                && "CONFIRMING".equalsIgnoreCase(representante.getTipoOperacion().getTipo());

        String tituloAccion = esConfirming ? "Solicitud de Descuento" : "Endoso";
        String tituloAccionCorta = esConfirming ? "Descuento" : "Endoso";
        String mensajeIntro = esConfirming
                ? "Se han generado varias solicitudes de descuento que requieren tu aprobación."
                : "Se han generado varios endosos que requieren tu aprobación.";
        String textoBoton = esConfirming ? "Aprobar Solicitudes de Descuento" : "Aprobar Endosos";

        // Cada Endoso del grupo puede a su vez cubrir varias facturas (endosoGrupal
        // habilitado en el fondeador), así que se aplana antes de listar.
        List<String> todasLasFacturas = grupo.stream()
                .flatMap(endoso -> Optional.ofNullable(endoso.getNumerosFactura()).orElse(List.of()).stream())
                .toList();

        List<Map<String, Object>> facturas = grupo.stream()
                .flatMap(endoso -> Optional.ofNullable(endoso.getNumerosFactura()).orElse(List.of()).stream()
                        .map(numeroFactura -> safeMap(
                                "numeroFactura", numeroFactura,
                                "valorFactura", formatearMonto(valorFactura(numeroFactura)),
                                "estadoAmigable", estadoEndosoAmigable(endoso.getEstado())
                        )))
                .toList();

        BigDecimal valorTotal = sumarValores(todasLasFacturas);

        Map<String, Object> data = safeMap(
                "cantidadFacturas", todasLasFacturas.size(),
                "valorTotalFacturas", formatearMonto(valorTotal),
                "tipoOperacion", representante.getTipoOperacion().getTipo(),
                "rutProveedor", representante.getRutProveedor(),
                "rutPagador", representante.getRutPagador(),
                "rutFondeador", representante.getRutFondeador(),
                "nombreSolicitante", nombreCompania(representante.getRutActorEndosa()),
                "facturas", facturas,
                "tituloAccion", tituloAccion,
                "tituloAccionCorta", tituloAccionCorta,
                "mensajeIntro", mensajeIntro,
                "textoBoton", textoBoton,
                "urlAprobacion", urlPlataformaOperador,
                "anio", String.valueOf(LocalDateTime.now().getYear())
        );

        NotificacionEmailEvent event = NotificacionEmailEvent.builder()
                .idNegociacion(representante.getNegociacionId())
                .destinatarios(destinatarios)
                .asunto("ENDOSO")
                .data(data)
                .tipoNotificacion(TipoNotificacion.ENDOSO_CREADO.name())
                .fecha(LocalDateTime.now())
                .build();

        outboxService.guardarEvento(representante.getNegociacionId(), topicNotificacionEmail, event);
    }

    // null (no "N/A" en el email) cuando ninguna factura tiene valor conocido, en
    // vez de 0 — que induciría a pensar que las facturas valen cero.
    private BigDecimal sumarValores(List<String> numerosFactura) {
        List<BigDecimal> valores = numerosFactura.stream()
                .map(this::valorFactura)
                .filter(Objects::nonNull)
                .toList();

        return valores.isEmpty() ? null : valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal valorFactura(String numeroFactura) {
        if (numeroFactura == null) {
            return null;
        }
        return facturaPort.findByNumeroFactura(numeroFactura)
                .map(Factura::getValorFactura)
                .orElse(null);
    }

    private String estadoEndosoAmigable(EstadoEndoso estado) {
        if (estado == null) {
            return SIN_DATO;
        }
        return switch (estado) {
            case PENDIENTE -> "Pendiente de aprobación";
            case EN_REVISION -> "En revisión";
            case PENDIENTE_OTP -> "Pendiente de confirmación";
            case ACEPTADO -> "Aceptado";
            case RECHAZADO -> "Rechazado";
            case PARCIAL -> "Parcialmente aprobado";
        };
    }

    @Override
    public void notificarFirmaEndoso(AprobacionRequest aprobacion) {
        Negociacion negociacion = negociacionPort
                .findById(aprobacion.getIdNegociacion())
                .orElseThrow(() -> new RuntimeException("Negociación no encontrada"));

        List<DestinatarioEmail> destinatarios =
                destinatarioResolver.resolverParaFirmaEndoso(negociacion, aprobacion.getRutEmpresa());

        boolean esConfirming = negociacion.getTipoOperacion() != null
                && "CONFIRMING".equalsIgnoreCase(negociacion.getTipoOperacion().getTipo());

        String tituloAccion = esConfirming ? "Solicitud de Descuento" : "Endoso";
        String tituloAccionCorta = esConfirming ? "Descuento" : "Endoso";

        List<AprobacionFacturaRequest> facturasRequest = Optional.ofNullable(aprobacion.getFacturas())
                .orElseGet(Collections::emptyList);

        boolean todasAprobadas = !facturasRequest.isEmpty()
                && facturasRequest.stream().allMatch(AprobacionFacturaRequest::isAprobado);

        String tituloResultado = esConfirming
                ? (todasAprobadas ? "Solicitud de Descuento Aprobada" : "Solicitud de Descuento Rechazada")
                : (todasAprobadas ? "Endoso Aprobado" : "Endoso Rechazado");

        List<Map<String, Object>> facturas = facturasRequest.stream()
                .map(f -> safeMap(
                        "numeroFactura", f.getNumeroFactura(),
                        "resultado", f.isAprobado() ? "Aprobada" : "Rechazada"
                ))
                .toList();

        Map<String, Object> data = safeMap(
                "id", negociacion.getId(),
                "tituloAccion", tituloAccion,
                "tituloAccionCorta", tituloAccionCorta,
                "tituloResultado", tituloResultado,
                "tipoOperacion", negociacion.getTipoOperacion() != null
                        ? negociacion.getTipoOperacion().getTipo() : null,
                "claseOperacion", negociacion.getTipoOperacion() != null
                        ? negociacion.getTipoOperacion().getClase() : null,
                "nombreActor", nombreCompania(aprobacion.getRutEmpresa()),
                "facturas", facturas,
                "anio", String.valueOf(LocalDateTime.now().getYear())
        );

        NotificacionEmailEvent event = NotificacionEmailEvent.builder()
                .idNegociacion(negociacion.getId())
                .destinatarios(destinatarios)
                .asunto("FIRMA_ENDOSO")
                .data(data)
                .tipoNotificacion(TipoNotificacion.ENDOSO_FIRMADO.name())
                .fecha(LocalDateTime.now())
                .build();

        outboxService.guardarEvento(negociacion.getId(), topicNotificacionEmail, event);
    }

    @Override
    public void notificarLiquidacionFallida(Negociacion negociacion, String motivo) {
        List<DestinatarioEmail> destinatarios =
                destinatarioResolver.resolverParaLiquidacionFallida(negociacion);

        Map<String, Object> data = safeMap(
                "id", negociacion.getId(),
                "tipoOperacion", negociacion.getTipoOperacion() != null
                        ? negociacion.getTipoOperacion().getTipo() : null,
                "rutPagador", negociacion.getRutPagador(),
                "rutProveedor", negociacion.getRutProveedor(),
                "rutFondeadores", negociacion.getRutFondeadores(),
                "totalNegociado", negociacion.getTotalNegociado(),
                "facturas", negociacion.getFacturas() != null
                        ? negociacion.getFacturas().size() : 0
        );

        NotificacionEmailEvent event = NotificacionEmailEvent.builder()
                .idEvento(UUID.randomUUID().toString())
                .idNegociacion(negociacion.getId())
                .tipoNotificacion(TipoNotificacion.LIQUIDACION_FALLIDA.name())
                .destinatarios(destinatarios)
                .asunto("LIQUIDACION_FALLIDA")
                .data(data)
                .fecha(LocalDateTime.now())
                .build();

        outboxService.guardarEvento(negociacion.getId(), topicNotificacionEmail, event);
    }

    private Map<String, Object> safeMap(Object... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("La cantidad de argumentos debe ser par");
        }
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }
}
