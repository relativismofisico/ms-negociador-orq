package com.negociadororq.infrastructure.config;

import java.util.List;

import com.negociadororq.application.mapper.EstadoFacturaMapper;
import com.negociadororq.application.mapper.OperacionMapper;
import com.negociadororq.application.port.output.AuditoriaOutputPort;
import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.CompanyOperationOutputPort;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.CompanySectionVerificationOutputPort;
import com.negociadororq.application.port.output.ContextoAuditoriaOutputPort;
import com.negociadororq.application.port.output.EmpresaQuotaOutputPort;
import com.negociadororq.application.port.output.EndosoOutputPort;
import com.negociadororq.application.port.output.EndosoPatchOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.application.port.output.IndirectFunderOfferOutputPort;
import com.negociadororq.application.port.output.IndirectFunderOfferSupplierQuotaOutputPort;
import com.negociadororq.application.port.output.MessageTemplateOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.port.output.NegociacionPatchOutputPort;
import com.negociadororq.application.port.output.OutboxOutputPort;
import com.negociadororq.application.port.output.PayerFundingVisibilityOutputPort;
import com.negociadororq.application.port.output.PdfFirmaOutputPort;
import com.negociadororq.application.port.output.PdfGeneradorOutputPort;
import com.negociadororq.application.port.output.PendingOtpOutputPort;
import com.negociadororq.application.port.output.PersonOutputPort;
import com.negociadororq.application.port.output.ReversionCuposReclamoOutputPort;
import com.negociadororq.application.port.output.TemplateEngineOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.application.port.output.UserQuotaOutputPort;
import com.negociadororq.application.service.AprobacionEndosoDescuentoServiceImpl;
import com.negociadororq.application.service.AuditoriaNegociacionService;
import com.negociadororq.application.service.CupoConsumidorService;
import com.negociadororq.application.service.CupoPropioFondeoService;
import com.negociadororq.application.service.CupoPropioFondeoServiceImpl;
import com.negociadororq.application.service.CupoReversionService;
import com.negociadororq.application.service.DocumentoEndosoService;
import com.negociadororq.application.service.DocumentoEndosoServiceImpl;
import com.negociadororq.application.service.EndosoDescuentoService;
import com.negociadororq.application.service.EndosoDescuentoServiceImpl;
import com.negociadororq.application.service.FacturaService;
import com.negociadororq.application.service.FacturaServiceImpl;
import com.negociadororq.application.service.FacturaVencimientoService;
import com.negociadororq.application.service.FirmaEndosoService;
import com.negociadororq.application.service.FirmaEndosoServiceImpl;
import com.negociadororq.application.service.IndirectFunderOfferSupplierQuotaConsultaService;
import com.negociadororq.application.service.IndirectFunderOfferSupplierQuotaConsultaServiceImpl;
import com.negociadororq.application.service.IndirectFunderOfferSupplierQuotaRecargaService;
import com.negociadororq.application.service.IndirectFunderOfferSupplierQuotaRecargaServiceImpl;
import com.negociadororq.application.service.NegociacionFactoringComfirmingService;
import com.negociadororq.application.service.NegociacionFactoringComfirmingServiceImpl;
import com.negociadororq.application.service.NotificacionService;
import com.negociadororq.application.service.NotificacionServiceImpl;
import com.negociadororq.application.service.OperadorServiceImpl;
import com.negociadororq.application.service.OutboxService;
import com.negociadororq.application.service.ProcesarFacturaNegociacionService;
import com.negociadororq.application.service.ProcesarFacturaNegociacionServiceImpl;
import com.negociadororq.application.service.ReversionCuposService;
import com.negociadororq.application.service.ReversionCuposServiceImpl;
import com.negociadororq.application.service.TasaConversionService;
import com.negociadororq.application.service.TasaConversionServiceImpl;
import com.negociadororq.application.service.TemplateRenderService;
import com.negociadororq.application.service.TemplateRenderServiceImpl;
import com.negociadororq.application.service.ValidacionFacturaService;
import com.negociadororq.application.service.ValidacionFacturaServiceImpl;
import com.negociadororq.application.service.notificacion.CuerpoNotificacionFactory;
import com.negociadororq.application.service.notificacion.DestinatarioResolver;
import com.negociadororq.application.service.notificacion.impl.FacturaCuerpoBuilder;
import com.negociadororq.application.service.notificacion.impl.NegociacionCuerpoBuilder;
import com.negociadororq.application.service.notificacion.impl.PersonaCuerpoBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Cablea manualmente las clases de {@code application/}, que dejaron de llevar
 * {@code @Service}/{@code @Component}/{@code @Value} para no depender del
 * framework (arquitectura hexagonal). Un método {@code @Bean} por clase, con
 * los parámetros en el mismo orden en que Lombok genera el constructor a
 * partir de los campos {@code private final} declarados en cada una.
 */
@Configuration
public class ApplicationBeansConfig {

    @Bean
    public EstadoFacturaMapper estadoFacturaMapper() {
        return new EstadoFacturaMapper();
    }

    @Bean
    public OperacionMapper operacionMapper() {
        return new OperacionMapper();
    }

    @Bean
    public DestinatarioResolver destinatarioResolver() {
        return new DestinatarioResolver();
    }

    @Bean
    public FacturaCuerpoBuilder facturaCuerpoBuilder() {
        return new FacturaCuerpoBuilder();
    }

    @Bean
    public NegociacionCuerpoBuilder negociacionCuerpoBuilder() {
        return new NegociacionCuerpoBuilder();
    }

    @Bean
    public PersonaCuerpoBuilder personaCuerpoBuilder() {
        return new PersonaCuerpoBuilder();
    }

    @Bean
    public CuerpoNotificacionFactory cuerpoNotificacionFactory(
            final FacturaCuerpoBuilder facturaCuerpoBuilder,
            final NegociacionCuerpoBuilder negociacionCuerpoBuilder,
            final PersonaCuerpoBuilder personaCuerpoBuilder) {
        return new CuerpoNotificacionFactory(
                List.of(facturaCuerpoBuilder, negociacionCuerpoBuilder, personaCuerpoBuilder));
    }

    @Bean
    public OutboxService outboxService(final OutboxOutputPort outboxPort) {
        return new OutboxService(outboxPort);
    }

    @Bean
    public OperadorServiceImpl operadorServiceImpl(
            final UserQuotaOutputPort userQuotaPort,
            final EmpresaQuotaOutputPort empresaQuotaPort,
            final FondeadorQuotaOutputPort fondeadorQuotaPort) {
        return new OperadorServiceImpl(userQuotaPort, empresaQuotaPort, fondeadorQuotaPort);
    }

    // No hace falta un @Bean explícito por interfaz (CupoConsumidorService/CupoReversionService):
    // como operadorServiceImpl() es el único bean del contexto asignable a cada una, Spring lo
    // resuelve automáticamente por tipo. Declarar acá un @Bean adicional que devuelva la misma
    // instancia produce 2 candidatos para el mismo tipo (NoUniqueBeanDefinitionException) — se
    // encontró así, en bootRun, al hacerlo con AprobacionEndosoDescuentoService/Interna más abajo.

    @Bean
    public CupoPropioFondeoService cupoPropioFondeoService(
            final FondeadorQuotaOutputPort fondeadorQuotaPort,
            final EmpresaQuotaOutputPort empresaQuotaPort) {
        return new CupoPropioFondeoServiceImpl(fondeadorQuotaPort, empresaQuotaPort);
    }

    @Bean
    public ValidacionFacturaService validacionFacturaService(
            final FacturaOutputPort facturaPort,
            final ClockPort clock,
            @Value("${negociacion.dias-minimos-vencimiento:10}") final long diasMinimosVencimiento) {
        return new ValidacionFacturaServiceImpl(facturaPort, clock, diasMinimosVencimiento);
    }

    @Bean
    public TemplateRenderService templateRenderService(
            final MessageTemplateOutputPort messageTemplatePort,
            final TemplateEngineOutputPort templateEnginePort) {
        return new TemplateRenderServiceImpl(messageTemplatePort, templateEnginePort);
    }

    @Bean
    public TasaConversionService tasaConversionService() {
        return new TasaConversionServiceImpl();
    }

    @Bean
    public FacturaService facturaService(final FacturaOutputPort facturaPort) {
        return new FacturaServiceImpl(facturaPort);
    }

    @Bean
    public IndirectFunderOfferSupplierQuotaRecargaService indirectFunderOfferSupplierQuotaRecargaService(
            final IndirectFunderOfferSupplierQuotaOutputPort indirectFunderOfferSupplierQuotaPort) {
        return new IndirectFunderOfferSupplierQuotaRecargaServiceImpl(indirectFunderOfferSupplierQuotaPort);
    }

    @Bean
    public IndirectFunderOfferSupplierQuotaConsultaService indirectFunderOfferSupplierQuotaConsultaService(
            final PayerFundingVisibilityOutputPort payerFundingVisibilityPort,
            final IndirectFunderOfferOutputPort indirectFunderOfferPort,
            final IndirectFunderOfferSupplierQuotaOutputPort indirectFunderOfferSupplierQuotaPort) {
        return new IndirectFunderOfferSupplierQuotaConsultaServiceImpl(
                payerFundingVisibilityPort, indirectFunderOfferPort, indirectFunderOfferSupplierQuotaPort);
    }

    @Bean
    public FirmaEndosoService firmaEndosoService(
            final EndosoOutputPort endosoPort,
            final PdfFirmaOutputPort pdfFirmaPort,
            final ClockPort clock,
            final AuditoriaNegociacionService auditoriaNegociacionService) {
        return new FirmaEndosoServiceImpl(endosoPort, pdfFirmaPort, clock, auditoriaNegociacionService);
    }

    @Bean
    public AuditoriaNegociacionService auditoriaNegociacionService(
            final AuditoriaOutputPort auditoriaPort,
            final ContextoAuditoriaOutputPort contextoAuditoriaPort) {
        return new AuditoriaNegociacionService(auditoriaPort, contextoAuditoriaPort);
    }

    @Bean
    public DocumentoEndosoService documentoEndosoService(
            final TemplateRenderService templateRenderService,
            final PdfGeneradorOutputPort pdfGeneradorPort,
            final UserOutputPort userPort,
            final PersonOutputPort personPort,
            final ClockPort clock) {
        return new DocumentoEndosoServiceImpl(templateRenderService, pdfGeneradorPort, userPort, personPort, clock);
    }

    @Bean
    public FacturaVencimientoService facturaVencimientoService(
            final FacturaOutputPort facturaPort,
            final NegociacionOutputPort negociacionPort,
            final CompanyOutputPort companyPort,
            final CupoReversionService cupoReversionService,
            final ClockPort clock) {
        return new FacturaVencimientoService(facturaPort, negociacionPort, companyPort, cupoReversionService, clock);
    }

    // El número de parámetros espeja 1:1 el constructor real de la clase (generado por
    // Lombok en orden de declaración de campos) — no es una señal de diseño a corregir acá.
    @SuppressWarnings("checkstyle:ParameterNumber")
    @Bean
    public NotificacionService notificacionService(
            final OutboxService outboxService,
            final CuerpoNotificacionFactory cuerpoFactory,
            final NegociacionOutputPort negociacionPort,
            final DestinatarioResolver destinatarioResolver,
            final CompanyOutputPort companyPort,
            final FacturaOutputPort facturaPort,
            final ClockPort clock,
            @Value("${topics.notificacion-email}") final String topicNotificacionEmail,
            @Value("${platform.operador.url:http://localhost:4800}") final String urlPlataformaOperador) {
        return new NotificacionServiceImpl(
                outboxService, cuerpoFactory, negociacionPort, destinatarioResolver, companyPort,
                facturaPort, clock, topicNotificacionEmail, urlPlataformaOperador);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    @Bean
    public ReversionCuposService reversionCuposService(
            final NegociacionOutputPort negociacionPort,
            final FacturaOutputPort facturaPort,
            final FacturaPatchOutputPort facturaPatchPort,
            final EndosoPatchOutputPort endosoPatchPort,
            final CupoReversionService cupoReversionService,
            final UserOutputPort userPort,
            final CompanyOutputPort companyPort,
            final NotificacionService notificacionService,
            final ReversionCuposReclamoOutputPort reclamoPort,
            final TransactionOperations transactionOperations) {
        return new ReversionCuposServiceImpl(
                negociacionPort, facturaPort, facturaPatchPort, endosoPatchPort,
                cupoReversionService, userPort, companyPort, notificacionService,
                reclamoPort, transactionOperations);
    }

    // El número de parámetros espeja 1:1 el constructor real de la clase (generado por
    // Lombok en orden de declaración de campos) — no es una señal de diseño a corregir acá.
    @SuppressWarnings({"checkstyle:ParameterNumber", "PMD.ExcessiveParameterList"})
    @Bean
    public ProcesarFacturaNegociacionService procesarFacturaNegociacionService(
            final FacturaOutputPort facturaPort,
            final CupoConsumidorService cupoConsumidorService,
            final UserOutputPort userPort,
            final CompanyOutputPort companyPort,
            final EmpresaQuotaOutputPort empresaQuotaPort,
            final FondeadorQuotaOutputPort fondeadorQuotaPort,
            final FacturaPatchOutputPort facturaPatchPort,
            final ValidacionFacturaService validacionFacturaService,
            final PayerFundingVisibilityOutputPort payerFundingVisibilityPort,
            final IndirectFunderOfferOutputPort indirectFunderOfferPort,
            final IndirectFunderOfferSupplierQuotaOutputPort indirectFunderOfferSupplierQuotaPort,
            final ClockPort clock) {
        return new ProcesarFacturaNegociacionServiceImpl(
                facturaPort, cupoConsumidorService, userPort, companyPort, empresaQuotaPort,
                fondeadorQuotaPort, facturaPatchPort, validacionFacturaService, payerFundingVisibilityPort,
                indirectFunderOfferPort, indirectFunderOfferSupplierQuotaPort, clock);
    }

    // El número de parámetros espeja 1:1 el constructor real de la clase (generado por
    // Lombok en orden de declaración de campos) — no es una señal de diseño a corregir acá.
    @SuppressWarnings({"checkstyle:ParameterNumber", "PMD.ExcessiveParameterList"})
    @Bean
    public NegociacionFactoringComfirmingService negociacionFactoringComfirmingService(
            final FacturaOutputPort facturaPort,
            final NegociacionOutputPort negociacionPort,
            final UserOutputPort userPort,
            final CompanyOutputPort companyPort,
            final CompanyOperationOutputPort companyOperationPort,
            final CompanySectionVerificationOutputPort companySectionVerificationPort,
            final NotificacionService notificacionService,
            final ProcesarFacturaNegociacionService procesarFacturaNegociacionService,
            final FacturaPatchOutputPort facturaPatchPort,
            final ClockPort clock,
            final AuditoriaNegociacionService auditoriaNegociacionService) {
        return new NegociacionFactoringComfirmingServiceImpl(
                facturaPort, negociacionPort, userPort, companyPort, companyOperationPort,
                companySectionVerificationPort, notificacionService, procesarFacturaNegociacionService,
                facturaPatchPort, clock, auditoriaNegociacionService);
    }

    // El número de parámetros espeja 1:1 el constructor real de la clase (generado por
    // Lombok en orden de declaración de campos) — no es una señal de diseño a corregir acá.
    @SuppressWarnings({"checkstyle:ParameterNumber", "PMD.ExcessiveParameterList"})
    @Bean
    public EndosoDescuentoService endosoDescuentoService(
            final NegociacionOutputPort negociacionPort,
            final EndosoOutputPort endosoPort,
            final FacturaOutputPort facturaPort,
            final NotificacionService notificacionService,
            final FacturaPatchOutputPort facturaPatchPort,
            final DocumentoEndosoService documentoEndosoService,
            final NegociacionPatchOutputPort negociacionPatchPort,
            final CompanyOutputPort companyPort,
            final FondeadorQuotaOutputPort fondeadorQuotaPort,
            final UserOutputPort userPort,
            final CupoReversionService cupoReversionService,
            final ClockPort clock,
            final AuditoriaNegociacionService auditoriaNegociacionService) {
        return new EndosoDescuentoServiceImpl(
                negociacionPort, endosoPort, facturaPort, notificacionService, facturaPatchPort,
                documentoEndosoService, negociacionPatchPort, companyPort, fondeadorQuotaPort,
                userPort, cupoReversionService, clock, auditoriaNegociacionService);
    }

    // El número de parámetros espeja 1:1 el constructor real de la clase (generado por
    // Lombok en orden de declaración de campos) — no es una señal de diseño a corregir acá.
    @SuppressWarnings({"checkstyle:ParameterNumber", "PMD.ExcessiveParameterList"})
    @Bean
    public AprobacionEndosoDescuentoServiceImpl aprobacionEndosoDescuentoServiceImpl(
            final NegociacionOutputPort negociacionPort,
            final FacturaPatchOutputPort facturaPatchPort,
            final EndosoPatchOutputPort endosoPatchPort,
            final OutboxService outboxService,
            final CupoReversionService cupoReversionService,
            final CompanyOutputPort companyPort,
            final FondeadorQuotaOutputPort fondeadorQuotaPort,
            final FacturaOutputPort facturaPort,
            final UserOutputPort userPort,
            final PendingOtpOutputPort pendingOtpPort,
            final FirmaEndosoService firmaEndosoService,
            final NotificacionService notificacionService,
            final EndosoOutputPort endosoPort,
            final DocumentoEndosoService documentoEndosoService,
            final ClockPort clock,
            @Value("${topics.otp-request}") final String topicRequestOtp,
            @Value("${topics.otp-validate}") final String topicValidateOtp,
            @Value("${topics.liquidacion}") final String topicLiquidacionSolicitada,
            final AuditoriaNegociacionService auditoriaNegociacionService) {
        return new AprobacionEndosoDescuentoServiceImpl(
                negociacionPort, facturaPatchPort, endosoPatchPort, outboxService, cupoReversionService,
                companyPort, fondeadorQuotaPort, facturaPort, userPort, pendingOtpPort, firmaEndosoService,
                notificacionService, endosoPort, documentoEndosoService, clock,
                topicRequestOtp, topicValidateOtp, topicLiquidacionSolicitada, auditoriaNegociacionService);
    }

    // Igual que con OperadorServiceImpl más arriba: sin @Bean explícito para
    // AprobacionEndosoDescuentoService/AprobacionInternaService — aprobacionEndosoDescuentoServiceImpl()
    // es el único bean asignable a cada una y Spring lo resuelve por tipo. Fue precisamente
    // este par el que disparó el NoUniqueBeanDefinitionException real en bootRun.
}
