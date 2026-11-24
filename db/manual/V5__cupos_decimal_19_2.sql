-- Fase 28d (J-04, decisión de John): las columnas de cupo de indirect_funder_offer_supplier_quota (quota, current_quota y
-- original_quota, el techo) pasan a DECIMAL(19,2), la escala de todos los cupos (la V15 de ms-registroempresa-neg hace lo
-- mismo con sus tablas). La misma sentencia va en db/manual/V5__cupos_decimal_19_2.sql para QA y producción, con su reversa.
-- Pasos: 1. respaldo de cada valor (cupos_respaldo_v5_negociador); 2. verificación previa: si algún valor no cabe en
-- DECIMAL(19,2) el paquete se DETIENE sin convertir nada; 3. ALTER ... MODIFY de las que no sean ya 19,2; 4. cada valor
-- idéntico a su respaldo y 0 <= current_quota <= original_quota. Idempotente. La columna rate (una tasa, hoy
-- DECIMAL(38,2)) NO se toca aquí: las tasas van en el punto 6.

CREATE TABLE IF NOT EXISTS cupos_respaldo_v5_negociador (
  tabla          VARCHAR(64)   NOT NULL,
  fila_id        BIGINT        NOT NULL,
  columna        VARCHAR(64)   NOT NULL,
  valor          DECIMAL(38,6) NULL,
  respaldado_en  DATETIME(6)   NOT NULL,
  PRIMARY KEY (tabla, fila_id, columna)
);

DROP PROCEDURE IF EXISTS v5n_columna;
DROP PROCEDURE IF EXISTS v5n_cupos_decimal_19_2;

DELIMITER $$
-- Respalda, verifica y (si hace falta) convierte UNA columna. p_def: definición sin el tipo (NULL / NOT NULL y DEFAULT).
CREATE PROCEDURE v5n_columna(IN p_tabla VARCHAR(64), IN p_columna VARCHAR(64), IN p_def VARCHAR(128), IN p_fase INT)
BEGIN
  DECLARE v_existe INT DEFAULT 0;
  DECLARE v_precision INT DEFAULT NULL;
  DECLARE v_escala INT DEFAULT NULL;
  SELECT COUNT(*), MAX(numeric_precision), MAX(numeric_scale) INTO v_existe, v_precision, v_escala
    FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = p_tabla AND column_name = p_columna;
  IF v_existe = 1 THEN
    IF p_fase = 1 THEN
      -- 1. respaldo
      SET @v5n_sql = CONCAT('REPLACE INTO cupos_respaldo_v5_negociador (tabla, fila_id, columna, valor, respaldado_en) SELECT ''',
                            p_tabla, ''', id, ''', p_columna, ''', `', p_columna, '`, NOW(6) FROM `', p_tabla, '`');
      PREPARE s FROM @v5n_sql; EXECUTE s; DEALLOCATE PREPARE s;
      -- 2. verificación previa
      SET @v5n_sql = CONCAT('SELECT COUNT(*) INTO @v5n_n FROM `', p_tabla, '` WHERE ABS(`', p_columna,
                            '`) >= 100000000000000000 OR `', p_columna, '` <> ROUND(`', p_columna, '`, 2)');
      PREPARE s FROM @v5n_sql; EXECUTE s; DEALLOCATE PREPARE s;
      IF @v5n_n > 0 THEN
        SET @v5n_msg = CONCAT('V5 negociador detenida: ', @v5n_n, ' valores de ', p_tabla, '.', p_columna, ' no caben en DECIMAL(19,2)');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @v5n_msg;
      END IF;
    ELSEIF p_fase = 2 AND NOT (v_precision = 19 AND v_escala = 2) THEN
      -- 3. ALTER
      SET @v5n_sql = CONCAT('ALTER TABLE `', p_tabla, '` MODIFY COLUMN `', p_columna, '` DECIMAL(19,2) ', p_def);
      PREPARE s FROM @v5n_sql; EXECUTE s; DEALLOCATE PREPARE s;
    ELSEIF p_fase = 3 THEN
      -- 4. cada valor idéntico a su respaldo
      SET @v5n_sql = CONCAT('SELECT COUNT(*) INTO @v5n_n FROM cupos_respaldo_v5_negociador r JOIN `', p_tabla, '` t ON t.id = r.fila_id',
                            ' WHERE r.tabla = ''', p_tabla, ''' AND r.columna = ''', p_columna, ''' AND NOT (t.`', p_columna,
                            '` <=> r.valor)');
      PREPARE s FROM @v5n_sql; EXECUTE s; DEALLOCATE PREPARE s;
      IF @v5n_n > 0 THEN
        SET @v5n_msg = CONCAT('V5 negociador detenida: ', @v5n_n, ' valores de ', p_tabla, '.', p_columna, ' cambiaron al convertir');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @v5n_msg;
      END IF;
    END IF;
  END IF;
END$$

CREATE PROCEDURE v5n_cupos_decimal_19_2()
BEGIN
  DECLARE v_fase INT DEFAULT 1;
  DECLARE v_n INT DEFAULT 0;
  DECLARE v_lista TEXT DEFAULT NULL;
  WHILE v_fase <= 3 DO
    CALL v5n_columna('indirect_funder_offer_supplier_quota', 'quota', 'NULL DEFAULT NULL', v_fase);
    CALL v5n_columna('indirect_funder_offer_supplier_quota', 'current_quota', 'NULL DEFAULT NULL', v_fase);
    CALL v5n_columna('indirect_funder_offer_supplier_quota', 'original_quota', 'NULL DEFAULT NULL', v_fase);
    SET v_fase = v_fase + 1;
  END WHILE;

  -- 4. invariantes del cupo indirecto: 0 <= current_quota <= original_quota (el techo)
  SELECT COUNT(*), GROUP_CONCAT(id ORDER BY id) INTO v_n, v_lista FROM indirect_funder_offer_supplier_quota
   WHERE current_quota < 0 OR (original_quota IS NOT NULL AND current_quota > original_quota);
  IF v_n > 0 THEN
    SET @v5n_msg = CONCAT('V5 negociador detenida: invariantes de cupo indirecto rotas (', v_n, '): ', LEFT(v_lista, 60));
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @v5n_msg;
  END IF;
END$$
DELIMITER ;

CALL v5n_cupos_decimal_19_2();
DROP PROCEDURE IF EXISTS v5n_cupos_decimal_19_2;
DROP PROCEDURE IF EXISTS v5n_columna;
