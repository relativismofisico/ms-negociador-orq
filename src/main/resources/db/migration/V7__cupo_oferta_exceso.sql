-- Fase 28d (P-19, decisión de John): exceso al bajar el cupo por proveedor de una oferta indirecta. Cuando la plantilla
-- nueva queda por debajo de lo que un proveedor ya consumió, su techo queda en lo consumido (disponible 0) y el exceso
-- (techo - plantilla nueva) se guarda aquí, en la misma transacción que el ajuste: proveedor, oferta, consumido,
-- plantilla nueva, exceso y versión del cambio. Una fila por (oferta, proveedor, versión): una reentrega no duplica.
-- La misma sentencia va en db/manual/V7__cupo_oferta_exceso.sql, con su reversa.
CREATE TABLE IF NOT EXISTS cupo_oferta_exceso (
  id               BIGINT        NOT NULL AUTO_INCREMENT,
  offer_id         BIGINT        NOT NULL,
  supplier_id      BIGINT        NOT NULL,
  consumido        DECIMAL(19,2) NOT NULL,
  plantilla_nueva  DECIMAL(19,2) NOT NULL,
  exceso           DECIMAL(19,2) NOT NULL,
  version          BIGINT        NOT NULL,
  registrado_en    DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cupo_oferta_exceso (offer_id, supplier_id, version),
  KEY ix_cupo_oferta_exceso_oferta (offer_id, registrado_en)
);
