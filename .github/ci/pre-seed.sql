-- PRE-seed del pipeline de CI: se aplica ANTES de levantar la app.
--
-- Tablas que este repo LEE pero no posee (dueñas reales: ms-registroempresa-neg)
-- -- las @Entity de este repo (CompanyEntity/CityEntity/DocumentTypeEntity/
-- CompanyOperationEntity/CompanySectionVerificationEntity/PersonEntity) mapean
-- solo un subconjunto angosto de columnas cada una, así que ddl-auto=update NO
-- alcanza a crear una tabla completa por su cuenta -- se precrean con la DDL
-- real (copiada de ms-registroempresa-neg/V1__baseline.sql), FOREIGN KEY hacia
-- tablas fuera de este pipeline aislado removidas (country/economic_activity/
-- company_state/document_type/company_contact/company_adviser no existen acá
-- y ninguna @Entity de este repo las necesita), y un par de columnas NOT NULL
-- sin default relajadas a nullable cuando el seed real no las provee
-- (document_type_ide de person) -- mismo criterio ya aplicado 2 veces esta
-- sesión en ms-consultasFacturas-neg.
--
-- Tablas que este repo SÍ posee de verdad (funder/parametrization/
-- indirect_funder_offer/indirect_funder_offer_supplier_quota/
-- payer_funding_visibility/user/user_quota/messages_templates) NO se precrean
-- acá -- sus @Entity mapean la tabla completa (confirmado leyendo cada una,
-- incluido el @Version real de funder/parametrization), así que
-- ddl-auto=update las crea bien solas al arrancar. Los datos de esas van en
-- post-seed.sql, después del boot.
--
-- Seed real (empresas KARATE-FUNDER/KARATE-DESTINO/KARATE-AJENA, persona del
-- asesor) portado del subconjunto necesario de
-- _e2e-infra/seed/ms-registroempresa-neg.sql -- ese repo no arranca en este
-- pipeline, así que se copian los datos, no se ejecuta el repo.
--
-- Datos 100% sinteticos. Idempotente (INSERT IGNORE / CREATE TABLE IF NOT EXISTS).

USE dbcompany;

-- ── person: DDL real, document_type_ide relajada a nullable ─────────────────
CREATE TABLE IF NOT EXISTS `person` (
  `IDE` bigint NOT NULL AUTO_INCREMENT,
  `firts_name` varchar(255) DEFAULT NULL,
  `second_name` varchar(255) DEFAULT NULL,
  `firts_lastname` varchar(255) DEFAULT NULL,
  `second_lastname` varchar(255) DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `genre` varchar(255) DEFAULT NULL,
  `document_number` varchar(255) DEFAULT NULL,
  `adviser` bigint DEFAULT NULL,
  `document_type_ide` bigint DEFAULT NULL,
  `country_code` bigint DEFAULT NULL,
  PRIMARY KEY (`IDE`),
  UNIQUE KEY `document_number_UNIQUE` (`document_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

-- ── company_adviser: stub mínimo (sin @Entity propia en este repo) ──────────
CREATE TABLE IF NOT EXISTS `company_adviser` (
  `ide` bigint NOT NULL PRIMARY KEY,
  `person_IDE` bigint DEFAULT NULL
);

-- ── company: DDL real completa, FOREIGN KEY removidas ────────────────────────
CREATE TABLE IF NOT EXISTS `company` (
  `ide_company` bigint NOT NULL AUTO_INCREMENT,
  `name_company` varchar(255) DEFAULT NULL,
  `nit` varchar(255) DEFAULT NULL,
  `address_company` varchar(255) DEFAULT NULL,
  `phone_company` varchar(255) DEFAULT NULL,
  `email_company` varchar(255) DEFAULT NULL,
  `company_represent_ide` bigint DEFAULT NULL,
  `company_state_ide` bigint DEFAULT NULL,
  `document_type_ide` bigint DEFAULT NULL,
  `company_contact_ide` bigint DEFAULT NULL,
  `country_code` bigint DEFAULT NULL,
  `city_code` bigint DEFAULT NULL,
  `economic_activity_id` bigint DEFAULT NULL,
  `economic_sector_id` bigint DEFAULT NULL,
  `tax_information_id` bigint DEFAULT NULL,
  `iva_regime_id` bigint DEFAULT NULL,
  `company_adviser_ide` bigint DEFAULT NULL,
  `nombre_compania` varchar(255) DEFAULT NULL,
  `users_amount` int DEFAULT NULL,
  `signature_resume_code` varchar(255) DEFAULT NULL,
  `registration_resume_code` varchar(255) DEFAULT NULL,
  `registration_resume_code_state` bigint DEFAULT NULL,
  `operario_access_enabled` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`ide_company`),
  UNIQUE KEY `ide_company_UNIQUE` (`ide_company`),
  UNIQUE KEY `name_companycol_UNIQUE` (`name_company`),
  UNIQUE KEY `nit_UNIQUE` (`nit`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

-- ── city: DDL real, FOREIGN KEY removida ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS `city` (
  `id` bigint NOT NULL,
  `city_name` varchar(255) DEFAULT NULL,
  `country_code` char(3) DEFAULT NULL,
  `state` varchar(255) DEFAULT NULL,
  `country_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

-- ── document_type: DDL real completa ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `document_type` (
  `ide_document_type` bigint NOT NULL AUTO_INCREMENT,
  `description` varchar(255) DEFAULT NULL,
  `document_type_name` varchar(255) DEFAULT NULL,
  `kind_document` int DEFAULT NULL,
  PRIMARY KEY (`ide_document_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

-- ── company_operation: DDL real, FOREIGN KEY removida ────────────────────────
CREATE TABLE IF NOT EXISTS `company_operation` (
  `ide_company_operation` bigint NOT NULL AUTO_INCREMENT,
  `enabled` bit(1) NOT NULL DEFAULT b'0',
  `operation_type` varchar(255) DEFAULT NULL,
  `company_id` bigint DEFAULT NULL,
  `selected` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`ide_company_operation`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

-- ── company_section_verification: DDL real, FOREIGN KEY removida ────────────
-- (usada por el gate VERIFICACION_BASE_DATOS del ítem 33 -- este repo solo la
-- lee de forma incidental, ningún feature de Karate depende de sembrar filas
-- acá; se precrea igual por si ddl-auto llega a tocarla).
CREATE TABLE IF NOT EXISTS `company_section_verification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `section_key` varchar(255) DEFAULT NULL,
  `verified_at` datetime(6) DEFAULT NULL,
  `verified_by` varchar(255) DEFAULT NULL,
  `company_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_company_section` (`company_id`,`section_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

-- ── user: DDL minima -- UserEntity de este repo mapea la tabla entera, pero
-- password es NOT NULL sin default y el seed no necesita autenticar con ella
-- (JWT firmado localmente por Karate) -- se deja NOT NULL con un placeholder
-- en cada INSERT de mas abajo, para que ddl-auto=update no tenga que decidir.
CREATE TABLE IF NOT EXISTS `user` (
  `ide_user` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL DEFAULT 'no-auth',
  `create_time` datetime(6) DEFAULT NULL,
  `rol_user_IDE` bigint DEFAULT NULL,
  `company_ide` bigint DEFAULT NULL,
  `state_user_id` bigint DEFAULT NULL,
  `person_IDE` bigint DEFAULT NULL,
  PRIMARY KEY (`ide_user`),
  UNIQUE KEY `username_UNIQUE` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

-- ═══════════════════════════════════════════════════════════════════════════
-- Seed real: subconjunto necesario de _e2e-infra/seed/ms-registroempresa-neg.sql
-- (empresas 7003/7004/7005 + usuarios + persona 8000 del asesor).
-- ═══════════════════════════════════════════════════════════════════════════

-- 7006 (KARATE-PAYERB) agregada además del trío original: la usa
-- consultar-supplier-quota.feature como proveedor de prueba de la oferta
-- indirecta 7360 (ver post-seed.sql).
INSERT IGNORE INTO company (ide_company, name_company, nit, phone_company, email_company, operario_access_enabled) VALUES
  (7003,'Karate Fondeador','KARATE-FUNDER','3000000010','f@k.test',0),
  (7004,'Karate Destino','KARATE-DESTINO','3000000011','d@k.test',0),
  (7005,'Karate Ajena','KARATE-AJENA','3000000012','x@k.test',0),
  (7006,'Karate Pagadora B','KARATE-PAYERB','3000000013','pb@k.test',0);

INSERT IGNORE INTO user (ide_user, username, password, create_time, company_ide, person_IDE, rol_user_IDE, state_user_id) VALUES
  (7203,'KARATE-FUNDER','no-auth',NOW(6),7003,NULL,2,1),
  (7204,'KARATE-DESTINO','no-auth',NOW(6),7004,NULL,2,1),
  (7205,'KARATE-AJENA','no-auth',NOW(6),7005,NULL,2,1),
  (7206,'KARATE-PAYERB','no-auth',NOW(6),7006,NULL,2,1);

INSERT IGNORE INTO person (IDE, firts_name, firts_lastname, phone, email, document_number) VALUES
  (8000,'Asesor','Karate','3009000000','asesor@k.test','DOC-ASESOR-8000');
INSERT IGNORE INTO company_adviser (ide, person_IDE) VALUES (8000, 8000);
INSERT IGNORE INTO user (ide_user, username, password, create_time, company_ide, person_IDE, rol_user_IDE, state_user_id) VALUES
  (8000,'DOC-ASESOR-8000','no-auth',NOW(6),NULL,8000,1,1);
UPDATE company SET company_adviser_ide = 8000 WHERE ide_company = 7004;
