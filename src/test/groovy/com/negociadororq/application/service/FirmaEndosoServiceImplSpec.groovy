package com.negociadororq.application.service

import com.negociadororq.application.port.output.EndosoOutputPort
import com.negociadororq.application.port.output.PdfFirmaOutputPort
import com.negociadororq.domain.enums.RolActor
import com.negociadororq.domain.model.AprobacionActorDto
import com.negociadororq.domain.model.Endoso
import com.negociadororq.domain.model.FacturaNegociada
import com.negociadororq.domain.model.PendingOtp
import spock.lang.Specification

class FirmaEndosoServiceImplSpec extends Specification {

    EndosoOutputPort endosoPort = Mock()
    PdfFirmaOutputPort pdfFirmaPort = Mock()
    FirmaEndosoServiceImpl service = new FirmaEndosoServiceImpl(endosoPort, pdfFirmaPort)

    def "firmarPdfEndoso actualiza documentoPdf del endoso y guarda"() {
        given:
        def pdfOriginal = "PDF-ORIGINAL".bytes
        def pdfFirmado = "PDF-FIRMADO".bytes

        def endoso = Endoso.builder()
                .id("endoso-001")
                .negociacionId("neg-001")
                .numeroFactura("FECI001")
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

    def "firmarPdfEndoso usa el index de aprobaciones existentes"() {
        given:
        def pdfOriginal = "PDF".bytes
        def pdfFirmado = "PDF-SIGNED".bytes
        def endoso = Endoso.builder()
                .documentoPdf(pdfOriginal)
                .build()

        def aprobacion = new AprobacionActorDto()
        def factura = FacturaNegociada.builder()
                .numeroFactura("F001")
                .aprobaciones([aprobacion, aprobacion]) // size=2
                .build()

        def pending = PendingOtp.builder().otp("456").ip("1.2.3.4").build()

        endosoPort.findByNegociacionIdAndNumeroFactura("neg-X", "F001") >> Optional.of(endoso)
        pdfFirmaPort.agregarFirma(pdfOriginal, _, 2) >> pdfFirmado

        when:
        service.firmarPdfEndoso("neg-X", factura, pending, "RUT", "OP",
                RolActor.PROVEEDOR, true, "OK")

        then:
        1 * pdfFirmaPort.agregarFirma(pdfOriginal, { it.rutEmpresa == "RUT" && it.otp == "456" }, 2) >> pdfFirmado
        endoso.documentoPdf == pdfFirmado
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
}