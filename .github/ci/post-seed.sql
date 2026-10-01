-- POST-seed: se aplica DESPUÉS de levantar la app -- ddl-auto=update ya creó
-- funder/parametrization/indirect_funder_offer/indirect_funder_offer_supplier_quota/
-- payer_funding_visibility/user_quota/messages_templates completas a partir de
-- las @Entity propias de este repo (confirmado, incluye @Version real en
-- funder/parametrization). Idempotente.
--
-- Anclas propias de este repo, portadas de _e2e-infra/seed/ms-negociador-orq.sql
-- + la fila de parametrization para 7004 que en _e2e-infra vive en el seed de
-- ms-registroempresa-neg (portada acá porque ese repo no corre en este pipeline).

USE dbcompany;

-- ── Autofondeo de la empresa 7004 (KARATE-DESTINO) ────────────────────────────
-- version=0 explícito: la columna la crea ddl-auto=update desde el @Version de
-- FondeadorQuotaEntity, sin default -- cualquier INSERT que no la incluya
-- rompe el primer saveAndFlush optimista sobre esa fila.
INSERT IGNORE INTO funder
  (id, funder_id, payer_id, supplier_id, payer_quota, payer_current_quota, payer_rate,
   estado, origen, aprobado_por_fondeador, activo, endoso_grupal, tipo_descuento,
   vigencia_descuento, porcentaje_reserva, version)
VALUES
  (7380, 7004, 7004, 0, 500000, 500000, 1.8, 'APROBADO', 'FONDEADOR', 1, b'1', 0,
   'FINANCIERO', 'TODO_EL_PLAZO', 0, 0);

-- ── Parametrización (cupo del administrador) de 7004 ─────────────────────────
-- Sin INSERT IGNORE: ddl-auto=update crea `parametrization` desde
-- EmpresaQuotaEntity sin UNIQUE en company_id (no lo declara), así que
-- INSERT IGNORE no protege contra duplicados si este script se reaplica sobre
-- la misma infra -- se usa el mismo guard WHERE NOT EXISTS que
-- messages_templates más abajo.
INSERT INTO parametrization (company_id, quota, current_quota, version)
SELECT 7004, 999999999, 999999999, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM parametrization WHERE company_id = 7004);

-- ── Oferta indirecta 7360 (fondeador 7003 -> pagador 7004) -- portada de
-- _e2e-infra/seed/ms-registroempresa-neg.sql, necesaria para
-- consultar-supplier-quota.feature/recargar-supplier-quota.feature.
-- OJO: a propósito NO se siembra indirect_funder_offer_supplier_quota para
-- (7360, 7006) -- recargar-supplier-quota.feature prueba justamente el 404
-- "el proveedor nunca negoció" contra esa combinación exacta (ver el
-- comentario del propio feature); consultar-supplier-quota.feature calcula
-- cupoDisponible desde supplier_quota_template de la oferta cuando no hay
-- fila propia, así que no necesita esta tabla poblada.
INSERT IGNORE INTO indirect_funder_offer
  (id, funder_id, payer_id, payer_rate, supplier_quota_template, estado, origen, aprobado_por_fondeador, activo)
VALUES
  (7360, 7003, 7004, 2.5, 1000000, 'APROBADO', 'FONDEADOR', 1, 1);

-- ── Visibilidad de pagador habilitada para 7004 (consulta de cupo indirecto) ──
INSERT IGNORE INTO payer_funding_visibility (company_id, habilitado) VALUES (7004, 1);

-- ── Guard A: operador propio de 7004 + operario "sombra" del asesor ───────────
INSERT IGNORE INTO user (ide_user, username, password, create_time, company_ide, person_IDE, rol_user_IDE, state_user_id) VALUES
  (7215,'op-destino','no-auth',NOW(6),7004,NULL,3,1),
  (7216,'opshadow-8000-7004','no-auth',NOW(6),7004,8000,3,1);
INSERT IGNORE INTO user_quota (id, user_id, quota, current_quota) VALUES
  (7215, 7215, 1000000000, 1000000000),
  (7216, 7216, 1000000000, 1000000000);

-- ── Sujetos de los tokens que firma karate-config.js (rama dev) ──────────────
-- JwtTestHelper firma con sub = 'dev-<rol>'; JjwtDecoder rechaza con 401
-- cualquier sub que no exista en dbcompany.user. company_ide NULL a propósito:
-- son tokens de rol genérico, no atados a una empresa.
INSERT IGNORE INTO user (ide_user, username, password, create_time, company_ide, person_IDE, rol_user_IDE, state_user_id) VALUES
  (7110,'dev-admin','no-auth',NOW(6),NULL,NULL,1,1),
  (7111,'dev-empresa','no-auth',NOW(6),NULL,NULL,2,1),
  (7112,'dev-operario','no-auth',NOW(6),NULL,NULL,3,1),
  (7113,'dev-fondeador','no-auth',NOW(6),NULL,NULL,4,1);

-- 7004 habilitada para FACTORING y CONFIRMING (validarEmpresaHabilitada del
-- flujo de negociación; los cupo-propio no lo chequean).
INSERT IGNORE INTO company_operation (ide_company_operation, enabled, operation_type, company_id, selected) VALUES
  (7404, b'1', 'FACTORING',  7004, b'1'),
  (7405, b'1', 'CONFIRMING', 7004, b'1');

-- ── Plantilla del documento formal de endoso (Mustache) ──────────────────────
-- TemplateRenderServiceImpl busca messages_templates.template_name='ENDOSO_DOCUMENTO'
-- activa; sin ella, crearEndoso() revienta con 500. A diferencia de
-- ms-notificaciones-util, acá no hay seeder automático.
INSERT INTO messages_templates (active, template_name, content)
SELECT b'1', 'ENDOSO_DOCUMENTO',
  '<!DOCTYPE html><html><body><h1>{{tituloDocumento}}</h1><p>{{nombreProveedor}} ({{rutProveedor}}) {{verboOperacion}} a favor de {{nombreFondeador}} ({{rutFondeador}}).</p><table>{{#facturas}}<tr><td>{{numeroFactura}}</td><td>{{valorFactura}}</td></tr>{{/facturas}}</table><p>Total facturas: {{cantidadFacturas}} — {{fechaOperacion}}</p><p>{{clausulaResponsabilidad}}</p><p>{{nombreFirmante}} {{tipoDocumentoFirmante}} {{numeroDocumentoFirmante}}</p></body></html>'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM messages_templates WHERE template_name = 'ENDOSO_DOCUMENTO');
