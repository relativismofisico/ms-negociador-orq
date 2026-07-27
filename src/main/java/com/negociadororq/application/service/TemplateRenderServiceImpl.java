package com.negociadororq.application.service;

import com.negociadororq.application.port.output.MessageTemplateOutputPort;
import com.negociadororq.application.port.output.TemplateEngineOutputPort;
import com.negociadororq.domain.model.MessageTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    private final MessageTemplateOutputPort messageTemplatePort;
    private final TemplateEngineOutputPort templateEnginePort;

    @Override
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
        data.put("claseOperacion", claseOperacion != null ? claseOperacion : "");
        data.put("nombreFondeador", nombreFondeador);
        data.put("rutFondeador", rutFondeador);
        data.put("fechaOperacion", fechaOperacion != null ? fechaOperacion.format(FORMATO_FECHA) : "");
        data.put("esGrupal", facturas.size() > 1);
        data.put("cantidadFacturas", facturas.size());

        // Título/verbo/referencia dependen únicamente de si es Factoring (Endoso)
        // o Confirming (Descuento) — se resuelven acá para no repetir el
        // condicional dentro de la plantilla.
        data.put("esFactoring", esFactoring);
        data.put("tituloDocumento", esFactoring ? "Endoso" : "Descuento");
        data.put("verboOperacion", esFactoring ? "ENDOSAR" : "DESCONTAR");

        data.put("nombreFirmante", firmante != null ? firmante.nombreCompleto() : "");
        data.put("tipoDocumentoFirmante", firmante != null ? firmante.tipoDocumento() : "");
        data.put("numeroDocumentoFirmante", firmante != null ? firmante.numeroDocumento() : "");

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

        return templateEnginePort.render(TEMPLATE_NAME, template.getContent(), data);
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
