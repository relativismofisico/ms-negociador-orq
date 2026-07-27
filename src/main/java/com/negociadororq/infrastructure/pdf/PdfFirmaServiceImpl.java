package com.negociadororq.infrastructure.pdf;

import com.negociadororq.application.port.output.PdfFirmaOutputPort;
import com.negociadororq.domain.model.FirmaOtpMetadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Slf4j
@Service
public class PdfFirmaServiceImpl implements PdfFirmaOutputPort {

    private static final float BASE_Y = 100f;
    private static final float LINE_HEIGHT = 10f;
    private static final float MARGEN_ENTRE_FIRMAS = 15f;

    @Override
    public byte[] agregarFirma(byte[] pdfBytes, FirmaOtpMetadata firma, int indexFirma) {
        try (PDDocument document = PDDocument.load(pdfBytes)) {

            PDPage page = document.getPage(document.getNumberOfPages() - 1);

            PDPageContentStream contentStream = new PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true
            );

            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 8);

            List<String> lineas = construirLineasFirma(firma);

            // El bloque de una firma ocupa (lineas.size() * LINE_HEIGHT) de alto (cada
            // línea se dibuja y luego se desplaza hacia abajo esa distancia). El
            // espaciado entre firmas consecutivas DEBE ser al menos esa altura más un
            // margen; antes era una constante fija (60) menor que la altura real del
            // bloque (9 líneas * 10 = 90), así que firmas consecutivas se solapaban
            // aunque el índice fuera el correcto. Se calcula a partir de lineas.size()
            // para que nunca vuelva a desincronizarse si se agregan/quitan líneas.
            float x = 50;
            float alturaBloque = lineas.size() * LINE_HEIGHT + MARGEN_ENTRE_FIRMAS;
            float y = BASE_Y + (indexFirma * alturaBloque);

            contentStream.newLineAtOffset(x, y);

            for (String linea : lineas) {
                contentStream.showText(linea);
                contentStream.newLineAtOffset(0, -LINE_HEIGHT);
            }

            contentStream.endText();
            contentStream.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);

            return out.toByteArray();

        } catch (IOException e) {
            log.error("Error agregando firma al PDF", e);
            throw new RuntimeException("Error agregando firma al PDF", e);
        }
    }

    private List<String> construirLineasFirma(FirmaOtpMetadata firma) {
        return List.of(
                "===== FIRMA ELECTRONICA =====",
                "Actor: " + firma.getRolActor(),
                "Empresa: " + firma.getRutEmpresa(),
                "Operador: " + firma.getRutOperador(),
                "Resultado: " + (firma.isAprobado() ? "APROBADO" : "RECHAZADO"),
                "OTP: " + firma.getOtp(),
                "IP: " + firma.getIp(),
                "Fecha: " + firma.getFecha(),
                "Obs: " + (firma.getObservacion() != null ? firma.getObservacion() : "")
        );
    }
}
