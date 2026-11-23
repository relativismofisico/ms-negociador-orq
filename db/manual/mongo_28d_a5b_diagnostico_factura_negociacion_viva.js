// Fase 28d (A5b): diagnóstico de SOLO LECTURA. Lista las facturas SIN_NEGOCIAR que tienen negociación (negociacionId),
// es decir, las que podían violar la invariante "una factura con una negociación viva nunca está SIN_NEGOCIAR" antes de
// que el mapeo PENDIENTE/PARCIAL pasara a NEGOCIADA. Ejecutar con mongosh sobre la base del negociador (Facturas) cuando
// exista QA o producción. Idempotente (no escribe nada).
// Para cada fila, la clave negociacion|cufe se cruza en MySQL con
//   SELECT clave_natural, estado FROM operacion_pendiente WHERE tipo = 'CREAR_NEGOCIACION' AND clave_natural = '<clave>';
// si el estado es CUPOS_DESCONTADOS o COMPLETADA, la negociación está viva y la factura debe quedar NEGOCIADA (corrección
// manual, con la decisión de John). Si es REVERTIDA, no hay negociación viva.
const filas = db.getCollection("Facturas").find(
  { estadoNegociacion: "SIN_NEGOCIAR", negociacionId: { $exists: true, $nin: [null, ""] } },
  { _id: 0, cufe: 1, negociacionId: 1, estadoFirma: 1 }).toArray();
filas.forEach(f => print("candidata: " + f.negociacionId + "|" + f.cufe + " firma=" + f.estadoFirma));
print("facturas SIN_NEGOCIAR con negociación (a cruzar con operacion_pendiente): " + filas.length);
