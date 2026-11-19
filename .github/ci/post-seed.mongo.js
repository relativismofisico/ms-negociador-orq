// Seed Mongo de ms-negociador-orq (DB_Facturas). Portado tal cual desde
// _e2e-infra/seed/ms-negociador-orq.mongo.js -- ya existía y coincide
// exactamente con endoso.feature, no se tradujo nada.
//
// Ancla un happy-path REAL de endoso de descuento para
// POST /negociaciones/NEG-END-CUFE/endosos -> 201 con cufesEndosadas poblado.
// Sin esto, la suite Karate de endoso solo podía afirmar "no 500" porque no
// había negociación/factura endosable en Mongo. Idempotente: borra el endoso
// y devuelve la factura a estado endosable en cada corrida.
//
// Actores: proveedor KARATE-CF-PROV, pagador KARATE-CF-PAG, fondeador
// KARATE-CF-FON (los mismos que usa el seed de ms-consultasFacturas-neg,
// misma base Mongo compartida -- hilo cross-repo, este endoso lleva el CUFE
// que ms-consultasFacturas-neg usa para resolver la factura exacta).

var PROV = "KARATE-CF-PROV";
var PAG  = "KARATE-CF-PAG";
var FON  = "KARATE-CF-FON";
var NEG  = "NEG-END-CUFE";
var NUM  = "FE-END-CUFE-1";
var CUFE = "CUFE-END-CUFE-0001-abcdef0123456789";

// ── Limpieza de corridas previas (idempotencia) ──────────────────────────────
db.endosos.deleteMany({ negociacionId: NEG });
db.negociacion.deleteMany({ _id: NEG });
db.Facturas.deleteMany({ _id: { $in: [NUM, NUM + "-DUP"] } });

// ── Factura endosable (colección "Facturas") ────────────────────────────────
db.Facturas.insertOne({
  _id: NUM, numeroFactura: NUM, cufe: CUFE,
  rutProveedor: PROV, nombreProveedor: "Karate CF Proveedor",
  rutPagador: PAG, nombrePagador: "Karate CF Pagador",
  moneda: "COP",
  valorFactura: NumberDecimal("7000000"),
  valorNetoFactura: NumberDecimal("6650000"),
  fechaEmisionFactura: ISODate("2026-08-01T00:00:00Z"),
  fechaNegociacionFactura: ISODate("2026-08-20T00:00:00Z"),
  fechavencimientoFactura: ISODate("2026-12-01T00:00:00Z"),
  estadoNegociacion: "NEGOCIADA", estadoFirma: "SIN_CESION",
  actores: [
    { rutEmpresa: PROV, rolEmpresaFactura: "PROVEEDOR" },
    { rutEmpresa: PAG,  rolEmpresaFactura: "PAGADOR" }
  ],
  usuarioCargaFactura: PROV, empresaCargaFactura: PROV
});

// ── Negociación con la factura pendiente de endoso ───────────────────────────
db.negociacion.insertOne({
  _id: NEG,
  tipoOperacion: { tipo: "FACTORING", clase: "NORMAL" },
  rutEmpresa: PROV, rutProveedor: PROV, rutPagador: PAG,
  rutFondeadores: [FON],
  totalNegociado: NumberDecimal("6650000"),
  estado: "ACEPTADA",
  facturas: [{
    numeroFactura: NUM,
    valorFactura: NumberDecimal("7000000"),
    estadoNegociacion: "APROBADA",
    estadoFirma: "SIN_CESION",
    rutProveedor: PROV, rutPagador: PAG,
    fondeadores: [{ rutFondeador: FON, nombreFondeador: "Karate CF Fondeador" }]
  }],
  fechaCreacion: ISODate("2026-08-20T10:00:00Z"),
  fechaUltimaActualizacion: ISODate("2026-08-20T10:00:00Z")
});

// Fase 28d (C): tras C la negociación guarda el CUFE de cada factura (lo copia de la base al negociar); sin él, el
// endoso, la OTP y la liquidación no la identifican ([FACTURA-SIN-CUFE]).
db.negociacion.updateOne({ _id: NEG, "facturas.numeroFactura": NUM }, { $set: { "facturas.$.cufe": CUFE } });

// Fase 28d (A1): pendiente OTP dedicado para el Karate de confirmar-otp (validación por HTTP en security). Solo lo usa
// ese escenario. Con security inalcanzable (CI) confirmar-otp da 503 y el pendiente no cambia; con security real (e2e),
// security no tiene OTP para esta negociación y responde EXPIRADO: 202 y el pendiente se descarta. Se reinicia en cada
// siembra.
db.pending_otp.deleteMany({ idNegociacion: "NEG-A1-HTTP" });
db.pending_otp.insertOne({
  idNegociacion: "NEG-A1-HTTP", rutEmpresa: "KARATE-A1-EMP", rutOperador: "op-karate-a1", rolActor: "PAGADOR",
  estado: "PENDIENTE_OTP", respuestasOtp: 0, fechaActualizacion: new Date()
});

print("seed ms-negociador-orq: negociacion=" + db.negociacion.countDocuments({ _id: NEG })
  + " factura=" + db.Facturas.countDocuments({ _id: NUM })
  + " endosos(previos borrados)=" + db.endosos.countDocuments({ negociacionId: NEG }));
