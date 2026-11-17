// Fase 28d (2.9) — índice único de la clave natural (idNegociacion, rutEmpresa, rutOperador). Ejecutar con mongosh
// DESPUÉS del backfill y con 0 claves repetidas en la consulta previa (mongo_28d_1). Filtro parcial: solo documentos
// con rutOperador, así no afecta a documentos de la versión anterior que aún no lo tengan. Idempotente.
db.getCollection("pending_otp").createIndex(
  { idNegociacion: 1, rutEmpresa: 1, rutOperador: 1 },
  { name: "ux_pending_otp_clave", unique: true, partialFilterExpression: { rutOperador: { $exists: true } } }
);
print("índices: " + db.getCollection("pending_otp").getIndexes().length);
