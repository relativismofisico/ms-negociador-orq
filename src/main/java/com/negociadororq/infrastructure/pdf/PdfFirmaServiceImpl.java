package com.negociadororq.infrastructure.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import com.negociadororq.application.port.output.PdfFirmaOutputPort;
import com.negociadororq.domain.model.FirmaOtpMetadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

/**
 * Implementación de {@link PdfFirmaOutputPort}: estampa un bloque de texto con la metadata de
 * la firma OTP sobre la última página del PDF, usando PDFBox.
 */
@Slf4j
@Service
public class PdfFirmaServiceImpl implements PdfFirmaOutputPort {

    /** Altura de la ÚLTIMA línea de la primera firma (antes BASE_Y=100 con 9 líneas: la última quedaba en 20). */
    private static final float BASE_ULTIMA_LINEA = 20f;
    private static final float LINE_HEIGHT = 10f;
    private static final float MARGEN_ENTRE_FIRMAS = 15f;
    private static final float MARGEN_IZQUIERDO = 50f;
    private static final int TAMANO_FUENTE = 8;

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
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, TAMANO_FUENTE);

            List<String> lineas = construirLineasFirma(firma);

            // El bloque de una firma ocupa (lineas.size() * LINE_HEIGHT) de alto (cada
            // línea se dibuja y luego se desplaza hacia abajo esa distancia). El
            // espaciado entre firmas consecutivas DEBE ser al menos esa altura más un
            // margen; antes era una constante fija (60) menor que la altura real del
            // bloque (9 líneas * 10 = 90), así que firmas consecutivas se solapaban
            // aunque el índice fuera el correcto. Se calcula a partir de lineas.size()
            // para que nunca vuelva a desincronizarse si se agregan/quitan líneas.
            float x = MARGEN_IZQUIERDO;
            float alturaBloque = lineas.size() * LINE_HEIGHT + MARGEN_ENTRE_FIRMAS;
            // Fase 28c: la primera línea se ubica de modo que la última quede en BASE_ULTIMA_LINEA. Con 9 líneas
            // da 100, la misma posición de antes; con las 11 líneas actuales, la última no cae al borde de la página.
            float y = BASE_ULTIMA_LINEA + (lineas.size() - 1) * LINE_HEIGHT + (indexFirma * alturaBloque);

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

    /** Fase 28c: siempre las mismas líneas (con "-" si falta el dato), para que la altura del bloque no cambie. */
    private static String valorODato(Object valor) {
        if (valor == null) {
            return "-";
        }
        return String.valueOf(valor);
    }

    private List<String> construirLineasFirma(FirmaOtpMetadata firma) {
        String resultado;
        if (firma.isAprobado()) {
            resultado = "APROBADO";
        } else {
            resultado = "RECHAZADO";
        }

        String observacion = firma.getObservacion();
        if (observacion == null) {
            observacion = "";
        }

        return List.of(
                "===== FIRMA ELECTRONICA =====",
                "Actor: " + firma.getRolActor(),
                "Empresa: " + firma.getRutEmpresa(),
                "Operador: " + firma.getRutOperador(),
                "Resultado: " + resultado,
                "OTP: " + firma.getOtp(),
                "Referencia OTP: " + valorODato(firma.getOtpId()),
                "Validado el: " + valorODato(firma.getFechaValidacion()),
                "IP: " + firma.getIp(),
                "Fecha: " + firma.getFecha(),
                "Obs: " + observacion
        );
    }
}
