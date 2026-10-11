package com.negociadororq.application.service;

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

import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.service.notificacion.CuerpoNotificacionFactory;
import com.negociadororq.application.service.notificacion.DestinatarioResolver;
import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.domain.enums.TipoNotificacion;
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
import lombok.RequiredArgsConstructor;

/**
 * Implementación de {@link NotificacionService}.
 */
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
    private final ClockPort clock;
    private final String topicNotificacionEmail;
    private final String urlPlataformaOperador;

    @Override
    public void notificarCreacionNegociacion(Negociacion negociacion) {
        List<DestinatarioEmail> destinatarios =
                destinatarioResolver.resolverParaCreacionNegociacion(negociacion);

        String tipoOperacion = null;
        String claseOperacion = null;
        if (negociacion.getTipoOperacion() != null) {
            tipoOperacion = negociacion.getTipoOperacion().getTipo();
            claseOperacion = negociacion.getTipoOperacion().getClase();
        }

        Map<String, Object> data = safeMap(
                "tipoOperacion", tipoOperacion,
                "claseOperacion", claseOperacion,
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
                .fecha(clock.now())
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

        if (nombres.isEmpty()) {
            return SIN_DATO;
        }
        return nombres;
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

        String nombreFondeadorMostrar;
        if (nombresFondeadores.isEmpty()) {
            nombreFondeadorMostrar = SIN_DATO;
        } else {
            nombreFondeadorMostrar = nombresFondeadores;
        }

        return safeMap(
                "numeroFactura", factura.getNumeroFactura(),
                "valorFactura", formatearMonto(factura.getValorFactura()),
                "nombrePagador", nombreCompania(factura.getRutPagador()),
                "nombreFondeador", nombreFondeadorMostrar
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
        if (fecha != null) {
            return fecha.format(FORMATO_FECHA);
        }
        return SIN_DATO;
    }

    @Override
    public void notificarEndoso(Endoso endoso) {
        List<DestinatarioEmail> destinatarios =
                destinatarioResolver.resolverParaEndoso(endoso);

        boolean esConfirming = endoso.getTipoOperacion() != null
                && "CONFIRMING".equalsIgnoreCase(endoso.getTipoOperacion().getTipo());

        String tituloAccion;
        String tituloAccionCorta;
        String mensajeIntro;
        String textoBoton;
        if (esConfirming) {
            tituloAccion = "Solicitud de Descuento";
            tituloAccionCorta = "Descuento";
            mensajeIntro = "Se ha generado una solicitud de descuento que requiere tu aprobación.";
            textoBoton = "Aprobar Solicitud de Descuento";
        } else {
            tituloAccion = "Endoso";
            tituloAccionCorta = "Endoso";
            mensajeIntro = "Se ha generado un endoso que requiere tu aprobación.";
            textoBoton = "Aprobar Endoso";
        }

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
                "anio", String.valueOf(clock.now().getYear())
        );

        NotificacionEmailEvent event = NotificacionEmailEvent.builder()
                .idNegociacion(endoso.getNegociacionId())
                .destinatarios(destinatarios)
                .asunto("ENDOSO")
                .data(data)
                .tipoNotificacion(TipoNotificacion.ENDOSO_CREADO.name())
                .fecha(clock.now())
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
        String tipoOperacion = SIN_DATO;
        if (endoso.getTipoOperacion() != null) {
            tipoOperacion = endoso.getTipoOperacion().getTipo();
        }
        return String.join("|",
                tipoOperacion,
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

        String tituloAccion;
        String tituloAccionCorta;
        String mensajeIntro;
        String textoBoton;
        if (esConfirming) {
            tituloAccion = "Solicitud de Descuento";
            tituloAccionCorta = "Descuento";
            mensajeIntro = "Se han generado varias solicitudes de descuento que requieren tu aprobación.";
            textoBoton = "Aprobar Solicitudes de Descuento";
        } else {
            tituloAccion = "Endoso";
            tituloAccionCorta = "Endoso";
            mensajeIntro = "Se han generado varios endosos que requieren tu aprobación.";
            textoBoton = "Aprobar Endosos";
        }

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
                "anio", String.valueOf(clock.now().getYear())
        );

        NotificacionEmailEvent event = NotificacionEmailEvent.builder()
                .idNegociacion(representante.getNegociacionId())
                .destinatarios(destinatarios)
                .asunto("ENDOSO")
                .data(data)
                .tipoNotificacion(TipoNotificacion.ENDOSO_CREADO.name())
                .fecha(clock.now())
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

        if (valores.isEmpty()) {
            return null;
        }
        return valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
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

        String tituloAccion;
        String tituloAccionCorta;
        if (esConfirming) {
            tituloAccion = "Solicitud de Descuento";
            tituloAccionCorta = "Descuento";
        } else {
            tituloAccion = "Endoso";
            tituloAccionCorta = "Endoso";
        }

        List<AprobacionFacturaRequest> facturasRequest = Optional.ofNullable(aprobacion.getFacturas())
                .orElseGet(Collections::emptyList);

        boolean todasAprobadas = !facturasRequest.isEmpty()
                && facturasRequest.stream().allMatch(AprobacionFacturaRequest::isAprobado);

        String tituloResultado;
        if (esConfirming) {
            if (todasAprobadas) {
                tituloResultado = "Solicitud de Descuento Aprobada";
            } else {
                tituloResultado = "Solicitud de Descuento Rechazada";
            }
        } else {
            if (todasAprobadas) {
                tituloResultado = "Endoso Aprobado";
            } else {
                tituloResultado = "Endoso Rechazado";
            }
        }

        List<Map<String, Object>> facturas = facturasRequest.stream()
                .map(f -> {
                    String resultado;
                    if (f.isAprobado()) {
                        resultado = "Aprobada";
                    } else {
                        resultado = "Rechazada";
                    }
                    return safeMap("numeroFactura", f.getNumeroFactura(), "resultado", resultado);
                })
                .toList();

        String tipoOperacion = null;
        String claseOperacion = null;
        if (negociacion.getTipoOperacion() != null) {
            tipoOperacion = negociacion.getTipoOperacion().getTipo();
            claseOperacion = negociacion.getTipoOperacion().getClase();
        }

        Map<String, Object> data = safeMap(
                "id", negociacion.getId(),
                "tituloAccion", tituloAccion,
                "tituloAccionCorta", tituloAccionCorta,
                "tituloResultado", tituloResultado,
                "tipoOperacion", tipoOperacion,
                "claseOperacion", claseOperacion,
                "nombreActor", nombreCompania(aprobacion.getRutEmpresa()),
                "facturas", facturas,
                "anio", String.valueOf(clock.now().getYear())
        );

        NotificacionEmailEvent event = NotificacionEmailEvent.builder()
                .idNegociacion(negociacion.getId())
                .destinatarios(destinatarios)
                .asunto("FIRMA_ENDOSO")
                .data(data)
                .tipoNotificacion(TipoNotificacion.ENDOSO_FIRMADO.name())
                .fecha(clock.now())
                .build();

        outboxService.guardarEvento(negociacion.getId(), topicNotificacionEmail, event);
    }

    @Override
    public void notificarLiquidacionFallida(Negociacion negociacion, String numeroFactura, String motivo) {
        List<DestinatarioEmail> destinatarios =
                destinatarioResolver.resolverParaLiquidacionFallida(negociacion);

        String tipoOperacion = null;
        if (negociacion.getTipoOperacion() != null) {
            tipoOperacion = negociacion.getTipoOperacion().getTipo();
        }
        int cantidadFacturas = 0;
        if (negociacion.getFacturas() != null) {
            cantidadFacturas = negociacion.getFacturas().size();
        }

        // Se conservan las claves que ya se enviaban (id, tipoOperacion, rut*, facturas = cantidad) para no romper
        // a ningún consumidor; se agregan el motivo, la factura rechazada, los nombres en vez de solo los NIT y el
        // total formateado (antes iba el BigDecimal crudo, que podía salir con notación científica).
        Map<String, Object> data = safeMap(
                "id", negociacion.getId(),
                "tipoOperacion", tipoOperacion,
                "rutPagador", negociacion.getRutPagador(),
                "rutProveedor", negociacion.getRutProveedor(),
                "rutFondeadores", negociacion.getRutFondeadores(),
                "totalNegociado", formatearMonto(negociacion.getTotalNegociado()),
                "facturas", cantidadFacturas,
                "cantidadFacturas", cantidadFacturas,
                "motivo", motivo,
                "numeroFactura", numeroFactura,
                "valorFactura", formatearMonto(valorFactura(numeroFactura)),
                "nombrePagador", nombreCompania(negociacion.getRutPagador()),
                "nombreProveedor", nombreCompania(negociacion.getRutProveedor()),
                "nombresFondeadores", nombresCompanias(negociacion.getRutFondeadores())
        );

        NotificacionEmailEvent event = NotificacionEmailEvent.builder()
                .idEvento(UUID.randomUUID().toString())
                .idNegociacion(negociacion.getId())
                .tipoNotificacion(TipoNotificacion.LIQUIDACION_FALLIDA.name())
                .destinatarios(destinatarios)
                .asunto("LIQUIDACION_FALLIDA")
                .data(data)
                .fecha(clock.now())
                .build();

        outboxService.guardarEvento(negociacion.getId(), topicNotificacionEmail, event);
    }

    private String nombresCompanias(List<String> nits) {
        if (nits == null || nits.isEmpty()) {
            return SIN_DATO;
        }
        return nits.stream()
                .map(this::nombreCompania)
                .distinct()
                .collect(Collectors.joining(", "));
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
