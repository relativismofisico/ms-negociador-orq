package com.negociadororq.infrastructure.template;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.negociadororq.application.port.output.TemplateEngineOutputPort;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

@Service
public class MustacheTemplateEngineAdapter implements TemplateEngineOutputPort {

    private static final MustacheFactory MUSTACHE_FACTORY = new DefaultMustacheFactory();

    @Override
    public String render(String templateName, String templateContent, Map<String, Object> data) {
        Mustache mustache = MUSTACHE_FACTORY.compile(new StringReader(templateContent), templateName);
        StringWriter writer = new StringWriter();
        mustache.execute(writer, data);
        return writer.toString();
    }
}
