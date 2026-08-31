package com.negociadororq.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.negociadororq.application.port.output.MessageTemplateOutputPort;
import com.negociadororq.application.port.output.TemplateEngineOutputPort;
import com.negociadororq.domain.model.MessageTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


/**
 * Implementación de {@link TemplateRenderService}.
 */
@Service
@RequiredArgsConstructor
public class TemplateRenderServiceImpl implements TemplateRenderService {

    // Plantilla almacenada en messages_templates (ver script de configuración),
    // no en el código: cambiar el texto legal ya no requiere un deploy.
    private static final String TEMPLATE_NAME = "ENDOSO_DOCUMENTO";
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // La cuenta bancaria del fondeador todavía no se captura en ningún lugar
    // del sistema: se deja parametrizada en la plantilla con este placeholder
    // hasta que exista esa funcionalidad.
    private static final String CUENTA_NO_REGISTRADA = "No registrada";

    // Exclusiva de Factoring CON_RESPONSABILIDAD/SIN_RESPONSABILIDAD. Cualquier
    // otra clase de Factoring (ANTICIPO, SIN_DESCUENTO) queda sin cláusula,
    // igual que antes de este cambio.
    private static final String CLASE_FACTORING_CON_RECURSO = "CON_RESPONSABILIDAD";
    private static final String CLASE_FACTORING_SIN_RECURSO = "SIN_RESPONSABILIDAD";

    // %1$s = numerosFacturas, %2$s = nombreFondeador. Se resuelven acá (no vía
    // {{mustache}} anidado) porque el motor de plantillas hace una sola pasada:
    // un tag mustache dentro de un valor ya sustituido queda como texto literal.
    private static final String CLAUSULA_CON_RECURSO = """
            El proveedor responde solidariamente por el pago de la(s) factura(s) %1$s \
            objeto de este descuento. En caso de que el pagador no cumpla con el pago al vencimiento, \
            %2$s podrá exigir el reembolso del valor descontado directamente al \
            proveedor, quien conserva la obligación cambiaria de regreso conforme al artículo 785 del \
            Código de Comercio.""";

    private static final String CLAUSULA_SIN_RECURSO = """
            El presente descuento se realiza SIN RESPONSABILIDAD para el proveedor. El riesgo de \
            impago del pagador es asumido íntegramente por %2$s, sin que pueda \
            ejercer acción de reembolso ni acción cambiaria de regreso contra el proveedor, conforme \
            a la cláusula "sin mi responsabilidad" del artículo 657 del Código de Comercio.""";

    private final MessageTemplateOutputPort messageTemplatePort;
    private final TemplateEngineOutputPort templateEnginePort;

    @Override
    @SuppressWarnings({"checkstyle:ParameterNumber", "PMD.ExcessiveParameterList"})
    public String renderEndosoFormalTemplate(
            String nombrePagador,
            String rutPagador,
            String nombreProveedor,
            String rutProveedor,
            List<FacturaEndosoTemplateData> facturas,
            String tipoOperacion,
            String claseOperacion,
            String nombreFondeador,
            String rutFondeador,
            LocalDateTime fechaOperacion,
            DatosFirmante firmante
    ) {
        MessageTemplate template = messageTemplatePort.findByTemplateName(TEMPLATE_NAME)
                .filter(MessageTemplate::isActive)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe una plantilla activa '" + TEMPLATE_NAME + "' en messages_templates"));

        boolean esFactoring = "FACTORING".equalsIgnoreCase(tipoOperacion);

        Map<String, Object> data = new HashMap<>();
        data.put("nombrePagador", nombrePagador);
        data.put("rutPagador", rutPagador);
        data.put("nombreProveedor", nombreProveedor);
        data.put("rutProveedor", rutProveedor);
        data.put("tipoOperacion", tipoOperacion);
        String claseOperacionTexto;
        if (claseOperacion != null) {
            claseOperacionTexto = claseOperacion;
        } else {
            claseOperacionTexto = "";
        }
        data.put("claseOperacion", claseOperacionTexto);
        data.put("nombreFondeador", nombreFondeador);
        data.put("rutFondeador", rutFondeador);

        String fechaOperacionTexto;
        if (fechaOperacion != null) {
            fechaOperacionTexto = fechaOperacion.format(FORMATO_FECHA);
        } else {
            fechaOperacionTexto = "";
        }
        data.put("fechaOperacion", fechaOperacionTexto);
        data.put("esGrupal", facturas.size() > 1);
        data.put("cantidadFacturas", facturas.size());

        // Título/verbo/referencia dependen únicamente de si es Factoring (Endoso)
        // o Confirming (Descuento) — se resuelven acá para no repetir el
        // condicional dentro de la plantilla.
        data.put("esFactoring", esFactoring);
        if (esFactoring) {
            data.put("tituloDocumento", "Endoso");
            data.put("verboOperacion", "ENDOSAR");
        } else {
            data.put("tituloDocumento", "Descuento");
            data.put("verboOperacion", "DESCONTAR");
        }

        if (firmante != null) {
            data.put("nombreFirmante", firmante.nombreCompleto());
            data.put("tipoDocumentoFirmante", firmante.tipoDocumento());
            data.put("numeroDocumentoFirmante", firmante.numeroDocumento());
        } else {
            data.put("nombreFirmante", "");
            data.put("tipoDocumentoFirmante", "");
            data.put("numeroDocumentoFirmante", "");
        }

        data.put("tipoCuentaFondeador", CUENTA_NO_REGISTRADA);
        data.put("numeroCuentaFondeador", CUENTA_NO_REGISTRADA);

        List<Map<String, Object>> facturasData = facturas.stream()
                .map(f -> {
                    Map<String, Object> fila = new HashMap<>();
                    fila.put("numeroFactura", f.numeroFactura());
                    fila.put("valorFactura", formatearMonto(f.valorFactura()));
                    return fila;
                })
                .toList();
        data.put("facturas", facturasData);

        String numerosFacturas = facturas.stream()
                .map(FacturaEndosoTemplateData::numeroFactura)
                .collect(Collectors.joining(", "));
        data.put("numerosFacturas", numerosFacturas);
        data.put("clausulaResponsabilidad",
                resolverClausulaResponsabilidad(esFactoring, claseOperacion, numerosFacturas, nombreFondeador));

        return templateEnginePort.render(TEMPLATE_NAME, template.getContent(), data);
    }

    // Cláusula exclusiva de Factoring CON_RESPONSABILIDAD/SIN_RESPONSABILIDAD.
    // Cualquier otro caso (Confirming, o Factoring ANTICIPO/SIN_DESCUENTO) queda
    // sin cláusula — se preserva el comportamiento previo a este cambio.
    private String resolverClausulaResponsabilidad(
            boolean esFactoring, String claseOperacion, String numerosFacturas, String nombreFondeador) {

        if (esFactoring && CLASE_FACTORING_SIN_RECURSO.equalsIgnoreCase(claseOperacion)) {
            return String.format(CLAUSULA_SIN_RECURSO, numerosFacturas, nombreFondeador);
        }
        if (esFactoring && CLASE_FACTORING_CON_RECURSO.equalsIgnoreCase(claseOperacion)) {
            return String.format(CLAUSULA_CON_RECURSO, numerosFacturas, nombreFondeador);
        }
        return "";
    }

    private String formatearMonto(BigDecimal monto) {
        if (monto == null) {
            return "N/A";
        }
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols(new Locale("es", "CO"));
        DecimalFormat formato = new DecimalFormat("#,##0.00", simbolos);
        return formato.format(monto.setScale(2, RoundingMode.HALF_UP));
    }
}
