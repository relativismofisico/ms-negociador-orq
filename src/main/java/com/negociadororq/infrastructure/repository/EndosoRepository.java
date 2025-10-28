package com.negociadororq.infrastructure.repository;

import com.negociadororq.domain.model.Endoso;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface EndosoRepository extends MongoRepository<Endoso, String> {

    List<Endoso> findByNegociacionIdIdAndFacturaId(String negociacionId, String facturaId);

    Optional<Endoso> findByNumeroFactura(String numeroFactura);

}
