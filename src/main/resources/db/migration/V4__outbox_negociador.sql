-- Fase 28d (A2): outbox JDBC de ms-commons-util 0.6.2 (auditoria.outbox.almacen=jdbc, auditoria.outbox.jdbc.tabla).
-- Toda la auditoría del negociador y los correos de la reversión (plantillas A y B) salen por esta tabla; los
-- que se registran dentro de una transacción MySQL de cupos quedan atómicos con ella. Idempotente (IF NOT EXISTS).
CREATE TABLE IF NOT EXISTS outbox_negociador (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    event_id        VARCHAR(64)  NOT NULL,
    tipo            VARCHAR(100) NULL,
    clave_kafka     VARCHAR(255) NOT NULL,
    topic           VARCHAR(255) NOT NULL,
    payload         MEDIUMTEXT   NOT NULL,
    estado          VARCHAR(16)  NOT NULL,
    intentos        INT          NOT NULL DEFAULT 0,
    proximo_intento DATETIME(3)  NOT NULL,
    lease_hasta     DATETIME(3)  NULL,
    fecha_creacion  DATETIME(3)  NOT NULL,
    fecha_envio     DATETIME(3)  NULL,
    ultimo_error    VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY ux_outbox_negociador_event_id (event_id),
    KEY ix_outbox_negociador_lote (estado, proximo_intento, id),
    KEY ix_outbox_negociador_clave (clave_kafka, estado, id),
    KEY ix_outbox_negociador_limpieza (estado, fecha_envio)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
