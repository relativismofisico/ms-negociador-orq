package com.negociadororq.application.port.output;

import java.util.Map;

public interface TemplateEngineOutputPort {
    String render(String templateName, String templateContent, Map<String, Object> data);
}
