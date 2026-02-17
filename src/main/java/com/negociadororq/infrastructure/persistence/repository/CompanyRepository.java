package com.negociadororq.infrastructure.persistence.repository;


import com.negociadororq.domain.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByNit(String nit); // buscar company por NIT (RUT)
}