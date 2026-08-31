package com.negociadororq.application.port.output;

import java.util.List;
import java.util.Optional;

import com.negociadororq.domain.model.Factura;

/**
 * Puerto de persistencia de facturas.
 */
public interface FacturaOutputPort {

    /**
     * @param numeroFactura número de la factura
     * @return la factura, si existe
     */
    Optional<Factura> findByNumeroFactura(String numeroFactura);

    /**
     * @param numerosFactura números de factura a buscar
     * @return las facturas encontradas para esos números
     */
    List<Factura> findByNumeroFacturaIn(List<String> numerosFactura);

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
     * @return las facturas negociadas pendientes de evaluar vencimiento
     */
    List<Factura> findFacturasPendientesVencimiento();

    /**
     * @param factura factura a persistir
     * @return la factura persistida
     */
    Factura save(Factura factura);

    /**
     * @param facturas facturas a persistir
     * @return las facturas persistidas
     */
    List<Factura> saveAll(List<Factura> facturas);
}
