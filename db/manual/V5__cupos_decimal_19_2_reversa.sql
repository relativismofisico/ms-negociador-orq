-- Fase 28d (J-04): reversa de V5__cupos_decimal_19_2.sql. Vuelve las columnas de cupo de
-- indirect_funder_offer_supplier_quota a DECIMAL(38,2) (el tipo anterior); ampliar no pierde dinero. Ejecutar con el
-- negociador de esta corrida detenido. La tabla cupos_respaldo_v5_negociador se conserva.
ALTER TABLE indirect_funder_offer_supplier_quota
  MODIFY COLUMN quota DECIMAL(38,2) NULL DEFAULT NULL,
  MODIFY COLUMN current_quota DECIMAL(38,2) NULL DEFAULT NULL,
  MODIFY COLUMN original_quota DECIMAL(38,2) NULL DEFAULT NULL;
