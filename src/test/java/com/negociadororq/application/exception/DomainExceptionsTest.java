package com.negociadororq.application.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Domain exceptions — constructores y campos HTTP")
class DomainExceptionsTest {

    // ─── ReglaDeNegocioException ──────────────────────────────────

    @Test
    @DisplayName("ReglaDeNegocioException(mensaje) asigna 422 y código por defecto")
    void regla_unSoloArg() {
        ReglaDeNegocioException ex = new ReglaDeNegocioException("Cupo insuficiente");
        assertThat(ex.getMessage()).isEqualTo("Cupo insuficiente");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ex.getCodigo()).isEqualTo("REGLA_DE_NEGOCIO_VIOLADA");
    }

    @Test
    @DisplayName("ReglaDeNegocioException(mensaje, codigo) asigna código personalizado")
    void regla_dosArgs() {
        ReglaDeNegocioException ex = new ReglaDeNegocioException("Factura vencida", "FACTURA_VENCIDA");
        assertThat(ex.getCodigo()).isEqualTo("FACTURA_VENCIDA");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // ─── ConflictoException ───────────────────────────────────────

    @Test
    @DisplayName("ConflictoException(mensaje) asigna 409 y código por defecto")
    void conflicto_unSoloArg() {
        ConflictoException ex = new ConflictoException("Endoso duplicado");
        assertThat(ex.getMessage()).isEqualTo("Endoso duplicado");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getCodigo()).isEqualTo("CONFLICTO");
    }

    @Test
    @DisplayName("ConflictoException(mensaje, codigo) asigna código personalizado")
    void conflicto_dosArgs() {
        ConflictoException ex = new ConflictoException("Recurso duplicado", "DUPLICADO");
        assertThat(ex.getCodigo()).isEqualTo("DUPLICADO");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ─── AccionNoPermitidaException ───────────────────────────────

    @Test
    @DisplayName("AccionNoPermitidaException(mensaje) asigna 403 y código por defecto")
    void accion_unSoloArg() {
        AccionNoPermitidaException ex = new AccionNoPermitidaException("No es proveedor");
        assertThat(ex.getMessage()).isEqualTo("No es proveedor");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getCodigo()).isEqualTo("ACCION_NO_PERMITIDA");
    }

    @Test
    @DisplayName("AccionNoPermitidaException(mensaje, codigo) asigna código personalizado")
    void accion_dosArgs() {
        AccionNoPermitidaException ex = new AccionNoPermitidaException("No es pagador", "NO_ES_PAGADOR");
        assertThat(ex.getCodigo()).isEqualTo("NO_ES_PAGADOR");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}