package com.negociadororq.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import com.negociadororq.infrastructure.persistence.entity.FacturaDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data MongoDB de {@link FacturaDocument}.
 */
@Repository
public interface FacturaRepository extends MongoRepository<FacturaDocument, String> {

    /**
     * {@code findFirst ... OrderByIdDesc} y no {@code findBy...}: {@code numeroFactura}
     * (//cbc:ID del XML DIAN) es el consecutivo del EMISOR, NO global — dos proveedores
     * distintos emiten legítimamente su factura {@code 1}. El {@code @Indexed(unique=true)}
     * incorrecto se quitó de este campo (674d6f7), así que la colisión quedó alcanzable:
     * un {@code findBy...} que devuelve {@code Optional} degradaría a
     * {@code IncorrectResultSizeDataAccessException} → 500 en el loop de creación de endoso
     * y en el resto del flujo de negociación/reversión. Mismo backstop que
     * ms-consultasFacturas-neg (1dfae0d). La clave única global real es {@code cufe}.
     *
     * @param numeroFactura número de la factura
     * @return la factura más reciente con ese número, si existe
     */
    Optional<FacturaDocument> findFirstByNumeroFacturaOrderByIdDesc(String numeroFactura);

    /**
     * @param cufe CUFE de la factura
     * @return la factura, si existe
     */
    Optional<FacturaDocument> findByCufe(String cufe);

    /**
     * @param rutPagador   RUT del pagador
     * @param rutProveedor RUT del proveedor
     * @return la primera factura que coincida con alguno de los dos RUTs
     */
    Optional<FacturaDocument> findFirstByRutPagadorOrRutProveedor(String rutPagador, String rutProveedor);

    /**
     * @param numerosFactura números de factura a verificar
     * @param rutPagador     RUT del pagador
     * @return true si alguna de esas facturas pertenece a ese pagador
     */
    boolean existsByNumeroFacturaInAndRutPagador(List<String> numerosFactura, String rutPagador);

    /**
     * @param numerosFactura números de factura a verificar
     * @param rutProveedor   RUT del proveedor
     * @return true si alguna de esas facturas pertenece a ese proveedor
     */
    boolean existsByNumeroFacturaInAndRutProveedor(List<String> numerosFactura, String rutProveedor);

    /**
     * @param numerosFactura números de factura a verificar
     * @param rutFondeador   RUT del fondeador
     * @return true si alguna de esas facturas tiene asignado ese fondeador
     */
    @Query("{ 'numeroFactura': { $in: ?0 }, 'fondeadores.rutFondeador': ?1 }")
    boolean existsByNumeroFacturaInAndFondeadoresRutFondeador(List<String> numerosFactura, String rutFondeador);

    /**
     * @param numerosFactura números de factura a buscar
     * @return las facturas encontradas para esos números
     */
    List<FacturaDocument> findByNumeroFacturaIn(List<String> numerosFactura);

    /**
     * @return las facturas negociadas pendientes de evaluar vencimiento (no LIQUIDADA, VENCIDA ni RECHAZADA)
     */
    @Query("{ 'estadoNegociacion': { $nin: ['LIQUIDADA', 'VENCIDA', 'RECHAZADA'] } }")
    List<FacturaDocument> findFacturasPendientesVencimiento();
}

