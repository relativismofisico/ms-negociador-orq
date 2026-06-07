package com.negociadororq.infrastructure.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración central de springdoc-openapi para ms-negociador-orq.
 * Registra el esquema de seguridad Bearer JWT y los metadatos de la API.
 * La UI se habilita/deshabilita por perfil mediante springdoc.swagger-ui.enabled.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(buildInfo())
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", buildSecurityScheme()));
    }

    private Info buildInfo() {
        return new Info()
            .title("ms-negociador-orq")
            .description(
                "Orquestador de flujos de negociación de facturas para operaciones de Factoring y Confirming.\n\n"
                + "## Autenticación\n"
                + "Todos los endpoints requieren un token **JWT Bearer** firmado con HMAC-HS256. "
                + "El token debe incluir el claim `rol` con uno de los valores permitidos por el grupo del endpoint.\n\n"
                + "## Flujo típico\n"
                + "1. `POST /negociar/factura` — crea la negociación\n"
                + "2. `POST /negociaciones/{id}/endosos` — registra el endoso de descuento\n"
                + "3. `POST /negociaciones/solicitar-otp` — solicita aprobación por OTP\n"
                + "4. `POST /negociaciones/confirmar-otp` — confirma el OTP y aprueba la operación\n\n"
                + "## Roles disponibles\n"
                + "| Rol | Descripción |\n"
                + "|---|---|\n"
                + "| `ADMINISTRADOR` | Acceso a todos los endpoints |\n"
                + "| `EMPRESA` | Puede negociar, endosar y aprobar |\n"
                + "| `OPERARIO` | Puede negociar y validar facturas |\n"
                + "| `FONDEADOR` | Puede endosar, aprobar y validar |")
            .version("1.0.0")
            .contact(new Contact()
                .name("Equipo Factoring")
                .email("factoring@empresa.com"))
            .license(new License()
                .name("Uso interno — Propietario"));
    }

    private SecurityScheme buildSecurityScheme() {
        return new SecurityScheme()
            .name("bearerAuth")
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description(
                "Token JWT firmado con HMAC-HS256. Claim requerido: `rol` "
                + "(ADMINISTRADOR | EMPRESA | OPERARIO | FONDEADOR). "
                + "Cabecera esperada: `Authorization: Bearer <token>`");
    }
}
