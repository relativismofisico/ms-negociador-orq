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
  fechavencimientoFactura: new Date(Date.now() + 180 * 864e5) /* Fase 28d (P-21): relativa; la fija de 2026-12-01 vencía */,
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

print("seed ms-negociador-orq: negociacion=" + db.negociacion.countDocuments({ _id: NEG })
  + " factura=" + db.Facturas.countDocuments({ _id: NUM })
  + " endosos(previos borrados)=" + db.endosos.countDocuments({ negociacionId: NEG }));

// Fase 28d (P-21): facturas de los Karate de A5, A5b y P12 (portado de _e2e-infra/seed/ms-negociador-orq.mongo.js).
// A5: VENCIDA y RECHAZADA no se vuelven a negociar (vencida-no-negociable.feature); A5b: NEGOCIADA con negociación viva
// (negociacion-viva.feature). Proveedor KARATE-PAYERD (7008), pagador KARATE-PAYERB (7006). Idempotente.
db.Facturas.deleteMany({ _id: { $in: ["FE-KARATE-A5-VENC", "FE-KARATE-A5-RECH", "FE-KARATE-A5B-VIVA"] } });
[["FE-KARATE-A5-VENC", "VENCIDA", "SIN_CESION"], ["FE-KARATE-A5-RECH", "RECHAZADA", "RECHAZADA"],
 ["FE-KARATE-A5B-VIVA", "NEGOCIADA", "SIN_CESION"]].forEach(function (x) {
  db.Facturas.insertOne({
    _id: x[0], numeroFactura: x[0], cufe: "CUFE-" + x[0],
    rutProveedor: "KARATE-PAYERD", nombreProveedor: "Karate Pagadora D",
    rutPagador: "KARATE-PAYERB", nombrePagador: "Karate Pagadora B",
    moneda: "COP",
    valorFactura: NumberDecimal("1000000.55"), valorNetoFactura: NumberDecimal("950000.37"),
    fechaEmisionFactura: new Date(Date.now() - 30 * 864e5),
    fechavencimientoFactura: new Date(Date.now() + 120 * 864e5),
    estadoNegociacion: x[1], estadoFirma: x[2], negociacionId: "NEG-KARATE-A5-ANTERIOR",
    actores: [
      { rutEmpresa: "KARATE-PAYERD", rolEmpresaFactura: "PROVEEDOR" },
      { rutEmpresa: "KARATE-PAYERB", rolEmpresaFactura: "PAGADOR" }
    ],
    usuarioCargaFactura: "KARATE-PAYERD", empresaCargaFactura: "KARATE-PAYERD"
  });
});

// P12 (punto 1.2): 2.000.000,20 neto contra 1.500.000,55 de cupo indirecto: SIN_CUPO_INDIRECTO, faltante 499.999,65.
db.Facturas.deleteMany({ _id: "FE-KARATE-SIN-CUPO-IND" });
db.Facturas.insertOne({
  _id: "FE-KARATE-SIN-CUPO-IND", numeroFactura: "FE-KARATE-SIN-CUPO-IND", cufe: "CUFE-FE-KARATE-SIN-CUPO-IND",
  rutProveedor: "KARATE-PAYERD", nombreProveedor: "Karate Pagadora D",
  rutPagador: "KARATE-DESTINO", nombrePagador: "Karate Destino",
  moneda: "COP",
  valorFactura: NumberDecimal("2100000.00"), valorNetoFactura: NumberDecimal("2000000.20"),
  fechaEmisionFactura: new Date(Date.now() - 5 * 864e5),
  fechavencimientoFactura: new Date(Date.now() + 120 * 864e5),
  estadoNegociacion: "SIN_NEGOCIAR", estadoFirma: "SIN_CESION",
  actores: [
    { rutEmpresa: "KARATE-PAYERD", rolEmpresaFactura: "PROVEEDOR" },
    { rutEmpresa: "KARATE-DESTINO", rolEmpresaFactura: "PAGADOR" }
  ],
  usuarioCargaFactura: "KARATE-PAYERD", empresaCargaFactura: "KARATE-PAYERD"
});
print("seed P-21: facturas A5/A5b/P12=" + db.Facturas.countDocuments({ _id: { $in: ["FE-KARATE-A5-VENC",
  "FE-KARATE-A5-RECH", "FE-KARATE-A5B-VIVA", "FE-KARATE-SIN-CUPO-IND"] } }));
