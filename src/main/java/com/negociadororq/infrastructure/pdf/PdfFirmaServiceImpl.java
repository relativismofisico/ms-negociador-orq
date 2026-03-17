package com.negociadororq.infrastructure.pdf;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@Service
public class PdfFirmaServiceImpl implements PdfFirmaService {

    @Override
    public byte[] agregarFirma(byte[] pdfBytes, String textoFirma, int indexFirma) {

        try (PDDocument document = PDDocument.load(pdfBytes)) {

            PDPage page = document.getPage(document.getNumberOfPages() - 1);

            PDPageContentStream contentStream = new PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true
            );

            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 10);

            // 📍 Posición dinámica (cada firma baja)
            float x = 50;
            float y = 100 + (indexFirma * 20);

            contentStream.newLineAtOffset(x, y);

            contentStream.showText(textoFirma);

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
}
