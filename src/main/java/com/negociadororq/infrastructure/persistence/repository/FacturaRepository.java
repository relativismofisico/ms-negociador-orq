package com.negociadororq.infrastructure.persistence.repository;

import com.negociadororq.infrastructure.persistence.entity.FacturaDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FacturaRepository extends MongoRepository<FacturaDocument, String> {

    Optional<FacturaDocument> findByNumeroFactura(String numeroFactura);

    Optional<FacturaDocument> findByCufe(String cufe);

    Optional<FacturaDocument> findFirstByRutPagadorOrRutProveedor(String rutPagador, String rutProveedor);

    boolean existsByNumeroFacturaInAndRutPagador(List<String> numerosFactura, String rutPagador);

    boolean existsByNumeroFacturaInAndRutProveedor(List<String> numerosFactura, String rutProveedor);

    @Query("{ 'numeroFactura': { $in: ?0 }, 'fondeadores.rutFondeador': ?1 }")
    boolean existsByNumeroFacturaInAndFondeadoresRutFondeador(List<String> numerosFactura, String rutFondeador);

    List<FacturaDocument> findByNumeroFacturaIn(List<String> numerosFactura);

    @Query("{ 'estadoNegociacion': { $nin: ['LIQUIDADA', 'VENCIDA', 'RECHAZADA'] } }")
    List<FacturaDocument> findFacturasPendientesVencimiento();
}

