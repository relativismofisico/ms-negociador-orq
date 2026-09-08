-- Esquema mínimo para el test de concurrencia de descontarCupo
-- (IndirectFunderOfferSupplierQuotaConcurrencyIT). Reproduce el UNIQUE real de
-- V1__baseline.sql — la FK a indirect_funder_offer se omite a propósito para
-- mantener el test autocontenido; la carrera no depende de la FK.
CREATE TABLE IF NOT EXISTS `indirect_funder_offer_supplier_quota` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `offer_id` bigint NOT NULL,
  `supplier_id` bigint NOT NULL,
  `rate` decimal(38,2) DEFAULT NULL,
  `quota` decimal(38,2) DEFAULT NULL,
  `current_quota` decimal(38,2) DEFAULT NULL,
  `original_quota` decimal(38,2) DEFAULT NULL,
  `creado_en` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_indirect_supplier_quota` (`offer_id`,`supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
