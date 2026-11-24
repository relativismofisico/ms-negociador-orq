package com.negociadororq.infrastructure.config;

import java.time.Duration;
import java.util.List;

import com.negociadororq.application.mapper.EstadoFacturaMapper;
import com.negociadororq.application.mapper.OperacionMapper;
import com.negociadororq.application.port.output.AlertaFirmaOutputPort;
import com.negociadororq.application.port.output.AuditoriaOutputPort;
import com.negociadororq.application.port.output.CancelacionLiquidacionOutputPort;
import com.negociadororq.application.port.output.ClockPort;
import com.negociadororq.application.port.output.CompanyOperationOutputPort;
import com.negociadororq.application.port.output.CompanyOutputPort;
import com.negociadororq.application.port.output.CompanySectionVerificationOutputPort;
import com.negociadororq.application.port.output.ContextoAuditoriaOutputPort;
import com.negociadororq.application.port.output.CorreoTransaccionalOutputPort;
import com.negociadororq.application.port.output.EmpresaQuotaOutputPort;
import com.negociadororq.application.port.output.EndosoOutputPort;
import com.negociadororq.application.port.output.EndosoPatchOutputPort;
import com.negociadororq.application.port.output.FacturaOutputPort;
import com.negociadororq.application.port.output.FacturaPatchOutputPort;
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.application.port.output.IndirectFunderOfferOutputPort;
import com.negociadororq.application.port.output.IndirectFunderOfferSupplierQuotaOutputPort;
import com.negociadororq.application.port.output.MarcaNegociacionOutputPort;
import com.negociadororq.application.port.output.MessageTemplateOutputPort;
import com.negociadororq.application.port.output.NegociacionOutputPort;
import com.negociadororq.application.port.output.NegociacionPatchOutputPort;
import com.negociadororq.application.port.output.OperacionPendienteOutputPort;
import com.negociadororq.application.port.output.OutboxOutputPort;
import com.negociadororq.application.port.output.PayerFundingVisibilityOutputPort;
import com.negociadororq.application.port.output.PdfFirmaOutputPort;
import com.negociadororq.application.port.output.PdfGeneradorOutputPort;
import com.negociadororq.application.port.output.PendingOtpOutputPort;
import com.negociadororq.application.port.output.PersonOutputPort;
import com.negociadororq.application.port.output.TemplateEngineOutputPort;
import com.negociadororq.application.port.output.TransaccionMongoOutputPort;
import com.negociadororq.application.port.output.UserOutputPort;
import com.negociadororq.application.port.output.UserQuotaOutputPort;
import com.negociadororq.application.port.output.ValidacionOtpOutputPort;
import com.negociadororq.application.service.AprobacionEndosoDescuentoServiceImpl;
import com.negociadororq.application.service.AprobacionInternaService;
import com.negociadororq.application.service.AuditoriaNegociacionService;
import com.negociadororq.application.service.AvisoCupoIndirectoService;
import com.negociadororq.application.service.ConfirmacionOtpService;
import com.negociadororq.application.service.ConfirmacionOtpServiceImpl;
import com.negociadororq.application.service.CorreosReversionService;
import com.negociadororq.application.service.CupoConsumidorService;
import com.negociadororq.application.service.CupoPropioFondeoService;
import com.negociadororq.application.service.CupoPropioFondeoServiceImpl;
import com.negociadororq.application.service.CupoReversionService;
import com.negociadororq.application.service.DocumentoEndosoService;
import com.negociadororq.application.service.DocumentoEndosoServiceImpl;
import com.negociadororq.application.service.EndosoDescuentoService;
import com.negociadororq.application.service.EndosoDescuentoServiceImpl;
import com.negociadororq.application.service.FacturaVencimientoService;
import com.negociadororq.application.service.FirmaEndosoService;
import com.negociadororq.application.service.FirmaEndosoServiceImpl;
import com.negociadororq.application.service.FirmaOtpService;
import com.negociadororq.application.service.FirmaOtpServiceImpl;
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
import com.negociadororq.application.service.PropagacionCupoOfertaService;
import com.negociadororq.application.service.ReanudadorCrearNegociacion;
import com.negociadororq.application.service.ReanudadorOperacion;
import com.negociadororq.application.service.ReconciliadorOperacionesService;
import com.negociadororq.application.service.ReversionCuposService;
import com.negociadororq.application.service.ReversionCuposServiceImpl;
import com.negociadororq.application.service.ReversionExactaService;
import com.negociadororq.application.service.SeguimientoLiquidacionService;
import com.negociadororq.application.service.TasaConversionService;
import com.negociadororq.application.service.TasaConversionServiceImpl;
import com.negociadororq.application.service.TemplateRenderService;
import com.negociadororq.application.service.TemplateRenderServiceImpl;
import com.negociadororq.application.service.ValidacionFacturaService;
import com.negociadororq.application.service.ValidacionFacturaServiceImpl;
import com.negociadororq.application.service.ValidadorOtpPendiente;
import com.negociadororq.application.service.notificacion.CuerpoNotificacionFactory;
import com.negociadororq.application.service.notificacion.DestinatarioResolver;
import com.negociadororq.application.service.notificacion.impl.FacturaCuerpoBuilder;
import com.negociadororq.application.service.notificacion.impl.NegociacionCuerpoBuilder;
import com.negociadororq.application.service.notificacion.impl.PersonaCuerpoBuilder;
import org.springframework.beans.factory.ObjectProvider;
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

    /**
     * Fase 28d (P-19): propagación del cupo por proveedor de la oferta indirecta a los proveedores que ya negociaron.
     *
     * @param indirectFunderOfferSupplierQuotaPort cupos indirectos por proveedor
     * @return el servicio
     */
    @Bean
    public PropagacionCupoOfertaService propagacionCupoOfertaService(
            final IndirectFunderOfferSupplierQuotaOutputPort indirectFunderOfferSupplierQuotaPort) {
        return new PropagacionCupoOfertaService(indirectFunderOfferSupplierQuotaPort);
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

    /** Fase 28c: estados de la firma por OTP, reintentos y alerta de firmas fallidas. */
    @Bean
    public FirmaOtpService firmaOtpService(
            final PendingOtpOutputPort pendingOtpPort,
            final AprobacionInternaService aprobacionInternaService,
            final NotificacionService notificacionService,
            final AlertaFirmaOutputPort alertaFirmaPort,
            final ClockPort clock,
            @Value("${firma.reconciliador.max-intentos:5}") final int maxIntentosFirma,
            @Value("${firma.reconciliador.espera:PT2M}") final java.time.Duration esperaReintento) {
        return new FirmaOtpServiceImpl(pendingOtpPort, aprobacionInternaService, notificacionService,
                alertaFirmaPort, clock, maxIntentosFirma, esperaReintento);
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
            final FacturaPatchOutputPort facturaPatchPort,
            final OperacionPendienteOutputPort operacionPendienteOutputPort,
            final ReversionExactaService reversionExactaService,
            final ClockPort clock) {
        return new FacturaVencimientoService(facturaPort, facturaPatchPort, operacionPendienteOutputPort,
                reversionExactaService, clock);
    }

    /**
     * Fase 28d (A2): devuelve exactamente lo que la creación descontó (registro en {@code operacion_pendiente}), una vez.
     *
     * @param operacionPendienteOutputPort {@code operacion_pendiente}
     * @param cupoReversionService         devoluciones de operador, empresa y fondeadores
     * @param indirecto                    devolución de la oferta indirecta
     * @param transactionOperations        transacción MySQL
     * @return el servicio
     */
    @Bean
    public ReversionExactaService reversionExactaService(
            final OperacionPendienteOutputPort operacionPendienteOutputPort,
            final CupoReversionService cupoReversionService,
            final IndirectFunderOfferSupplierQuotaOutputPort indirecto,
            final TransactionOperations transactionOperations,
            final AuditoriaNegociacionService auditoriaNegociacionService,
            final CorreosReversionService correosReversionService) {
        return new ReversionExactaService(operacionPendienteOutputPort, cupoReversionService, indirecto,
                transactionOperations, auditoriaNegociacionService, correosReversionService);
    }

    /**
     * Fase 28d (A2): plantillas A y B de la reversión por el outbox JDBC (en la transacción de la reversión).
     *
     * @param correoTransaccionalOutputPort outbox JDBC
     * @param companyPort                   nombres de las empresas
     * @param negociacionPort               contexto si el registro no lo trae
     * @param clock                         reloj
     * @return el servicio
     */
    @Bean
    public CorreosReversionService correosReversionService(
            final CorreoTransaccionalOutputPort correoTransaccionalOutputPort,
            final CompanyOutputPort companyPort,
            final NegociacionOutputPort negociacionPort,
            final ClockPort clock) {
        return new CorreosReversionService(correoTransaccionalOutputPort, companyPort, negociacionPort, clock);
    }

    /**
     * Fase 28d (A2): reanudador de CREAR_NEGOCIACION (marcador CANCELADA primero; completa o revierte exacto).
     *
     * @param marcaNegociacionOutputPort   marcador único
     * @param negociacionPort              negociación
     * @param operacionPendienteOutputPort operaciones pendientes
     * @param facturaPatchPort             reparación de la factura
     * @param reversionExactaService       reversión exacta
     * @return el reanudador
     */
    @Bean
    public ReanudadorCrearNegociacion reanudadorCrearNegociacion(
            final MarcaNegociacionOutputPort marcaNegociacionOutputPort,
            final NegociacionOutputPort negociacionPort,
            final OperacionPendienteOutputPort operacionPendienteOutputPort,
            final FacturaPatchOutputPort facturaPatchPort,
            final ReversionExactaService reversionExactaService) {
        return new ReanudadorCrearNegociacion(marcaNegociacionOutputPort, negociacionPort,
                operacionPendienteOutputPort, facturaPatchPort, reversionExactaService);
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

    @Bean
    public ReversionCuposService reversionCuposService(
            final NegociacionOutputPort negociacionPort,
            final FacturaOutputPort facturaPort,
            final FacturaPatchOutputPort facturaPatchPort,
            final NotificacionService notificacionService,
            final ReversionExactaService reversionExactaService) {
        return new ReversionCuposServiceImpl(negociacionPort, facturaPort, facturaPatchPort, notificacionService,
                reversionExactaService);
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
            final ClockPort clock,
            final OperacionPendienteOutputPort operacionPendienteOutputPort,
            final AuditoriaNegociacionService auditoriaNegociacionService) {
        return new ProcesarFacturaNegociacionServiceImpl(
                facturaPort, cupoConsumidorService, userPort, companyPort, empresaQuotaPort,
                fondeadorQuotaPort, facturaPatchPort, validacionFacturaService, payerFundingVisibilityPort,
                indirectFunderOfferPort, indirectFunderOfferSupplierQuotaPort, clock, operacionPendienteOutputPort,
                auditoriaNegociacionService);
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
            final AuditoriaNegociacionService auditoriaNegociacionService,
            final TransaccionMongoOutputPort transaccionMongoOutputPort,
            final MarcaNegociacionOutputPort marcaNegociacionOutputPort,
            final OperacionPendienteOutputPort operacionPendienteOutputPort,
            final ReversionExactaService reversionExactaService,
            final AvisoCupoIndirectoService avisoCupoIndirectoService) {
        return new NegociacionFactoringComfirmingServiceImpl(
                facturaPort, negociacionPort, userPort, companyPort, companyOperationPort,
                companySectionVerificationPort, notificacionService, procesarFacturaNegociacionService,
                facturaPatchPort, clock, auditoriaNegociacionService, transaccionMongoOutputPort,
                marcaNegociacionOutputPort, operacionPendienteOutputPort, reversionExactaService,
                avisoCupoIndirectoService);
    }

    /**
     * Fase 28d (punto 1.2c): aviso al fondeador por falta de cupo indirecto, por el outbox JDBC (uno por trío al día).
     *
     * @param correoTransaccionalOutputPort outbox JDBC
     * @param companyPort                   nombre del proveedor
     * @param clock                         reloj
     * @return el servicio
     */
    @Bean
    public AvisoCupoIndirectoService avisoCupoIndirectoService(
            final CorreoTransaccionalOutputPort correoTransaccionalOutputPort,
            final CompanyOutputPort companyPort,
            final ClockPort clock) {
        return new AvisoCupoIndirectoService(correoTransaccionalOutputPort, companyPort, clock);
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
            final ReversionExactaService reversionExactaService,
            final ClockPort clock,
            final AuditoriaNegociacionService auditoriaNegociacionService) {
        return new EndosoDescuentoServiceImpl(
                negociacionPort, endosoPort, facturaPort, notificacionService, facturaPatchPort,
                documentoEndosoService, negociacionPatchPort, companyPort, fondeadorQuotaPort,
                userPort, reversionExactaService, clock, auditoriaNegociacionService);
    }

    /**
     * Fase 28d (A4): seguimiento de las solicitudes de liquidación (reenvíos, cancelación y revisión manual).
     *
     * @return el servicio
     */
    @Bean
    @SuppressWarnings({"checkstyle:ParameterNumber", "PMD.ExcessiveParameterList"})
    public SeguimientoLiquidacionService seguimientoLiquidacionService(
            final OperacionPendienteOutputPort operacionPendienteOutputPort, final OutboxOutputPort outboxOutputPort,
            final FacturaOutputPort facturaOutputPort, final FacturaPatchOutputPort facturaPatchOutputPort,
            final CancelacionLiquidacionOutputPort cancelacionLiquidacionOutputPort,
            final ReversionCuposService reversionCuposService, final ReversionExactaService reversionExactaService,
            final ClockPort clockPort,
            @Value("${liquidacion.seguimiento.intervalo:PT15M}") final java.time.Duration intervalo,
            @Value("${liquidacion.seguimiento.reenvios:4}") final int reenvios,
            @Value("${liquidacion.seguimiento.max-cancelaciones:8}") final int maxCancelaciones,
            @Value("${liquidacion.seguimiento.lote:50}") final int lote) {
        return new SeguimientoLiquidacionService(operacionPendienteOutputPort, outboxOutputPort, facturaOutputPort,
                facturaPatchOutputPort, cancelacionLiquidacionOutputPort, reversionCuposService, reversionExactaService,
                clockPort, new SeguimientoLiquidacionService.Parametros(intervalo, reenvios, maxCancelaciones, lote));
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
            final ReversionExactaService reversionExactaService,
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
            @Value("${topics.liquidacion}") final String topicLiquidacionSolicitada,
            final AuditoriaNegociacionService auditoriaNegociacionService,
            final SeguimientoLiquidacionService seguimientoLiquidacionService,
            final TransaccionMongoOutputPort transaccionMongoOutputPort) {
        return new AprobacionEndosoDescuentoServiceImpl(
                negociacionPort, facturaPatchPort, endosoPatchPort, outboxService, reversionExactaService,
                companyPort, fondeadorQuotaPort, facturaPort, userPort, pendingOtpPort, firmaEndosoService,
                notificacionService, endosoPort, documentoEndosoService, clock,
                topicRequestOtp, topicLiquidacionSolicitada, auditoriaNegociacionService, seguimientoLiquidacionService,
                transaccionMongoOutputPort);
    }

    // Igual que con OperadorServiceImpl más arriba: sin @Bean explícito para
    // AprobacionEndosoDescuentoService/AprobacionInternaService — aprobacionEndosoDescuentoServiceImpl()
    // es el único bean asignable a cada una y Spring lo resuelve por tipo. Fue precisamente
    // este par el que disparó el NoUniqueBeanDefinitionException real en bootRun.

    /**
     * Fase 28d, opción A: reconciliador único de {@code operacion_pendiente}, con un reanudador por tipo (los aporta
     * cada flujo).
     */
    @Bean
    public ReconciliadorOperacionesService reconciliadorOperacionesService(
            final OperacionPendienteOutputPort operacionPendienteOutputPort, final ClockPort clockPort,
            final ObjectProvider<ReanudadorOperacion> reanudadores,
            @Value("${operaciones.reconciliador.gracia:PT2M}") final Duration gracia,
            @Value("${operaciones.reconciliador.concesion:PT5M}") final Duration concesion,
            @Value("${operaciones.reconciliador.lote:50}") final int lote,
            @Value("${operaciones.reconciliador.max-intentos:10}") final int maxIntentos) {
        return new ReconciliadorOperacionesService(operacionPendienteOutputPort, clockPort,
                reanudadores.orderedStream().toList(), gracia, concesion, lote, maxIntentos);
    }

    /**
     * Fase 28d (H): validación por HTTP del código de un pendiente OTP con la clave
     * {@code idNegociacion|rutEmpresa|rutOperador|n}.
     *
     * @param validacionOtpOutputPort cliente HTTP de security
     * @param pendingOtpOutputPort    pendientes OTP
     * @return el validador
     */
    @Bean
    public ValidadorOtpPendiente validadorOtpPendiente(final ValidacionOtpOutputPort validacionOtpOutputPort,
            final PendingOtpOutputPort pendingOtpOutputPort) {
        return new ValidadorOtpPendiente(validacionOtpOutputPort, pendingOtpOutputPort);
    }

    /**
     * Fase 28d (A1): {@code confirmar-otp} valida el código por HTTP contra security (el único camino) y sigue la
     * firma.
     *
     * @param pendingOtpOutputPort  pendientes OTP
     * @param validadorOtpPendiente validación por HTTP
     * @param firmaOtpService       firma con la respuesta de security
     * @return el servicio de confirmación
     */
    @Bean
    public ConfirmacionOtpService confirmacionOtpService(final PendingOtpOutputPort pendingOtpOutputPort,
            final ValidadorOtpPendiente validadorOtpPendiente, final FirmaOtpService firmaOtpService) {
        return new ConfirmacionOtpServiceImpl(pendingOtpOutputPort, validadorOtpPendiente, firmaOtpService);
    }
}
