-- Fase 27a — reclamo único de la reversión de cupos por liquidación fallida.
--
-- ReversionCuposServiceImpl inserta aquí (numero_factura, negociacion_id) en la MISMA transacción en que devuelve los
-- cupos de operador, empresa y fondeador: un liquidacion-fallida duplicado, concurrente o reintentado tras un fallo a
-- mitad choca con la clave primaria y no vuelve a revertir. Tabla propia de ms-negociador-orq.
--
-- Idempotente (IF NOT EXISTS). En qa/prod Flyway está apagado: aplicar a mano db/manual/V2__reversion_cupos_factura.sql
-- (copia exacta de este archivo) antes de desplegar.
CREATE TABLE IF NOT EXISTS reversion_cupos_factura (
    numero_factura VARCHAR(100) NOT NULL,
    negociacion_id VARCHAR(64)  NOT NULL,
    fecha_reclamo  DATETIME(6)  NOT NULL,
    PRIMARY KEY (numero_factura, negociacion_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
