-- Fase 28d (P-19): marcas del consumo idempotente de commons (ConsumoIdempotente, almacén jdbc) del negociador. La tabla
-- es la misma (compartida) que crean la V11 de ms-registroempresa-neg y la V9 de ms-security-util: IF NOT EXISTS, para no
-- depender de que esos servicios hayan migrado antes. La misma sentencia va en db/manual/V8__evento_procesado.sql.
CREATE TABLE IF NOT EXISTS evento_procesado (
    consumidor      VARCHAR(100) NOT NULL,
    clave           VARCHAR(191) NOT NULL,
    fecha_procesado DATETIME(6)  NOT NULL,
    PRIMARY KEY (consumidor, clave),
    KEY ix_evento_procesado_fecha (fecha_procesado)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
