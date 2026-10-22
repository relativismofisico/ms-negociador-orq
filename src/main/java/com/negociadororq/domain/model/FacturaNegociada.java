package com.negociadororq.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.enums.EstadoNegociacion;
import com.negociadororq.domain.util.Sensible;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;


/**
 * Estado de negociación de una factura puntual dentro de una {@link Negociacion}: en
 * qué punto va su aprobación, su endoso y qué fondeador(es) tiene asociados.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacturaNegociada {

    private String numeroFactura;
    private BigDecimal valorFactura;


    private EstadoNegociacion estadoNegociacion;
    private EstadoFirma estadoFirma;

    private String rutPagador;
    private String rutProveedor;
    // Cada factura puede tener uno o varios fondeadores asociados
    private List<FondeadorAsignacion> fondeadores;

    // Historial de aprobaciones (puede haber varias, según los roles que participen)
    private List<AprobacionActorDto> aprobaciones;
    //private EstadoFacturaNegociada estado;
    private EstadoEndoso estadoEndoso;
    private Boolean estadoCreacionEndoso;

    @ToString.Exclude
    private String otpEndoso;

    private LocalDateTime fechaOtp;

    /** En el toString() el valor sale como {@code ***}: nunca en logs. Lo invoca el toString() que genera Lombok. */
    @ToString.Include(name = "otpEndoso")
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private String otpEndosoParaLog() {
        return Sensible.ocultar(otpEndoso);
    }
}

