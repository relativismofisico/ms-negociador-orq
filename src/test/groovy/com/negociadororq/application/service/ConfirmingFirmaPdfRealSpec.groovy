package com.negociadororq.application.service

import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.CompanyOutputPort
import com.negociadororq.application.port.output.EndosoOutputPort
import com.negociadororq.application.port.output.EndosoPatchOutputPort
import com.negociadororq.application.port.output.FacturaOutputPort
import com.negociadororq.application.port.output.FacturaPatchOutputPort
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort
import com.negociadororq.application.port.output.NegociacionOutputPort
import com.negociadororq.application.port.output.PendingOtpOutputPort
import com.negociadororq.application.port.output.PersonOutputPort
import com.negociadororq.application.port.output.UserOutputPort
import com.negociadororq.domain.enums.EstadoFirma
import com.negociadororq.domain.enums.RolActor
import com.negociadororq.domain.model.AprobacionActorDto
import com.negociadororq.domain.model.AprobacionFacturaRequest
import com.negociadororq.domain.model.AprobacionRequest
import com.negociadororq.domain.model.Endoso
import com.negociadororq.domain.model.Factura
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.FondeadorAsignacion
import com.negociadororq.domain.model.Negociacion
import com.negociadororq.domain.model.Operacion
import com.negociadororq.domain.model.PendingOtp
import com.negociadororq.infrastructure.pdf.PdfFirmaServiceImpl
import com.negociadororq.infrastructure.pdf.PdfGeneradorAdapter
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDateTime

/**
 * Fase 28c: Confirming con PDF REAL (HTML -> PDF con openhtmltopdf y estampado con PDFBox, sin mocks del PDF).
 * Camino del Proveedor: aprobarInterno -> regenerarDocumentoConFirmante -> generarDocumentoFormal -> firmarPdfEndoso.
 * Si el endoso ya tiene una firma de otro actor, regenerar el documento la borraría: la firma previa debe seguir.
 */
class ConfirmingFirmaPdfRealSpec extends Specification {

    static final String NEG = "neg-conf-1"
    static final String FAC = "FC-CONF-1"
    static final String PROV = "PROV-1"
    static final String PAG = "PAG-1"
    static final String FOND = "FOND-1"

    ClockPort clock = Stub() {
        now() >> LocalDateTime.of(2026, 11, 15, 10, 0, 5)
    }
    Endoso endoso
    EndosoOutputPort endosoPort = Stub() {
        findByNegociacionIdAndNumeroFactura(_, _) >> { Optional.ofNullable(endoso) }
        save(_) >> { Endoso e -> endoso = e; e }
    }
    int renders = 0
    TemplateRenderService plantilla = Stub() {
        renderEndosoFormalTemplate(*_) >> {
            renders++
            "<html><body><h1>ENDOSO EN CONFIRMING</h1><p>Factura " + FAC + "</p><p>Documento formal del endoso.</p></body></html>"
        }
    }
    UserOutputPort userPort = Stub() { findByUsername(_) >> Optional.empty() }
    PersonOutputPort personPort = Stub()
    DocumentoEndosoServiceImpl documento = new DocumentoEndosoServiceImpl(plantilla, new PdfGeneradorAdapter(), userPort,
            personPort, clock)
    FirmaEndosoServiceImpl firma = new FirmaEndosoServiceImpl(endosoPort, new PdfFirmaServiceImpl(), clock,
            Mock(AuditoriaNegociacionService))

    Factura factura = new Factura(numeroFactura: FAC, rutPagador: PAG, nombrePagador: "Pagador Uno", rutProveedor: PROV,
            nombreProveedor: "Proveedor Uno", valorFactura: 1000000G)
    FacturaNegociada facturaNeg = FacturaNegociada.builder()
            .numeroFactura(FAC).rutPagador(PAG).rutProveedor(PROV)
            .fondeadores([FondeadorAsignacion.builder().rutFondeador(FOND).nombreFondeador("Fondo Uno").build()])
            .aprobaciones([]).estadoFirma(EstadoFirma.EN_REVISION).build()
    Negociacion negociacion = Negociacion.builder().id(NEG).tipoOperacion(new Operacion("CONFIRMING", "NORMAL"))
            .facturas([facturaNeg]).build()

    NegociacionOutputPort negociacionPort = Stub() {
        findById(NEG) >> Optional.of(negociacion)
        save(_) >> { Negociacion n -> n }
    }
    FacturaOutputPort facturaPort = Stub() { findByNumeroFactura(FAC) >> Optional.of(factura) }

    AprobacionEndosoDescuentoServiceImpl service = new AprobacionEndosoDescuentoServiceImpl(
            negociacionPort, Mock(FacturaPatchOutputPort), Mock(EndosoPatchOutputPort), Mock(OutboxService),
            Mock(CupoReversionService), Mock(CompanyOutputPort), Mock(FondeadorQuotaOutputPort), facturaPort,
            userPort, Mock(PendingOtpOutputPort), firma, Mock(NotificacionService), endosoPort,
            documento, clock,
            "topic-otp-request", "topic-otp-validate", "topic-liquidacion", Mock(AuditoriaNegociacionService))

    PendingOtp pending(String rut, String rol, String otp, String otpId, String ip, String operador) {
        def req = new AprobacionRequest(NEG, operador, rut, rol, [new AprobacionFacturaRequest(FAC, true, null)], "ok " + rol)
        PendingOtp.builder().id("p-" + otpId).idNegociacion(NEG).rutEmpresa(rut).rolActor(rol).otp(otp).otpId(otpId)
                .ip(ip).fechaValidacion(Instant.parse("2026-11-15T14:59:58Z")).request(req).requestId("r-" + otpId).build()
    }

    /** Documento formal inicial y la firma del Fondeador ya estampada (como la deja su aprobación). */
    void documentoConFirmaDelFondeador() {
        endoso = Endoso.builder().id("end-1").negociacionId(NEG).numerosFactura([FAC]).aprobaciones([]).firmasOtp([]).build()
        documento.generarDocumentoFormal(endoso, negociacion, [factura], [facturaNeg], "op-pag")
        firma.firmarPdfEndoso(NEG, facturaNeg, pending(FOND, "FONDEADOR", "111111", "70", "10.0.0.7", "op-fond"), FOND,
                "op-fond", RolActor.FONDEADOR, true, "ok FONDEADOR")
        // actualizarAprobacionEndoso (patch en Mongo) deja la aprobación en el endoso y en la factura
        def ap = new AprobacionActorDto(rutActor: FOND, rolActor: RolActor.FONDEADOR, aprobado: true,
                observacion: "ok FONDEADOR", fecha: LocalDateTime.of(2026, 11, 15, 9, 0), otpId: "70")
        endoso.aprobaciones = [ap]
        facturaNeg.aprobaciones = [ap]
    }

    static List<String> lineas(byte[] pdf) {
        PDDocument.load(pdf).withCloseable { d ->
            new PDFTextStripper().getText(d).readLines()*.trim().findAll { it }
        }
    }

    static List<List<String>> bloques(byte[] pdf) {
        List<String> l = lineas(pdf)
        List<List<String>> salida = []
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i) == "===== FIRMA ELECTRONICA =====") {
                salida.add(new ArrayList<String>(l.subList(i, Math.min(l.size(), i + 11))))
            }
        }
        salida
    }

    /** Caracteres fuera del área de la página (x o y fuera de la caja). */
    static int fueraDePagina(byte[] pdf) {
        int fuera = 0
        PDDocument.load(pdf).withCloseable { d ->
            def s = new PDFTextStripper() {
                @Override
                protected void writeString(String texto, List<TextPosition> posiciones) {
                    def caja = getCurrentPage().getMediaBox()
                    posiciones.each { p ->
                        if (p.XDirAdj < 0 || p.XDirAdj + p.widthDirAdj > caja.width || p.YDirAdj - p.heightDir < 0
                                || p.YDirAdj > caja.height) {
                            fuera++
                        }
                    }
                    super.writeString(texto, posiciones)
                }
            }
            s.getText(d)
        }
        fuera
    }

    def "Confirming: la firma previa (otro actor) sigue en el PDF real cuando el Proveedor aprueba, y un reintento no añade un tercer bloque"() {
        given:
        documentoConFirmaDelFondeador()
        def antes = bloques(endoso.documentoPdf)
        assert antes.size() == 1

        when: "el Proveedor aprueba (camino de regeneración del documento con su firmante) y se estampa su firma"
        service.aprobarInterno(pending(PROV, "PROVEEDOR", "222222", "80", "10.0.0.8", "op-prov"))
        def b = bloques(endoso.documentoPdf)
        def fondeador = b.find { it.contains("Actor: FONDEADOR") }
        def proveedor = b.find { it.contains("Actor: PROVEEDOR") }

        then: "(1) la firma previa sigue con su OTP, referencia, fechas e IP"
        fondeador != null
        fondeador == antes[0]
        fondeador.containsAll(["OTP: 111111", "Referencia OTP: 70", "Validado el: 15/11/2026 09:59:58 (hora Colombia)",
                               "IP: 10.0.0.7"])
        // "Fecha" sale del reloj local de la JVM convertido a hora Colombia: se comprueba el formato, no la hora
        fondeador.any { it ==~ /Fecha: \d{2}\/\d{2}\/\d{4} \d{2}:\d{2}:\d{2} \(hora Colombia\)/ }

        and: "(2) la firma nueva está presente"
        proveedor != null
        proveedor.containsAll(["OTP: 222222", "Referencia OTP: 80", "IP: 10.0.0.8", "Empresa: " + PROV])

        and: "(3) exactamente 2 bloques de 11 líneas"
        b.size() == 2
        b.every { it.size() == 11 && it[0] == "===== FIRMA ELECTRONICA =====" && it[10].startsWith("Obs:") }

        and: "(5) ninguna línea cae fuera de la página"
        fueraDePagina(endoso.documentoPdf) == 0

        when: "(4) reintento del mismo otpId"
        service.aprobarInterno(pending(PROV, "PROVEEDOR", "222222", "80", "10.0.0.8", "op-prov"))

        then:
        bloques(endoso.documentoPdf).size() == 2
        endoso.firmasOtp.count { it == "80:" + FAC } == 1
    }

    def "Confirming sin firmas previas: el Proveedor regenera el documento con su firmante y queda un solo bloque"() {
        given:
        endoso = Endoso.builder().id("end-2").negociacionId(NEG).numerosFactura([FAC]).aprobaciones([]).firmasOtp([]).build()
        documento.generarDocumentoFormal(endoso, negociacion, [factura], [facturaNeg], "op-pag")
        def rendersAntes = renders

        when:
        service.aprobarInterno(pending(PROV, "PROVEEDOR", "222222", "81", "10.0.0.8", "op-prov"))

        then:
        renders == rendersAntes + 1
        bloques(endoso.documentoPdf).size() == 1
        bloques(endoso.documentoPdf)[0].contains("Actor: PROVEEDOR")
        fueraDePagina(endoso.documentoPdf) == 0
    }
}
