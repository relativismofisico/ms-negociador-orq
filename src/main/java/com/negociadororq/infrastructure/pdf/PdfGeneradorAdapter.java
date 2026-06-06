package com.negociadororq.infrastructure.pdf;

import com.negociadororq.application.port.output.PdfGeneradorOutputPort;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Slf4j
@Service
public class PdfGeneradorAdapter implements PdfGeneradorOutputPort {

    @Override
    public byte[] generarDesdePdfHtml(String html) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            log.error("Error generando PDF de endoso", e);
            throw new RuntimeException("Error generando PDF de endoso", e);
        }
    }
}
