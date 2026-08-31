package com.negociadororq.application.port.output;

import java.util.Map;

/**
 * Puerto para renderizar una plantilla de texto (Mustache) con datos dinámicos.
 */
public interface TemplateEngineOutputPort {

    /**
     * @param templateName    nombre de la plantilla, usado solo para trazabilidad/logs
     * @param templateContent contenido crudo de la plantilla con sus placeholders
     * @param data             valores a interpolar en la plantilla
     * @return el texto renderizado
     */
    String render(String templateName, String templateContent, Map<String, Object> data);
}
