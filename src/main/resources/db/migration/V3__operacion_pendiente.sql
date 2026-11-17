-- Fase 28d, opción A: operaciones mixtas (MySQL + Mongo) pendientes de completar. Una sola tabla para crear
-- negociación, rechazo, endoso rechazado, liquidación fallida y vencimiento. La clave natural va con el CUFE de la
-- factura (negociacion|cufe), nunca con el número, que no es único entre emisores. El UNIQUE (tipo, clave_natural) hace
-- idempotente el alta; las transiciones son UPDATE condicionales sobre el estado (CAS); el reconciliador toma las que
-- quedaron a medias con una concesión (lease_hasta).
CREATE TABLE operacion_pendiente (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    tipo                VARCHAR(40)  NOT NULL,
    clave_natural       VARCHAR(191) NOT NULL,
    estado              VARCHAR(40)  NOT NULL,
    datos               TEXT         NULL,
    lease_hasta         DATETIME(3)  NULL,
    intentos            INT          NOT NULL DEFAULT 0,
    fecha_creacion      DATETIME(3)  NOT NULL,
    fecha_actualizacion DATETIME(3)  NOT NULL,
    ultimo_error        VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_operacion_pendiente_clave (tipo, clave_natural),
    KEY ix_operacion_pendiente_reconciliar (estado, lease_hasta, fecha_actualizacion)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
