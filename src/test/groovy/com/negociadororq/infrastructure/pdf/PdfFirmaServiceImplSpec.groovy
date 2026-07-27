package com.negociadororq.infrastructure.pdf

import com.negociadororq.domain.model.FirmaOtpMetadata
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import spock.lang.Specification

import java.time.LocalDateTime

class PdfFirmaServiceImplSpec extends Specification {

    PdfFirmaServiceImpl service = new PdfFirmaServiceImpl()

    private static byte[] pdfDeUnaPagina() {
        def out = new ByteArrayOutputStream()
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage())
            document.save(out)
        }
        return out.toByteArray()
    }

    private static FirmaOtpMetadata firma(String rol) {
        FirmaOtpMetadata.builder()
                .rutEmpresa("900111222")
                .rutOperador("11111111")
                .rolActor(rol)
                .otp("123456")
                .ip("127.0.0.1")
                .aprobado(true)
                .fecha(LocalDateTime.now())
                .observacion(null)
                .build()
    }

    /**
     * Extrae, para cada línea de texto del PDF, su coordenada Y (en el sistema
     * de PDFBox, donde Y crece hacia ARRIBA en la página, igual que en la
     * lógica de producción). Se usa para verificar que los bloques de dos
     * firmas consecutivas no se solapen verticalmente.
     */
    private static List<Float> extraerCoordenadasY(byte[] pdfBytes) {
        List<Float> ys = []
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            def stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> textPositions) throws java.io.IOException {
                    if (!textPositions.isEmpty()) {
                        // PDFTextStripper reporta Y "desde arriba"; se invierte a Y-desde-abajo
                        // (el mismo sistema que usa PDPageContentStream en producción) restando
                        // contra la altura de la página.
                        float pageHeight = document.getPage(0).getMediaBox().getHeight()
                        float yDesdeArriba = textPositions.get(0).getY()
                        ys.add(pageHeight - yDesdeArriba)
                    }
                    super.writeString(text, textPositions)
                }
            }
            stripper.setSortByPosition(true)
            stripper.getText(document)
        }
        return ys
    }

    def "agregarFirma con index=0 y index=1 no genera bloques de texto solapados verticalmente"() {
        given:
        byte[] pdfOriginal = pdfDeUnaPagina()

        when:
        byte[] conPrimeraFirma = service.agregarFirma(pdfOriginal, firma("PROVEEDOR"), 0)
        byte[] conSegundaFirma = service.agregarFirma(conPrimeraFirma, firma("FONDEADOR"), 1)

        List<Float> yPrimeraFirma = extraerCoordenadasY(conPrimeraFirma)
        List<Float> yAmbasFirmas = extraerCoordenadasY(conSegundaFirma)

        then:
        // La primera firma por sí sola ocupa 9 líneas de texto.
        yPrimeraFirma.size() == 9

        // Con las dos firmas debería haber 18 líneas en total (9 + 9).
        yAmbasFirmas.size() == 18

        // Las Y del bloque 1 (ya conocidas de la extracción anterior) deben
        // seguir estando todas presentes; el resto son las del bloque 2.
        List<Float> ysBloque2 = new ArrayList<>(yAmbasFirmas)
        yPrimeraFirma.each { ysBloque2.remove(it) }

        ysBloque2.size() == 9

        float maxBloque1 = yPrimeraFirma.max()
        float minBloque2 = ysBloque2.min()

        minBloque2 > maxBloque1
    }

    def "agregarFirma escribe las 9 líneas esperadas del bloque de firma"() {
        given:
        byte[] pdfOriginal = pdfDeUnaPagina()

        when:
        byte[] resultado = service.agregarFirma(pdfOriginal, firma("PAGADOR"), 0)

        String texto
        try (PDDocument document = PDDocument.load(resultado)) {
            texto = new PDFTextStripper().getText(document)
        }

        then:
        texto.contains("FIRMA ELECTRONICA")
        texto.contains("Actor: PAGADOR")
        texto.contains("Empresa: 900111222")
        texto.contains("OTP: 123456")
    }
}
