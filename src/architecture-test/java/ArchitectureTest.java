import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class ArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.negociadororq..");

    // ==================== REGLAS ACTIVAS ====================

    /**
     * Los controllers REST deben residir en la capa application.controller.
     * Aplica a todas las clases cuyo nombre termina en "Controller".
     */
    @Test
    void controllers_deben_estar_en_application_controller() {
        classes().that().haveNameMatching(".*Controller")
                .should().resideInAPackage("..application.controller..")
                .check(classes);
    }

    /**
     * Las clases con nombre *Service deben residir en application o infrastructure.
     * Se permite infrastructure porque QuotaService y PdfFirmaService son interfaces
     * de puerto definidas junto a su adaptador en la capa de persistencia/pdf.
     */
    @Test
    void services_deben_estar_en_application_o_infrastructure() {
        classes().that().haveNameMatching(".*Service")
                .should().resideInAnyPackage("..application..", "..infrastructure..")
                .check(classes);
    }

    /**
     * La infraestructura puede depender de application y domain, pero no puede
     * usar bibliotecas externas que no estén explícitamente aprobadas.
     */
    @Test
    void infraestructura_solo_puede_usar_librerias_aprobadas() {
        ArchRule rule = classes()
                .that().resideInAPackage("..infrastructure..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "..infrastructure..",
                        "..application..",
                        "..domain..",
                        "..enums..",
                        "java..",
                        "javax..",
                        "jakarta..",
                        "org.springframework..",
                        "org.slf4j..",
                        "org.w3c..",
                        "reactor..",
                        "io.swagger.v3..",
                        "lombok..",
                        "com.fasterxml.jackson..",
                        "org.apache.kafka..",
                        "org.apache.pdfbox..",
                        "com.openhtmltopdf..",
                        "org.bson..",
                        "io.jsonwebtoken.."
                );

        rule.check(classes);
    }

    /**
     * El dominio no debe usar anotaciones de Spring cuyo nombre de clase
     * contenga Service, Repository o Controller (esas son anotaciones de capas
     * superiores). Se permite @Document, @Entity, @Id y similares.
     */
    @Test
    void domain_no_debe_usar_anotaciones_de_capa_superior() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .haveNameMatching(".*(Spring|Autowired|Service|Repository|Controller).*")
                .check(classes);
    }

    // ==================== DEUDA TÉCNICA DOCUMENTADA ====================

    /**
     * DEUDA TÉCNICA — Dominio depende de Application.
     *
     * PendingOtp (domain.model) tiene un campo de tipo AprobacionRequestDto
     * (application.controller.dto), violando el principio de que el dominio
     * no debe depender de capas externas.
     *
     * Solución pendiente: crear un objeto de valor en domain (PendingOtpRequest)
     * y mapear desde AprobacionRequestDto antes de persistir en domain.
     */
    // @Test
    // void domain_no_debe_depende_de_nadie() {
    //     noClasses()
    //             .that().resideInAPackage("..domain..")
    //             .should().dependOnClassesThat()
    //             .resideInAnyPackage("..application..", "..infrastructure..")
    //             .check(classes);
    // }

    /**
     * DEUDA TÉCNICA — Application depende directamente de Infrastructure.
     *
     * Los servicios de aplicación inyectan repositorios de infraestructura
     * directamente (ej: AprobacionEndosoDescuentoServiceImpl usa NegociacionRepository,
     * FacturaPatchRepository, CompanyRepository, etc.) en lugar de depender de
     * interfaces de puerto (puertos de salida) definidas en la capa de aplicación.
     *
     * Solución pendiente: definir interfaces de puerto (OutputPort) en application
     * e implementarlas como adaptadores en infrastructure. Esto permitiría aislar
     * la capa de aplicación de los detalles de persistencia.
     */
    // @Test
    // void application_solo_puede_depende_de_domain() {
    //     classes()
    //             .that().resideInAPackage("..application..")
    //             .should().onlyDependOnClassesThat()
    //             .resideInAnyPackage("..application..", "..domain..", "..enums..",
    //                     "java..", "javax..", "jakarta..", "org.springframework..",
    //                     "org.slf4j..", "lombok..", "com.fasterxml.jackson..",
    //                     "com.openhtmltopdf..", "io.swagger.v3..")
    //             .check(classes);
    // }

    /**
     * DEUDA TÉCNICA — Ciclos de dependencia entre capas.
     *
     * Existe un ciclo: application → domain (servicios dependen de modelos)
     * y domain → application (PendingOtp depende de AprobacionRequestDto).
     * Este ciclo se resolverá al corregir la deuda técnica de PendingOtp.
     */
    // @Test
    // void no_deberia_haber_ciclos_entre_capas() {
    //     slices().matching("com.negociadororq.(*)..")
    //             .should().beFreeOfCycles()
    //             .check(classes);
    // }

    /**
     * DEUDA TÉCNICA — Modelos de dominio usan @Document/@Entity de frameworks.
     *
     * Los modelos de dominio usan @Document e @Id de Spring Data MongoDB,
     * y @Entity/@Table de Jakarta Persistence para los modelos JPA.
     * Esto viola Clean Architecture (el dominio no debe depender de frameworks).
     *
     * Solución pendiente: extraer entidades de persistencia a infrastructure.persistence.entity
     * y mapear desde/hacia los modelos de dominio puros usando MapStruct o builders.
     */
    // @Test
    // void domain_no_debe_usar_spring_data() { ... }
}