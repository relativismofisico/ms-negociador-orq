package com.negociadororq.infrastructure.pdf

import com.negociadororq.domain.model.FirmaOtpMetadata
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import spock.lang.Specification

import java.time.Instant

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
                .fecha(Instant.now())
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
        // La primera firma por sí sola ocupa 11 líneas de texto (Fase 28c: + Referencia OTP y Validado el).
        yPrimeraFirma.size() == 11
        // la última línea no cae al borde de la página
        yPrimeraFirma.min() >= 20f

        // Con las dos firmas debería haber 22 líneas en total (11 + 11).
        yAmbasFirmas.size() == 22

        // Las Y del bloque 1 (ya conocidas de la extracción anterior) deben
        // seguir estando todas presentes; el resto son las del bloque 2.
        List<Float> ysBloque2 = new ArrayList<>(yAmbasFirmas)
        yPrimeraFirma.each { ysBloque2.remove(it) }

        ysBloque2.size() == 11

        float maxBloque1 = yPrimeraFirma.max()
        float minBloque2 = ysBloque2.min()

        minBloque2 > maxBloque1
    }

    def "agregarFirma escribe las líneas esperadas del bloque de firma"() {
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

    def "Fase 28c: el bloque lleva el OTP completo, la referencia, la fecha de validación y la IP (11 líneas siempre)"() {
        given:
        def conReferencia = FirmaOtpMetadata.builder().rutEmpresa("900111222").rutOperador("11111111")
                .rolActor("PAGADOR").otp("123456").ip("127.0.0.1").aprobado(true).fecha(Instant.now())
                .otpId("77").fechaValidacion(Instant.parse("2026-11-13T15:00:00Z")).build()

        when:
        def texto = textoDe(service.agregarFirma(pdfDeUnaPagina(), conReferencia, 0))
        def anterior = textoDe(service.agregarFirma(pdfDeUnaPagina(), firma("PAGADOR"), 0))

        then:
        texto.contains("OTP: 123456")
        texto.contains("Referencia OTP: 77")
        texto.contains("Validado el: 13/11/2026 10:00:00 (hora Colombia)")
        texto.contains("IP: 127.0.0.1")
        anterior.contains("Referencia OTP: -")
        anterior.contains("Validado el: -")
        lineasDeFirma(texto) == 11
        lineasDeFirma(anterior) == 11
    }

    private static String textoDe(byte[] pdf) {
        try (PDDocument document = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(document)
        }
    }

    private static int lineasDeFirma(String texto) {
        texto.readLines().findAll { it.trim() }.size()
    }

    def "Fase 28c: las 11 líneas exactas, con las fechas en hora Colombia sea cual sea la zona de la JVM (#zona)"() {
        given:
        def original = TimeZone.getDefault()
        def firma = FirmaOtpMetadata.builder().rutEmpresa("900111222").rutOperador("11111111")
                .rolActor("PAGADOR").otp("123456").ip("127.0.0.1").aprobado(true)
                .fecha(Instant.parse("2026-11-13T15:00:05Z"))
                .otpId("77").fechaValidacion(Instant.parse("2026-11-13T15:00:00Z"))
                .observacion("Sin observaciones").build()

        when:
        TimeZone.setDefault(TimeZone.getTimeZone(zona))
        List<String> lineas
        try {
            lineas = textoDe(service.agregarFirma(pdfDeUnaPagina(), firma, 0)).readLines()*.trim().findAll { it }
        } finally {
            TimeZone.setDefault(original)
        }

        then:
        lineas == [
                "===== FIRMA ELECTRONICA =====",
                "Actor: PAGADOR",
                "Empresa: 900111222",
                "Operador: 11111111",
                "Resultado: APROBADO",
                "OTP: 123456",
                "Referencia OTP: 77",
                "Validado el: 13/11/2026 10:00:00 (hora Colombia)",
                "IP: 127.0.0.1",
                "Fecha: 13/11/2026 10:00:05 (hora Colombia)",
                "Obs: Sin observaciones"]

        where:
        zona << ["UTC", "America/Bogota", "Asia/Tokyo", "Pacific/Kiritimati", "America/Los_Angeles"]
    }

    def "Fase 28c: fecha ausente se imprime como -"() {
        expect:
        PdfFirmaServiceImpl.fechaColombia(null) == "-"
        PdfFirmaServiceImpl.fechaColombia(Instant.parse("2026-01-01T04:59:59Z")) == "31/12/2025 23:59:59 (hora Colombia)"
    }
}
