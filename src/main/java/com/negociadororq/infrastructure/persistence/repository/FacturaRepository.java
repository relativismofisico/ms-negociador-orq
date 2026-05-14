package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.domain.model.Factura;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;
import org.springframework.data.mongodb.repository.Query;



@Repository
public interface FacturaRepository extends MongoRepository<Factura, String> {

    /**
     * Busca una factura por el número de factura
     */
    Optional<Factura> findByNumeroFactura(String numeroFactura);

    /**
     * Busca una factura por CUFE
     */
    Optional<Factura> findByCufe(String cufe);

    Optional<Factura> findFirstByRutPagadorOrRutProveedor(String rutPagador, String rutProveedor);

    boolean existsByNumeroFacturaInAndRutPagador(List<String> numerosFactura, String rutPagador);

    boolean existsByNumeroFacturaInAndRutProveedor(List<String> numerosFactura, String rutProveedor);

    @Query("{ 'numeroFactura': { $in: ?0 }, 'fondeadores.rutFondeador': ?1 }")
    boolean existsByNumeroFacturaInAndFondeadoresRutFondeador(List<String> numerosFactura, String rutFondeador);

    /**
     * 🔍 Busca todas las facturas cuyos números estén en la lista dada
     */
    List<Factura> findByNumeroFacturaIn(List<String> numerosFactura);

    @Query("{ 'estadoNegociacion': { $nin: ['LIQUIDADA', 'VENCIDA', 'RECHAZADA'] } }")
    List<Factura> findFacturasPendientesVencimiento();

}
