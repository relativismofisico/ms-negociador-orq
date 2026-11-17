// Fase 28d (2.9) — SOLO LECTURA. Ejecutar con mongosh sobre la base de negociador ANTES del backfill y del índice.
// No modifica nada. Imprime solo conteos.
//   1) solicitudes sin rutOperador (las que el backfill va a completar)
//   2) claves (idNegociacion, rutEmpresa, rutOperador) repetidas, que impedirían crear el índice único
const c = db.getCollection("pending_otp");
print("total: " + c.countDocuments({}));
print("sin rutOperador: " + c.countDocuments({ rutOperador: { $exists: false } }));
print("sin rutOperador y con request.rutOperador (se completan): "
    + c.countDocuments({ rutOperador: { $exists: false }, "request.rutOperador": { $exists: true, $ne: null } }));
const repetidas = c.aggregate([
  { $project: { idNegociacion: 1, rutEmpresa: 1, op: { $ifNull: ["$rutOperador", "$request.rutOperador"] } } },
  { $group: { _id: { n: "$idNegociacion", e: "$rutEmpresa", o: "$op" }, total: { $sum: 1 } } },
  { $match: { total: { $gt: 1 } } },
  { $count: "claves" }
]).toArray();
print("claves repetidas (contando el operador que dejaría el backfill): " + (repetidas.length ? repetidas[0].claves : 0));
// Si hay claves repetidas, NO se borra nada aquí: se revisan a mano antes de crear el índice.
