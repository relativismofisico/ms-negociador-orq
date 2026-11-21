// Fase 28d (A5): índices del vencimiento nocturno y del seguimiento de la firma final. Ejecutar con mongosh sobre la
// base del negociador (colección Facturas). Idempotente: createIndex con el mismo nombre y la misma definición no hace
// nada si ya existe. Antes muestra el diagnóstico (cuántas facturas toca cada consulta).
const f = db.getCollection("Facturas");
const hoy10 = new Date(Date.now() + 10 * 864e5);
print("diagnóstico: candidatas a vencer (hoy + 10 días) = " + f.countDocuments({
  fechavencimientoFactura: { $lte: hoy10 },
  estadoNegociacion: { $in: ["CARGADA", "SIN_NEGOCIAR", "NEGOCIADA", "ENDOSADA"] }
}));
print("diagnóstico: VENCIDA con reversión pendiente = " + f.countDocuments({ vencimientoReversionPendiente: true }));
print("diagnóstico: firma final sin seguimiento = " + f.countDocuments({ seguimientoLiquidacionPendiente: true }));
// Consulta del job: fecha de vencimiento hasta hoy + 10 días y estado
f.createIndex({ fechavencimientoFactura: 1, estadoNegociacion: 1 }, { name: "ix_facturas_vencimiento_estado" });
// Reanudación del job y re-emisión del reconciliador: solo los documentos marcados (índices parciales, pequeños)
f.createIndex({ vencimientoReversionPendiente: 1 },
  { name: "ix_facturas_vencimiento_reversion_pendiente", partialFilterExpression: { vencimientoReversionPendiente: true } });
f.createIndex({ seguimientoLiquidacionPendiente: 1 },
  { name: "ix_facturas_seguimiento_liquidacion_pendiente",
    partialFilterExpression: { seguimientoLiquidacionPendiente: true } });
print("índices de Facturas: " + f.getIndexes().map(i => i.name).join(", "));
