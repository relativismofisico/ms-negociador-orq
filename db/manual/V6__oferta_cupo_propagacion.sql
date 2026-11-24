-- Fase 28d (P-19, decisión de John J-09): propagación del cupo por proveedor de la oferta indirecta a los proveedores que
-- ya negociaron. Cada fila de indirect_funder_offer_supplier_quota guarda la plantilla de la oferta con la que está
-- alineada (oferta_cupo_plantilla) y la versión del cambio aplicado (oferta_cupo_version, la cupo_version de la V16 de
-- ms-registroempresa-neg). Al llegar el evento registro.cupo-oferta-indirecta-cambiado (versión v, plantilla T), cada fila
-- con versión < v suma d = T - oferta_cupo_plantilla a su techo (original_quota) y a su disponible, sin bajar nunca de lo
-- consumido; una reentrega o un evento viejo que llega después de uno nuevo no cambia nada.
-- Relleno inicial ("sumar la diferencia", lo decidido): la plantilla de cada fila existente es la plantilla VIGENTE de su
-- oferta, así que un cambio posterior suma exactamente T_nueva - T_vigente y las filas heredadas con techos distintos
-- SIGUEN distintas. Antes de desplegar se corre db/manual/diagnostico_p19_techos_heredados.sql (checklist).
-- La misma sentencia va en db/manual/V6__oferta_cupo_propagacion.sql, con su reversa. Idempotente.
DROP PROCEDURE IF EXISTS v6n_oferta_cupo_propagacion;

DELIMITER $$
CREATE PROCEDURE v6n_oferta_cupo_propagacion()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                 AND table_name = 'indirect_funder_offer_supplier_quota' AND column_name = 'oferta_cupo_version') THEN
    ALTER TABLE indirect_funder_offer_supplier_quota ADD COLUMN oferta_cupo_version BIGINT NOT NULL DEFAULT 0;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
                 AND table_name = 'indirect_funder_offer_supplier_quota' AND column_name = 'oferta_cupo_plantilla') THEN
    ALTER TABLE indirect_funder_offer_supplier_quota ADD COLUMN oferta_cupo_plantilla DECIMAL(19,2) NULL;
  END IF;
  -- relleno: plantilla vigente de la oferta (si la tabla de ofertas está en este esquema) y su versión (V16 de registro)
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
             AND table_name = 'indirect_funder_offer' AND column_name = 'cupo_version') THEN
    UPDATE indirect_funder_offer_supplier_quota q JOIN indirect_funder_offer o ON o.id = q.offer_id
       SET q.oferta_cupo_plantilla = o.supplier_quota_template, q.oferta_cupo_version = o.cupo_version
     WHERE q.oferta_cupo_plantilla IS NULL;
  ELSEIF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE()
                 AND table_name = 'indirect_funder_offer') THEN
    UPDATE indirect_funder_offer_supplier_quota q JOIN indirect_funder_offer o ON o.id = q.offer_id
       SET q.oferta_cupo_plantilla = o.supplier_quota_template
     WHERE q.oferta_cupo_plantilla IS NULL;
  END IF;
END$$
DELIMITER ;

CALL v6n_oferta_cupo_propagacion();
DROP PROCEDURE IF EXISTS v6n_oferta_cupo_propagacion;
