package com.negociadororq.infrastructure.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.negociadororq.domain.enums.EstadoEndoso;
import com.negociadororq.domain.enums.EstadoFirma;
import com.negociadororq.domain.enums.EstadoNegociacion;
import com.negociadororq.domain.model.AprobacionActorDto;
import com.negociadororq.domain.model.FondeadorAsignacion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Forma de persistencia embebida de
 * {@link com.negociadororq.domain.model.FacturaNegociada} dentro de
 * {@link NegociacionDocument}. Mismos campos que el modelo de dominio, pero como tipo
 * propio de infraestructura.
 *
 * <p>Los campos {@code fondeadores}/{@code aprobaciones} siguen tipados con
 * {@link FondeadorAsignacion}/{@link AprobacionActorDto} del dominio (no se duplican) —
 * son value objects sin ninguna anotación de framework, y este nivel de anidamiento no
 * forma parte del hallazgo real que motivó esta clase (que era específicamente que
 * {@code NegociacionDocument} usaba {@code FacturaNegociada}/{@code Operacion} directo).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacturaNegociadaEmbedded {

    private String numeroFactura;
    private BigDecimal valorFactura;

    private EstadoNegociacion estadoNegociacion;
    private EstadoFirma estadoFirma;

    private String rutPagador;
    private String rutProveedor;
    private List<FondeadorAsignacion> fondeadores;

    private List<AprobacionActorDto> aprobaciones;
    private EstadoEndoso estadoEndoso;
    private Boolean estadoCreacionEndoso;

    private String otpEndoso;

    private LocalDateTime fechaOtp;
}
