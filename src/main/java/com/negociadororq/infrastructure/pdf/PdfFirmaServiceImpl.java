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

            float x = 50;
            float y = 100 + (indexFirma * 60);

            contentStream.newLineAtOffset(x, y);

            List<String> lineas = construirLineasFirma(firma);

            for (String linea : lineas) {
                contentStream.showText(linea);
                contentStream.newLineAtOffset(0, -10);
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
