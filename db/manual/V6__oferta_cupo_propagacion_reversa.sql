-- Fase 28d (P-19): reversa de V6__oferta_cupo_propagacion.sql. Los techos ya ajustados por P-19 se quedan como están
-- (es dinero aplicado); solo se quitan las columnas de control. Ejecutar con el negociador de esta corrida detenido.
ALTER TABLE indirect_funder_offer_supplier_quota DROP COLUMN oferta_cupo_plantilla, DROP COLUMN oferta_cupo_version;
