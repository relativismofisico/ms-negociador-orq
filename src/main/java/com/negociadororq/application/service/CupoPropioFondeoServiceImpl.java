package com.negociadororq.application.service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import com.negociadororq.application.dto.CupoPropioFondeoDto;
import com.negociadororq.application.exception.ConflictoException;
import com.negociadororq.application.exception.PeticionInvalidaException;
import com.negociadororq.application.exception.RecursoNoEncontradoException;
import com.negociadororq.application.exception.ReglaDeNegocioException;
import com.negociadororq.application.port.output.EmpresaQuotaOutputPort;
import com.negociadororq.application.port.output.FondeadorQuotaOutputPort;
import com.negociadororq.domain.model.EmpresaQuota;
import com.negociadororq.domain.model.FondeadorQuota;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Implementación de {@link CupoPropioFondeoService}.
 */
@Service
@RequiredArgsConstructor
public class CupoPropioFondeoServiceImpl implements CupoPropioFondeoService {

    private static final String TIPO_DESCUENTO_FIJO = "FIJO";
    private static final String TIPO_DESCUENTO_DINAMICO = "DINAMICO";
    private static final Set<String> TIPOS_DESCUENTO_VALIDOS =
            Set.of(TIPO_DESCUENTO_FIJO, "FINANCIERO", TIPO_DESCUENTO_DINAMICO);
    private static final Set<String> TIPOS_TASA_VALIDOS =
            Set.of("TASA_EFECTIVA_ANUAL", "TASA_MES_VENCIDO", "TASA_MES_ANTICIPADO");

    private static final String VIGENCIA_TODO_EL_PLAZO = "TODO_EL_PLAZO";
    private static final Set<String> VIGENCIAS_VALIDAS =
            Set.of(VIGENCIA_TODO_EL_PLAZO, "PRIMEROS_DIAS", "DIAS_ANTES_VENCIMIENTO");

    private final FondeadorQuotaOutputPort fondeadorQuotaPort;
    private final EmpresaQuotaOutputPort empresaQuotaPort;

    @Override
    public CupoPropioFondeoDto obtenerCupoPropio(Long companyId) {
        FondeadorQuota autofondeo = buscarAutofondeo(companyId);
        EmpresaQuota empresaQuota = buscarCupoAdministrador(companyId);

        return toDto(companyId, autofondeo, empresaQuota);
    }

    @Override
    @SuppressWarnings("checkstyle:ParameterNumber")
    public CupoPropioFondeoDto actualizarCupoPropio(Long companyId, BigDecimal nuevoCupo, BigDecimal nuevaTasa,
            String tipoDescuento, String tipoTasa, BigDecimal tarifaPlena,
            String vigenciaDescuento, Integer diasVigencia, BigDecimal porcentajeReserva) {
        FondeadorQuota autofondeo = buscarAutofondeo(companyId);
        EmpresaQuota empresaQuota = buscarCupoAdministrador(companyId);

        if (nuevoCupo.compareTo(empresaQuota.getQuota()) > 0) {
            throw new ReglaDeNegocioException(
                    "El cupo propio de fondeo no puede exceder el cupo asignado por el administrador",
                    "CUPO_PROPIO_EXCEDE_CUPO_ADMINISTRADOR");
        }

        String tipoTasaValidado = validarTipoDescuentoYTasa(tipoDescuento, tipoTasa);
        BigDecimal tarifaPlenaValidada = validarTarifaPlena(tipoDescuento, tarifaPlena);
        Integer diasVigenciaValidados = validarVigenciaDescuento(vigenciaDescuento, diasVigencia);

        // Preserva el monto ya consumido: el disponible se recalcula sobre el nuevo
        // cupo en vez de sumarle la diferencia, para no regalar ni penalizar cupo
        // por el simple hecho de editar el límite.
        BigDecimal cupoAnterior = Optional.ofNullable(autofondeo.getPayerQuota()).orElse(BigDecimal.ZERO);
        BigDecimal disponibleAnterior = Optional.ofNullable(autofondeo.getPayerCurrentQuota()).orElse(BigDecimal.ZERO);
        BigDecimal consumido = cupoAnterior.subtract(disponibleAnterior).max(BigDecimal.ZERO);

        BigDecimal nuevoDisponible = nuevoCupo.subtract(consumido).max(BigDecimal.ZERO);

        autofondeo.setPayerQuota(nuevoCupo);
        autofondeo.setPayerCurrentQuota(nuevoDisponible);
        autofondeo.setPayerRate(nuevaTasa);
        autofondeo.setTipoDescuento(tipoDescuento);
        autofondeo.setTipoTasa(tipoTasaValidado);
        autofondeo.setTarifaPlena(tarifaPlenaValidada);
        autofondeo.setVigenciaDescuento(vigenciaDescuento);
        autofondeo.setDiasVigencia(diasVigenciaValidados);
        autofondeo.setPorcentajeReserva(porcentajeReserva);

        FondeadorQuota guardado = guardarConVersionado(autofondeo, companyId);

        return toDto(companyId, guardado, empresaQuota);
    }

    /**
     * tipoDescuento es siempre obligatorio. tipoTasa es obligatorio salvo que
     * tipoDescuento sea FIJO (caso en el que se ignora/limpia, ya que Fijo se
     * muestra como "Tarifa Plena" sin tipo de tasa asociado).
     *
     * @return el tipoTasa a persistir (null si tipoDescuento es FIJO)
     */
    private String validarTipoDescuentoYTasa(String tipoDescuento, String tipoTasa) {
        if (tipoDescuento == null || !TIPOS_DESCUENTO_VALIDOS.contains(tipoDescuento)) {
            throw new PeticionInvalidaException(
                    "tipoDescuento debe ser alguno de: " + TIPOS_DESCUENTO_VALIDOS, "TIPO_DESCUENTO_INVALIDO");
        }

        if (TIPO_DESCUENTO_FIJO.equals(tipoDescuento)) {
            return null;
        }

        if (tipoTasa == null) {
            throw new ReglaDeNegocioException(
                    "Debe indicar el tipo de tasa cuando el tipo de descuento es " + tipoDescuento,
                    "TIPO_TASA_REQUERIDO");
        }

        if (!TIPOS_TASA_VALIDOS.contains(tipoTasa)) {
            throw new PeticionInvalidaException(
                    "tipoTasa debe ser alguno de: " + TIPOS_TASA_VALIDOS, "TIPO_TASA_INVALIDO");
        }

        return tipoTasa;
    }

    /**
     * tarifaPlena solo aplica (y es obligatoria) cuando tipoDescuento es
     * DINAMICO: ahí coexiste con tasa (a diferencia de FIJO, donde tasa se
     * muestra como "Tarifa Plena" y este campo no aplica). Se ignora/limpia
     * en cualquier otro caso, aunque venga informada.
     *
     * @return la tarifaPlena a persistir (null salvo que tipoDescuento sea DINAMICO)
     */
    private BigDecimal validarTarifaPlena(String tipoDescuento, BigDecimal tarifaPlena) {
        if (!TIPO_DESCUENTO_DINAMICO.equals(tipoDescuento)) {
            return null;
        }

        if (tarifaPlena == null) {
            throw new ReglaDeNegocioException(
                    "Debe indicar la tarifa plena cuando el tipo de descuento es DINAMICO",
                    "TARIFA_PLENA_REQUERIDA");
        }

        return tarifaPlena;
    }

    /**
     * vigenciaDescuento es siempre obligatoria. diasVigencia es obligatorio
     * (entero no negativo) salvo que vigenciaDescuento sea TODO_EL_PLAZO
     * (caso en el que se ignora/limpia, aunque venga informado).
     *
     * @return los diasVigencia a persistir (null si vigenciaDescuento es TODO_EL_PLAZO)
     */
    private Integer validarVigenciaDescuento(String vigenciaDescuento, Integer diasVigencia) {
        if (vigenciaDescuento == null || !VIGENCIAS_VALIDAS.contains(vigenciaDescuento)) {
            throw new PeticionInvalidaException(
                    "vigenciaDescuento debe ser alguno de: " + VIGENCIAS_VALIDAS, "VIGENCIA_DESCUENTO_INVALIDA");
        }

        if (VIGENCIA_TODO_EL_PLAZO.equals(vigenciaDescuento)) {
            return null;
        }

        if (diasVigencia == null) {
            throw new ReglaDeNegocioException(
                    "Debe indicar los días de vigencia cuando la vigencia del descuento es " + vigenciaDescuento,
                    "DIAS_VIGENCIA_REQUERIDOS");
        }

        return diasVigencia;
    }

    @Override
    public CupoPropioFondeoDto actualizarEndosoGrupal(Long companyId, boolean endosoGrupal) {
        FondeadorQuota autofondeo = buscarAutofondeo(companyId);
        EmpresaQuota empresaQuota = buscarCupoAdministrador(companyId);

        autofondeo.setEndosoGrupal(endosoGrupal);
        FondeadorQuota guardado = guardarConVersionado(autofondeo, companyId);

        return toDto(companyId, guardado, empresaQuota);
    }

    @Override
    public CupoPropioFondeoDto actualizarRentabilidadMinima(Long companyId, boolean activa, BigDecimal valor) {
        FondeadorQuota autofondeo = buscarAutofondeo(companyId);
        EmpresaQuota empresaQuota = buscarCupoAdministrador(companyId);

        if (activa && valor == null) {
            throw new ReglaDeNegocioException(
                    "Debe indicar el valor de rentabilidad mínima esperada (tasa Efectivo Anual)",
                    "RENTABILIDAD_MINIMA_SIN_VALOR");
        }

        autofondeo.setRentabilidadMinimaActiva(activa);
        autofondeo.setRentabilidadMinima(valor);
        FondeadorQuota guardado = guardarConVersionado(autofondeo, companyId);

        return toDto(companyId, guardado, empresaQuota);
    }

    @Override
    public CupoPropioFondeoDto restablecerCupo(Long companyId) {
        FondeadorQuota autofondeo = buscarAutofondeo(companyId);
        EmpresaQuota empresaQuota = buscarCupoAdministrador(companyId);

        autofondeo.setPayerCurrentQuota(Optional.ofNullable(autofondeo.getPayerQuota()).orElse(BigDecimal.ZERO));
        FondeadorQuota guardado = guardarConVersionado(autofondeo, companyId);

        return toDto(companyId, guardado, empresaQuota);
    }

    /**
     * Guarda la configuración de autofondeo forzando flush inmediato (para que un
     * conflicto de {@code @Version} se detecte acá, no al hacer commit de la
     * transacción) y lo traduce a un 409 — dos ediciones concurrentes de la misma
     * configuración (ej. dos pestañas de administrador) deben poder reintentar, no
     * recibir un 500 genérico.
     */
    private FondeadorQuota guardarConVersionado(FondeadorQuota autofondeo, Long companyId) {
        try {
            return fondeadorQuotaPort.saveAndFlush(autofondeo);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictoException(
                    "La configuración de autofondeo de la empresa " + companyId
                            + " fue modificada por otra petición concurrente. Vuelve a intentar.",
                    "CUPO_PROPIO_CONCURRENCIA");
        }
    }

    private FondeadorQuota buscarAutofondeo(Long companyId) {
        return fondeadorQuotaPort.findByFunderIdAndPayerId(companyId, companyId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La empresa " + companyId
                                + " no está habilitada como fondeadora de sí misma para Confirming"));
    }

    private EmpresaQuota buscarCupoAdministrador(Long companyId) {
        return empresaQuotaPort.findByCompanyId(companyId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El administrador aún no ha asignado un cupo general a la empresa " + companyId));
    }

    private CupoPropioFondeoDto toDto(Long companyId, FondeadorQuota autofondeo, EmpresaQuota empresaQuota) {
        return CupoPropioFondeoDto.builder()
                .companyId(companyId)
                .cupoPropio(Optional.ofNullable(autofondeo.getPayerQuota()).orElse(BigDecimal.ZERO))
                .cupoDisponible(Optional.ofNullable(autofondeo.getPayerCurrentQuota()).orElse(BigDecimal.ZERO))
                .cupoMaximoAdministrador(empresaQuota.getQuota())
                .tasa(autofondeo.getPayerRate())
                .endosoGrupal(Boolean.TRUE.equals(autofondeo.getEndosoGrupal()))
                .rentabilidadMinimaActiva(Boolean.TRUE.equals(autofondeo.getRentabilidadMinimaActiva()))
                .rentabilidadMinima(autofondeo.getRentabilidadMinima())
                .tipoDescuento(autofondeo.getTipoDescuento())
                .tipoTasa(autofondeo.getTipoTasa())
                .tarifaPlena(autofondeo.getTarifaPlena())
                .vigenciaDescuento(autofondeo.getVigenciaDescuento())
                .diasVigencia(autofondeo.getDiasVigencia())
                .porcentajeReserva(autofondeo.getPorcentajeReserva())
                .build();
    }
}
