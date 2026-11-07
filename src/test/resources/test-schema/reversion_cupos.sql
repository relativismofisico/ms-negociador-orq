-- Esquema mínimo para ReversionCuposIdempotenciaIT: las columnas que mapean UserEntity, UserQuotaEntity,
-- EmpresaQuotaEntity (parametrization) y FondeadorQuotaEntity (funder). La tabla del reclamo sale de la
-- migración real (db/migration/V2__reversion_cupos_factura.sql).
CREATE TABLE IF NOT EXISTS `user` (
  ide_user      BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(100) NOT NULL,
  password      VARCHAR(255) NOT NULL,
  create_time   VARCHAR(50),
  rol_user_IDE  BIGINT,
  company_ide   BIGINT,
  state_user_id BIGINT,
  person_IDE    BIGINT,
  PRIMARY KEY (ide_user)
);

CREATE TABLE IF NOT EXISTS user_quota (
  id            BIGINT         NOT NULL AUTO_INCREMENT,
  quota         DECIMAL(19, 2) NOT NULL,
  current_quota DECIMAL(19, 2) NOT NULL,
  user_id       BIGINT         NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS parametrization (
  id            BIGINT         NOT NULL AUTO_INCREMENT,
  company_id    BIGINT         NOT NULL,
  quota         DECIMAL(19, 2) NOT NULL,
  current_quota DECIMAL(19, 2) NOT NULL,
  version       BIGINT,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS funder (
  id                         BIGINT NOT NULL AUTO_INCREMENT,
  funder_id                  BIGINT,
  supplier_id                BIGINT,
  payer_id                   BIGINT,
  payer_quota                DECIMAL(19, 2),
  supplier_quota             DECIMAL(19, 2),
  payer_current_quota        DECIMAL(19, 2),
  supplier_current_quota     DECIMAL(19, 2),
  payer_rate                 DECIMAL(19, 4),
  supplier_rate              DECIMAL(19, 4),
  estado                     VARCHAR(30),
  origen                     VARCHAR(30),
  aprobado_por_fondeador     BIT,
  activo                     BIT,
  endoso_grupal              BIT,
  rentabilidad_minima_activa BIT,
  rentabilidad_minima        DECIMAL(19, 4),
  tipo_descuento             VARCHAR(30),
  tipo_tasa                  VARCHAR(30),
  tarifa_plena               DECIMAL(19, 4),
  vigencia_descuento         VARCHAR(30),
  dias_vigencia              INT,
  porcentaje_reserva         DECIMAL(19, 4),
  version                    BIGINT,
  PRIMARY KEY (id)
);
