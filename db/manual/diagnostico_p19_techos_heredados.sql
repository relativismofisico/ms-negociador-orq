-- Fase 28d (P-19, decisión de John J-09): diagnóstico ANTES de desplegar P-19 (solo lectura). P-19 SUMA la diferencia de
-- la plantilla a cada proveedor: las filas heredadas con techos distintos dentro de una misma oferta (proveedores que
-- negociaron cuando la plantilla era otra) SIGUEN distintas. Si alguna de las dos consultas devuelve filas, NO desplegar:
-- llevar la lista a John para que decida si se igualan.
-- 1. Ofertas con más de un techo entre sus proveedores
SELECT offer_id, COUNT(*) AS proveedores, COUNT(DISTINCT original_quota) AS techos_distintos,
       MIN(original_quota) AS techo_min, MAX(original_quota) AS techo_max
  FROM indirect_funder_offer_supplier_quota
 GROUP BY offer_id
HAVING COUNT(DISTINCT original_quota) > 1;
-- 2. Proveedores cuyo techo no es la plantilla vigente de su oferta
SELECT q.offer_id, q.supplier_id, q.original_quota AS techo, o.supplier_quota_template AS plantilla_vigente
  FROM indirect_funder_offer_supplier_quota q JOIN indirect_funder_offer o ON o.id = q.offer_id
 WHERE NOT (q.original_quota <=> o.supplier_quota_template);
