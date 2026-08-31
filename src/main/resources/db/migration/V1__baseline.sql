-- Fase 13 (pipeline de Mantenibilidad) — baseline inicial de Flyway.
--
-- ms-negociador-orq solo posee UNA tabla MySQL propia (el resto de sus
-- entidades JPA sobre MySQL son copias de solo lectura de tablas ajenas —
-- company, person, user, funder, indirect_funder_offer,
-- payer_funding_visibility, message_templates, etc. — no se migran desde
-- acá). Además de MySQL, este repo usa MongoDB para negociaciones/
-- facturas/endosos/OTP/outbox, fuera del alcance de Flyway.
--
-- Generado con `SHOW CREATE TABLE` contra la RDS de dev el 2026-08-30.
-- Requiere que `indirect_funder_offer` (ms-registroempresa-neg) ya exista.
--
-- No se ejecuta contra la base de dev existente: se aplica
-- `spring.flyway.baseline-on-migrate=true` + `baseline-version=1`.

CREATE TABLE `indirect_funder_offer_supplier_quota` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `offer_id` bigint NOT NULL,
  `supplier_id` bigint NOT NULL,
  `rate` decimal(38,2) DEFAULT NULL,
  `quota` decimal(38,2) DEFAULT NULL,
  `current_quota` decimal(38,2) DEFAULT NULL,
  `original_quota` decimal(38,2) DEFAULT NULL,
  `creado_en` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_indirect_supplier_quota` (`offer_id`,`supplier_id`),
  CONSTRAINT `fk_indirect_supplier_quota_offer` FOREIGN KEY (`offer_id`) REFERENCES `indirect_funder_offer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
