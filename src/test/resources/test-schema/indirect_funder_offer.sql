-- Fase 28d (P-19): oferta indirecta mínima (de ms-registroempresa-neg) para los IT del negociador: la fila del proveedor
-- nace con la plantilla y la versión del cupo de la oferta (INSERT ... SELECT con bloqueo compartido).
CREATE TABLE IF NOT EXISTS `indirect_funder_offer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `funder_id` bigint NOT NULL DEFAULT 0,
  `payer_id` bigint NOT NULL DEFAULT 0,
  `supplier_quota_template` decimal(19,2) DEFAULT NULL,
  `cupo_version` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
