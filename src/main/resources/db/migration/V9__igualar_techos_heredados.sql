-- Fase 28d (P-19 / P-24, decisión de John 2026-11-24): igualar los techos heredados de los proveedores de cada oferta
-- indirecta a la plantilla VIGENTE de su oferta (supplier_quota_template), con piso en lo consumido. Antes de P-19 un
-- proveedor tomaba la plantilla de su primera negociación y no la seguía; no existe ningún camino que le asigne a un
-- proveedor puntual un cupo distinto de la plantilla (solo crear desde la plantilla, recargar al techo y P-19), así que
-- todas las filas se igualan. Paquete de migración de cupos; la misma sentencia va en db/manual con su reversa.
-- Pasos:
--   1. respaldo de cada fila (cupos_respaldo_v9_negociador, con la hora de esta corrida);
--   2. consumido = techo - disponible, calculado UNA vez (tabla temporal) antes de escribir;
--   3. techo = MAX(plantilla, consumido), disponible = techo - consumido (nunca negativo), quota = techo, y la fila queda
--      alineada con la plantilla y la versión de la oferta (P-19);
--   4. exceso (consumido > plantilla) en cupo_oferta_exceso (V7), igual que P-19; una vez por (oferta, proveedor, versión);
--   5. verificación: 0 <= disponible <= techo en todas las filas; si no, SIGNAL.
-- Idempotente: una segunda corrida no cambia ningún valor. Requiere la V6 y la V7.
CREATE TABLE IF NOT EXISTS cupos_respaldo_v9_negociador (
  corrida         DATETIME(6)   NOT NULL,
  id              BIGINT        NOT NULL,
  offer_id        BIGINT        NOT NULL,
  supplier_id     BIGINT        NOT NULL,
  quota           DECIMAL(19,2) NULL,
  current_quota   DECIMAL(19,2) NULL,
  original_quota  DECIMAL(19,2) NULL,
  plantilla       DECIMAL(19,2) NULL,
  version         BIGINT        NULL,
  PRIMARY KEY (corrida, id)
);

DROP PROCEDURE IF EXISTS v9n_igualar_techos;

DELIMITER $$
CREATE PROCEDURE v9n_igualar_techos()
BEGIN
  DECLARE v_corrida DATETIME(6) DEFAULT NOW(6);
  DECLARE v_n INT DEFAULT 0;
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE()
             AND table_name = 'indirect_funder_offer') THEN
    -- 1. respaldo
    INSERT INTO cupos_respaldo_v9_negociador (corrida, id, offer_id, supplier_id, quota, current_quota, original_quota,
                                              plantilla, version)
    SELECT v_corrida, id, offer_id, supplier_id, quota, current_quota, original_quota, oferta_cupo_plantilla,
           oferta_cupo_version FROM indirect_funder_offer_supplier_quota;

    -- 2. consumido antes de escribir
    DROP TEMPORARY TABLE IF EXISTS v9n_consumo;
    CREATE TEMPORARY TABLE v9n_consumo (id BIGINT PRIMARY KEY, consumido DECIMAL(19,2) NOT NULL)
      SELECT id, GREATEST(COALESCE(original_quota, 0) - COALESCE(current_quota, 0), 0) AS consumido
        FROM indirect_funder_offer_supplier_quota;

    -- 3. igualar al techo de la plantilla vigente, con piso en lo consumido
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
               AND table_name = 'indirect_funder_offer' AND column_name = 'cupo_version') THEN
      UPDATE indirect_funder_offer_supplier_quota q
        JOIN v9n_consumo c ON c.id = q.id
        JOIN indirect_funder_offer o ON o.id = q.offer_id
         SET q.original_quota = GREATEST(o.supplier_quota_template, c.consumido),
             q.current_quota = GREATEST(o.supplier_quota_template, c.consumido) - c.consumido,
             q.quota = GREATEST(o.supplier_quota_template, c.consumido),
             q.oferta_cupo_plantilla = o.supplier_quota_template,
             q.oferta_cupo_version = GREATEST(q.oferta_cupo_version, o.cupo_version)
       WHERE o.supplier_quota_template IS NOT NULL;
    ELSE
      UPDATE indirect_funder_offer_supplier_quota q
        JOIN v9n_consumo c ON c.id = q.id
        JOIN indirect_funder_offer o ON o.id = q.offer_id
         SET q.original_quota = GREATEST(o.supplier_quota_template, c.consumido),
             q.current_quota = GREATEST(o.supplier_quota_template, c.consumido) - c.consumido,
             q.quota = GREATEST(o.supplier_quota_template, c.consumido),
             q.oferta_cupo_plantilla = o.supplier_quota_template
       WHERE o.supplier_quota_template IS NOT NULL;
    END IF;

    -- 4. exceso, como en P-19
    INSERT IGNORE INTO cupo_oferta_exceso (offer_id, supplier_id, consumido, plantilla_nueva, exceso, version,
                                           registrado_en)
    SELECT q.offer_id, q.supplier_id, c.consumido, q.oferta_cupo_plantilla, c.consumido - q.oferta_cupo_plantilla,
           q.oferta_cupo_version, NOW(6)
      FROM indirect_funder_offer_supplier_quota q JOIN v9n_consumo c ON c.id = q.id
     WHERE q.oferta_cupo_plantilla IS NOT NULL AND c.consumido > q.oferta_cupo_plantilla;
    DROP TEMPORARY TABLE IF EXISTS v9n_consumo;

    -- 5. invariantes
    SELECT COUNT(*) INTO v_n FROM indirect_funder_offer_supplier_quota
     WHERE current_quota < 0 OR current_quota > original_quota;
    IF v_n > 0 THEN
      SET @v9n_msg = CONCAT('V9 negociador detenida: invariantes de cupo indirecto rotas (', v_n, ')');
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @v9n_msg;
    END IF;
  END IF;
END$$
DELIMITER ;

CALL v9n_igualar_techos();
DROP PROCEDURE IF EXISTS v9n_igualar_techos;
