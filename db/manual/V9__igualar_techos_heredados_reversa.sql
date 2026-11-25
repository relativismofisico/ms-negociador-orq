-- Fase 28d (P-19 / P-24): reversa de V9__igualar_techos_heredados.sql. Devuelve cada fila de
-- indirect_funder_offer_supplier_quota a los valores de la ÚLTIMA corrida respaldada en cupos_respaldo_v9_negociador y
-- borra los excesos que esa corrida registró. Solo es válida ANTES de que lleguen propagaciones de P-19 posteriores
-- (después, los valores de esas propagaciones se perderían): ejecutar con el negociador de esta corrida detenido.
UPDATE indirect_funder_offer_supplier_quota q
  JOIN cupos_respaldo_v9_negociador r
    ON r.id = q.id AND r.corrida = (SELECT MAX(corrida) FROM cupos_respaldo_v9_negociador)
   SET q.quota = r.quota, q.current_quota = r.current_quota, q.original_quota = r.original_quota,
       q.oferta_cupo_plantilla = r.plantilla, q.oferta_cupo_version = r.version;
DELETE e FROM cupo_oferta_exceso e
 WHERE e.registrado_en >= (SELECT MAX(corrida) FROM cupos_respaldo_v9_negociador);
