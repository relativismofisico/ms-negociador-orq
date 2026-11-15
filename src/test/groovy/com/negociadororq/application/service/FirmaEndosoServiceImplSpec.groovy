package com.negociadororq.application.service

import com.negociadororq.application.port.output.ClockPort
import com.negociadororq.application.port.output.EndosoOutputPort
import com.negociadororq.application.port.output.PdfFirmaOutputPort
import com.negociadororq.domain.enums.RolActor
import com.negociadororq.domain.model.AprobacionActorDto
import com.negociadororq.domain.model.Endoso
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.PendingOtp
import spock.lang.Specification

import java.time.LocalDateTime

class FirmaEndosoServiceImplSpec extends Specification {

    EndosoOutputPort endosoPort = Mock()
    PdfFirmaOutputPort pdfFirmaPort = Mock()
    ClockPort clock = Mock() {
        now() >> LocalDateTime.now()
    }
    AuditoriaNegociacionService auditoriaNegociacion = Mock()

    FirmaEndosoServiceImpl service = new FirmaEndosoServiceImpl(endosoPort, pdfFirmaPort, clock, auditoriaNegociacion)

    def "firmarPdfEndoso actualiza documentoPdf del endoso y guarda"() {
        given:
        def pdfOriginal = "PDF-ORIGINAL".bytes
        def pdfFirmado = "PDF-FIRMADO".bytes

        def endoso = Endoso.builder()
                .id("endoso-001")
                .negociacionId("neg-001")
                .numerosFactura(["FECI001"])
                .documentoPdf(pdfOriginal)
                .build()

        def factura = FacturaNegociada.builder()
                .numeroFactura("FECI001")
                .aprobaciones([])
                .build()

        def pending = PendingOtp.builder()
                .otp("123456")
                .ip("192.168.1.1")
                .build()

        endosoPort.findByNegociacionIdAndNumeroFactura("neg-001", "FECI001") >> Optional.of(endoso)
        pdfFirmaPort.agregarFirma(pdfOriginal, _, 0) >> pdfFirmado

        when:
        service.firmarPdfEndoso("neg-001", factura, pending, "RUT-EMP", "RUT-OP",
                RolActor.PAGADOR, true, "Sin observaciones")

        then:
        endoso.documentoPdf == pdfFirmado
        1 * endosoPort.save(endoso)
    }

    def "firmarPdfEndoso lanza RuntimeException cuando endoso no existe"() {
        given:
        def factura = FacturaNegociada.builder()
                .numeroFactura("NO-EXISTE")
                .aprobaciones([])
                .build()

        def pending = PendingOtp.builder().otp("999").ip("10.0.0.1").build()

        endosoPort.findByNegociacionIdAndNumeroFactura("neg-001", "NO-EXISTE") >> Optional.empty()

        when:
        service.firmarPdfEndoso("neg-001", factura, pending, "RUT", "OP",
                RolActor.FONDEADOR, false, null)

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Endoso no encontrado")
    }

    def "firmarPdfEndoso usa el index de aprobaciones existentes DEL ENDOSO, no de la factura"() {
        given:
        def pdfOriginal = "PDF".bytes
        def pdfFirmado = "PDF-SIGNED".bytes
        def aprobacion = new AprobacionActorDto()
        def endoso = Endoso.builder()
                .documentoPdf(pdfOriginal)
                .aprobaciones([aprobacion, aprobacion]) // size=2: ya hay 2 firmas estampadas en ESTE documento
                .build()

        def factura = FacturaNegociada.builder()
                .numeroFactura("F001")
                .aprobaciones([]) // la factura, por su cuenta, no tiene ninguna aprobación propia todavía
                .build()

        def pending = PendingOtp.builder().otp("456").ip("1.2.3.4").build()

        endosoPort.findByNegociacionIdAndNumeroFactura("neg-X", "F001") >> Optional.of(endoso)

        when:
        service.firmarPdfEndoso("neg-X", factura, pending, "RUT", "OP",
                RolActor.PROVEEDOR, true, "OK")

        then:
        1 * pdfFirmaPort.agregarFirma(pdfOriginal, { it.rutEmpresa == "RUT" && it.otp == "456" }, 2) >> pdfFirmado
        endoso.documentoPdf == pdfFirmado
    }

    def "firmarPdfEndoso con endoso grupal no reinicia el index en 0 para la segunda factura (evita que las firmas se superpongan)"() {
        // Regresión del bug reportado: con endosoGrupal=true, dos facturas distintas
        // pueden compartir el mismo Endoso/documentoPdf. Antes del fix, el index salía
        // de factura.getAprobaciones().size() (propio de cada factura, siempre en 0 la
        // primera vez), así que la segunda factura volvía a estampar en el mismo lugar
        // que la primera, tapándola. Acá simulamos justamente ese escenario: la factura
        // en curso no tiene aprobaciones propias (es la primera vez que ESTA factura se
        // firma), pero el Endoso compartido ya tiene una aprobación de otra factura del
        // mismo grupo.
        given:
        def pdfConUnaFirma = "PDF-CON-1-FIRMA".bytes
        def pdfConDosFirmas = "PDF-CON-2-FIRMAS".bytes
        def aprobacionDeOtraFacturaDelGrupo = new AprobacionActorDto()
        def endosoGrupal = Endoso.builder()
                .numerosFactura(["F001", "F002"])
                .documentoPdf(pdfConUnaFirma)
                .aprobaciones([aprobacionDeOtraFacturaDelGrupo])
                .build()

        def facturaSinAprobacionesPropias = FacturaNegociada.builder()
                .numeroFactura("F002")
                .aprobaciones([])
                .build()

        def pending = PendingOtp.builder().otp("789").ip("1.1.1.1").build()

        endosoPort.findByNegociacionIdAndNumeroFactura("neg-grupal", "F002") >> Optional.of(endosoGrupal)

        when:
        service.firmarPdfEndoso("neg-grupal", facturaSinAprobacionesPropias, pending, "RUT-2", "OP-2",
                RolActor.FONDEADOR, true, "OK")

        then:
        1 * pdfFirmaPort.agregarFirma(pdfConUnaFirma, _, 1) >> pdfConDosFirmas
        endosoGrupal.documentoPdf == pdfConDosFirmas
    }

    def "firmarPdfEndoso construye FirmaOtpMetadata con todos los campos"() {
        given:
        def endoso = Endoso.builder().documentoPdf("P".bytes).build()
        def factura = FacturaNegociada.builder()
                .numeroFactura("FACT-A")
                .aprobaciones([])
                .build()
        def pending = PendingOtp.builder().otp("111222").ip("10.20.30.40").build()

        endosoPort.findByNegociacionIdAndNumeroFactura("N1", "FACT-A") >> Optional.of(endoso)
        pdfFirmaPort.agregarFirma(_, _, _) >> "SIGNED".bytes

        when:
        service.firmarPdfEndoso("N1", factura, pending, "EMP-001", "OP-002",
                RolActor.FONDEADOR, false, "Rechazado")

        then:
        1 * pdfFirmaPort.agregarFirma(_, {
            it.rutEmpresa == "EMP-001" &&
            it.rutOperador == "OP-002" &&
            it.rolActor == "FONDEADOR" &&
            it.otp == "111222" &&
            it.ip == "10.20.30.40" &&
            it.aprobado == false &&
            it.observacion == "Rechazado"
        }, 0) >> "SIGNED".bytes
    }

    def "Fase 28c: la firma lleva otpId y fecha de validación, y deja la marca otpId:factura en el mismo guardado"() {
        given:
        def endoso = Endoso.builder().id("e-1").negociacionId("neg-1").numerosFactura(["F1"])
                .documentoPdf("PDF".bytes).build()
        def factura = FacturaNegociada.builder().numeroFactura("F1").aprobaciones([]).build()
        def validado = java.time.Instant.parse("2026-11-13T15:00:00Z")
        def pending = PendingOtp.builder().otp("123456").ip("1.1.1.1").otpId("77").fechaValidacion(validado).build()
        endosoPort.findByNegociacionIdAndNumeroFactura("neg-1", "F1") >> Optional.of(endoso)

        when:
        service.firmarPdfEndoso("neg-1", factura, pending, "EMP", "OP", RolActor.PAGADOR, true, null)

        then:
        1 * pdfFirmaPort.agregarFirma(_, { it.otpId == "77" && it.fechaValidacion == validado && it.otp == "123456" && it.fecha != null }, 0) >>
                "PDF-FIRMADO".bytes
        1 * endosoPort.save({ Endoso e -> e.firmasOtp == ["77:F1"] && new String(e.documentoPdf) == "PDF-FIRMADO" })
    }

    def "Fase 28c: reintento de la misma validación sobre la misma factura: no se estampa dos veces"() {
        given:
        def endoso = Endoso.builder().id("e-1").negociacionId("neg-1").numerosFactura(["F1", "F2"])
                .documentoPdf("PDF-FIRMADO".bytes).firmasOtp(["77:F1"]).build()
        def pending = PendingOtp.builder().otp("123456").otpId("77").build()
        endosoPort.findByNegociacionIdAndNumeroFactura("neg-1", _) >> Optional.of(endoso)

        when:
        service.firmarPdfEndoso("neg-1", FacturaNegociada.builder().numeroFactura("F1").build(), pending, "EMP", "OP",
                RolActor.PAGADOR, true, null)

        then:
        0 * pdfFirmaPort.agregarFirma(*_)
        0 * endosoPort.save(_)

        when: "otra factura del mismo endoso grupal sí se firma"
        service.firmarPdfEndoso("neg-1", FacturaNegociada.builder().numeroFactura("F2").build(), pending, "EMP", "OP",
                RolActor.PAGADOR, true, null)

        then:
        1 * pdfFirmaPort.agregarFirma(*_) >> "PDF-2".bytes
        1 * endosoPort.save({ Endoso e -> e.firmasOtp == ["77:F1", "77:F2"] })
    }

    def "Fase 28c: yaFirmada mira la marca otpId:factura del endoso; sin otpId (formato anterior) es false"() {
        given:
        def endoso = Endoso.builder().firmasOtp(["77:F1"]).build()
        endosoPort.findByNegociacionIdAndNumeroFactura("neg-1", _) >> Optional.of(endoso)

        expect:
        service.yaFirmada("neg-1", "F1", "77")
        !service.yaFirmada("neg-1", "F1", "78")
        !service.yaFirmada("neg-1", "F2", "77")
        !service.yaFirmada("neg-1", "F1", null)
    }

    def "Fase 28c: tieneFirmas es true con marcas o con aprobaciones registradas (formato anterior), y false sin endoso"() {
        given:
        endosoPort.findByNegociacionIdAndNumeroFactura("neg-1", "F1") >> Optional.of(endoso)
        endosoPort.findByNegociacionIdAndNumeroFactura("neg-1", "F9") >> Optional.empty()

        expect:
        service.tieneFirmas("neg-1", "F1") == esperado
        !service.tieneFirmas("neg-1", "F9")

        where:
        endoso                                                                          | esperado
        Endoso.builder().build()                                                        | false
        Endoso.builder().firmasOtp([]).aprobaciones([]).build()                         | false
        Endoso.builder().firmasOtp(["70:F1"]).build()                                   | true
        Endoso.builder().aprobaciones([new com.negociadororq.domain.model.AprobacionActorDto()]).build() | true
    }
}
