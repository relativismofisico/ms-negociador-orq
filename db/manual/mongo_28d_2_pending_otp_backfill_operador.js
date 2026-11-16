// Fase 28d (2.9) — backfill. Ejecutar con mongosh DESPUÉS de desplegar el código nuevo de ms-negociador-orq y ANTES
// del índice (mongo_28d_3). Copia rutOperador desde la solicitud guardada (request.rutOperador) a los documentos que
// no lo tienen. Idempotente: un segundo lanzamiento no cambia nada. No borra documentos.
const r = db.getCollection("pending_otp").updateMany(
  { rutOperador: { $exists: false }, "request.rutOperador": { $exists: true, $ne: null } },
  [ { $set: { rutOperador: "$request.rutOperador" } } ]
);
print("completados: " + r.modifiedCount);
