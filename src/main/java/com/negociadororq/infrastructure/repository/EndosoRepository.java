package com.negociadororq.infrastructure.repository;

import com.negociadororq.domain.model.Endoso;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface EndosoRepository extends MongoRepository<Endoso, String> {

    // 🔹 Endoso de UNA factura dentro de una negociación
    Optional<Endoso> findByNegociacionIdAndFacturaId(
            String negociacionId,
            String facturaId
    );

    // 🔹 Todos los endosos de una negociación
    List<Endoso> findByNegociacionId(String negociacionId);

    // 🔹 Todos los endosos de una factura (histórico / auditoría)
    List<Endoso> findByFacturaId(String facturaId);


    boolean existsByNegociacionIdAndFacturaId(
            String negociacionId,
            String facturaId
    );
}
