-- Fase 28d (P-19): copia de la V7 para los IT con esquema propio.
CREATE TABLE IF NOT EXISTS cupo_oferta_exceso (
  id BIGINT NOT NULL AUTO_INCREMENT, offer_id BIGINT NOT NULL, supplier_id BIGINT NOT NULL, consumido DECIMAL(19,2) NOT NULL,
  plantilla_nueva DECIMAL(19,2) NOT NULL, exceso DECIMAL(19,2) NOT NULL, version BIGINT NOT NULL, registrado_en DATETIME(6) NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_cupo_oferta_exceso (offer_id, supplier_id, version)
);
