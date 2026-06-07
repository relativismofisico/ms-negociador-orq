# ms-negociador-orq

Microservicio orquestador de flujos de negociación de facturas para operaciones de **Factoring** y **Confirming**.

---

## Tabla de Contenidos

1. [Descripción General](#1-descripción-general)
2. [Tecnologías](#2-tecnologías)
3. [Requisitos Previos](#3-requisitos-previos)
4. [Configuración](#4-configuración)
5. [Ejecución Local](#5-ejecución-local)
6. [Swagger / OpenAPI](#6-swagger--openapi)
7. [Endpoints](#7-endpoints)
8. [Reglas de Negocio](#8-reglas-de-negocio)
9. [Modelo de Datos](#9-modelo-de-datos)
10. [Seguridad](#10-seguridad)
11. [Observabilidad](#11-observabilidad)
12. [Pruebas](#12-pruebas)
13. [Estructura del Proyecto](#13-estructura-del-proyecto)
14. [Diagramas](#14-diagramas)
15. [Ambientes](#15-ambientes)
16. [Troubleshooting](#16-troubleshooting)
17. [Consideraciones Técnicas](#17-consideraciones-técnicas)

---

## 1. Descripción General

### Objetivo del Microservicio

`ms-negociador-orq` es el orquestador central de los flujos financieros de negociación de facturas. Su función es coordinar la validación, creación, endoso y aprobación de operaciones de **Factoring** (anticipo de facturas a proveedores) y **Confirming** (pago anticipado a proveedores por cuenta de pagadores), garantizando la integridad de cupos, el cumplimiento de reglas de negocio y la trazabilidad de cada operación.

### Problema de Negocio que Resuelve

En las cadenas de financiamiento de facturas intervienen múltiples actores (empresas, operadores, proveedores, pagadores, fondeadores) con cupos independientes y reglas de operación distintas según el tipo de producto financiero. Sin un orquestador, la validación de cupos en cascada, la gestión de estados de las facturas y la coordinación de aprobaciones con OTP serían responsabilidad de múltiples servicios sin garantía de consistencia. Este microservicio centraliza toda esa lógica.

### Alcance Funcional

| Funcionalidad | Descripción |
|---|---|
| Creación de negociaciones | Procesa lotes de facturas con validación de cupos en cascada |
| Registro de endosos | Genera documentos HTML de endoso de descuento |
| Aprobación con OTP | Flujo asíncrono de validación OTP via Kafka |
| Validación de vencimiento | Consulta el estado de vigencia de una factura |
| Reversión de cupos | Restaura cupos cuando una liquidación falla |
| Notificaciones | Publica eventos de email para cada transición |

### Arquitectura Utilizada

El microservicio implementa **Arquitectura Hexagonal (Ports & Adapters)** en combinación con principios de **Clean Architecture**:

- **Domain**: Modelos de negocio puros, enumeraciones y eventos de dominio. Sin dependencias de frameworks.
- **Application**: Servicios de aplicación, DTOs, puertos de salida (interfaces), controladores REST y excepciones de dominio.
- **Infrastructure**: Adaptadores de persistencia (MongoDB, MySQL/JPA), productores/consumidores Kafka, configuración de seguridad y configuración OpenAPI.

La comunicación asíncrona con otros microservicios se realiza mediante **Apache Kafka** con el patrón **Outbox** para garantizar la entrega de eventos.

---

## 2. Tecnologías

| Categoría | Tecnología | Versión |
|---|---|---|
| Lenguaje | Java | 17 |
| Framework | Spring Boot | 3.3.4 |
| Build | Gradle | 8.10 |
| Persistencia documental | MongoDB (Spring Data) | Spring Boot 3.3.4 managed |
| Persistencia relacional | MySQL + Spring Data JPA | Connector 8.3.0 |
| Mensajería | Apache Kafka (Spring Kafka) | Spring Boot 3.3.4 managed |
| Seguridad | Spring Security 6 + OAuth2 Resource Server | Spring Boot 3.3.4 managed |
| JWT | jjwt-api | 0.12.6 |
| Documentación API | springdoc-openapi-starter-webmvc-ui | 2.5.0 |
| Generación de PDF | openhtmltopdf-pdfbox | 1.0.10 |
| Reducción boilerplate | Lombok | Spring Boot 3.3.4 managed |
| Pruebas unitarias | JUnit 5 + Mockito | Spring Boot 3.3.4 managed |
| Pruebas de aceptación | Karate | 1.4.0 |
| Pruebas de arquitectura | ArchUnit | 1.3.0 |
| Pruebas de rendimiento | Apache JMeter | 5.6.3 (externo) |
| Cobertura | JaCoCo | Spring Boot 3.3.4 managed |
| Análisis estático | Checkstyle | 10.17.0 |
| Análisis estático | PMD | 7.3.0 |
| Análisis estático | SpotBugs | 4.8.6 |
| Calidad de código | SonarQube | Pendiente por definir (versión del servidor) |

---

## 3. Requisitos Previos

### Java

- **JDK 17** o superior. Se recomienda [Eclipse Temurin 17](https://adoptium.net/).

```bash
java -version
# java version "17.x.x"
```

### Gradle

- **Gradle 8.10** (incluido via Gradle Wrapper, no requiere instalación manual).

```bash
./gradlew --version
```

### Bases de Datos

| Base de Datos | Versión Mínima | Uso |
|---|---|---|
| MongoDB | 6.x | Negociaciones, facturas, endosos, OTP, outbox |
| MySQL | 8.0 | Usuarios, empresas, cupos |

### Mensajería

- **Apache Kafka** 3.x con los siguientes topics creados:

| Topic | Descripción |
|---|---|
| `liquidacion.solicitada` | Evento de solicitud de liquidación |
| `otp.requested` | Solicitud de generación de OTP |
| `otp.validate` | Solicitud de validación de OTP |
| `otp.validate.response` | Respuesta de validación de OTP |
| `notificacion.email` | Eventos de notificación por email |
| `liquidacion.fallida` | Evento de liquidación fallida |

### Dependencias Externas

- Servicio externo de validación y entrega de OTP (consume `otp.requested`, produce `otp.validate.response`).
- Servicio de liquidación (consume `liquidacion.solicitada`).
- Servicio de notificación de emails (consume `notificacion.email`).

### Docker

No se detectó `Dockerfile` ni `docker-compose.yml` en el repositorio. **Pendiente por definir** si el despliegue se realiza en contenedores.

---

## 4. Configuración

### Variables de Entorno

Las variables marcadas con `*` son **obligatorias en producción**.

| Variable | Descripción | Ejemplo |
|---|---|---|
| `DB_HOST` * | Host de la base de datos MySQL | `dbcompany.rds.amazonaws.com` |
| `DB_USER` * | Usuario MySQL | `admin` |
| `DB_PASSWORD` * | Contraseña MySQL | `s3cret` |
| `MONGO_URI` * | URI de conexión MongoDB | `mongodb+srv://user:pass@cluster/` |
| `MONGO_DB` * | Nombre de la base de datos MongoDB | `DB_Facturas_Prod` |
| `KAFKA_SERVERS` * | Bootstrap servers de Kafka | `kafka1:9092,kafka2:9092` |
| `JWT_SECRET` | Secret Base64 HMAC-HS256 para JWT | Ver `application.yml` |
| `AMBIENTE_PIPE` | Perfil activo de Spring | `dev`, `qa`, `prod` |

### application.yml — Configuración Principal

```yaml
server:
  port: 8083

spring:
  application:
    name: ms-negociador-orq
  jackson:
    default-property-inclusion: non_null
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 200MB

negociacion:
  dias-minimos-vencimiento: 10   # Días mínimos antes del vencimiento para negociar

springdoc:
  api-docs:
    path: /v3/api-docs
    enabled: true
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
    tags-sorter: alpha
    operations-sorter: alpha
    display-request-duration: true
    try-it-out-enabled: true

security:
  jwt:
    secret: AF84F1FGllNpNnLG055fdg5hGHJK4KGG5VH5TR5J05JFGGDFDGXVV545J4505G666JFGF2mMY95y
  roles:
    negociacion:
      - ADMINISTRADOR
      - EMPRESA
      - OPERARIO
    endoso:
      - ADMINISTRADOR
      - EMPRESA
      - FONDEADOR
    otp:
      - ADMINISTRADOR
      - EMPRESA
      - FONDEADOR
    validacion:
      - ADMINISTRADOR
      - OPERARIO
      - EMPRESA
      - FONDEADOR

kafka:
  topics:
    liquidacion: liquidacion.solicitada
    otp-request: otp.requested
    otp-validate: otp.validate
    otp-validate-response: otp.validate.response
    notificacion-email: notificacion.email
    liquidacion-fallida: liquidacion.fallida

facturas:
  vencimiento:
    cron: "0 0 1 * * *"
```

### Configuración por Ambiente

| Propiedad | Desarrollo | QA | Producción |
|---|---|---|---|
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/dbcompany` | `jdbc:mysql://${DB_HOST}/dbcompany` | `jdbc:mysql://${DB_HOST}/dbcompany?useSSL=true` |
| `spring.data.mongodb.uri` | `mongodb://localhost:27017/DB_Facturas` | `${MONGO_URI}` | `${MONGO_URI}` |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | `${KAFKA_SERVERS}` | `${KAFKA_SERVERS}` |
| `spring.jpa.hibernate.ddl-auto` | `update` | `update` | `validate` |
| `spring.jpa.show-sql` | `true` | `false` | `false` |
| `springdoc.swagger-ui.enabled` | `true` | `true` | `false` |
| `springdoc.api-docs.enabled` | `true` | `true` | `false` |
| HikariCP pool máximo | 10 | 10 | 20 |

### Secrets Requeridos

> ⚠️ **En producción, el secret JWT no debe estar en `application.yml`**. Debe inyectarse como variable de entorno o mediante un gestor de secretos (AWS Secrets Manager, HashiCorp Vault, etc.).

El secret JWT actual en `application.yml` es solo para entornos de desarrollo y pruebas.

---

## 5. Ejecución Local

### Compilar el proyecto

```bash
./gradlew build
```

### Compilar sin tests

```bash
./gradlew build -x test -x architectureTest
```

### Ejecutar el microservicio

```bash
./gradlew bootRun
```

Con perfil específico:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

O con el JAR generado:

```bash
java -jar build/libs/ms-negociador-orq-1.0.0.jar --spring.profiles.active=dev
```

### Correr pruebas unitarias

```bash
./gradlew test
```

### Correr pruebas de arquitectura

```bash
./gradlew architectureTest
```

### Correr pruebas de aceptación (Karate)

```bash
# Suite completa
./gradlew automatedTest

# Solo smoke tests
./gradlew automatedTest -Dkarate.options="--tags @smoke"

# Solo regression
./gradlew automatedTest -Dkarate.options="--tags @regression"
```

### Verificar calidad de código

```bash
# Checkstyle
./gradlew checkstyleMain

# PMD
./gradlew pmdMain

# SpotBugs
./gradlew spotbugsMain

# Todos los checks
./gradlew check
```

### Ver reporte de cobertura

```bash
./gradlew jacocoTestReport
# Reporte: build/jacocoHtml/index.html
```

### Análisis SonarQube

```bash
./gradlew sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.login=<token>
```

### Configuración Local Mínima

Antes de ejecutar localmente, asegúrese de tener activos:

1. MongoDB en `localhost:27017` con base de datos `DB_Facturas`
2. MySQL en `localhost:3306` con base de datos `dbcompany`
3. Kafka en `localhost:9092`

---

## 6. Swagger / OpenAPI

### Habilitación

Swagger está habilitado automáticamente en los perfiles `default`, `dev` y `qa`. En producción se deshabilita mediante `application-prod.yml`.

### URLs

| Ambiente | Swagger UI | OpenAPI JSON |
|---|---|---|
| Local | `http://localhost:8083/swagger-ui.html` | `http://localhost:8083/v3/api-docs` |
| Desarrollo | Pendiente por definir | Pendiente por definir |
| QA | Pendiente por definir | Pendiente por definir |
| Producción | **Deshabilitado** | **Deshabilitado** |

### Autenticación Requerida

Todos los endpoints requieren autenticación mediante **Bearer JWT**:

```
Authorization: Bearer <token>
```

El token debe contener el claim `rol` con uno de los valores: `ADMINISTRADOR`, `EMPRESA`, `OPERARIO`, `FONDEADOR`.

### Cómo Generar un Token de Prueba (Desarrollo)

Utilice la clase `JwtTestTokenGenerator` incluida en el proyecto:

```bash
cd src/test/java/com/negociadororq/util
# Ejecutar la clase directamente desde el IDE o via Gradle test
```

O genere un token manualmente con el secret del entorno de desarrollo:

- **Secret (dev):** `AF84F1FGllNpNnLG055fdg5hGHJK4KGG5VH5TR5J05JFGGDFDGXVV545J4505G666JFGF2mMY95y`
- **Claims requeridos:** `sub`, `rol` (ADMINISTRADOR | EMPRESA | OPERARIO | FONDEADOR)
- **Algoritmo:** HS256

### Cómo Usar Swagger UI

1. Acceder a `http://localhost:8083/swagger-ui.html`
2. Hacer clic en **Authorize** (ícono del candado)
3. Ingresar el token en el campo `bearerAuth (http, Bearer)`: `<token>` (sin el prefijo "Bearer")
4. Hacer clic en **Authorize** y cerrar el diálogo
5. Expandir el endpoint deseado y hacer clic en **Try it out**
6. Completar el cuerpo de la solicitud y ejecutar

---

## 7. Endpoints

### POST /negociar/factura — Crear Negociación

**Roles requeridos:** `ADMINISTRADOR`, `EMPRESA`, `OPERARIO`

**Request Body:**
```json
{
  "rutEmpresa": "900341104",
  "rolEmpresa": "PROVEEDOR",
  "tipoOperacion": {
    "tipo": "FACTORING",
    "clase": "DESCUENTO"
  },
  "facturas": [
    {
      "numeroFactura": "FECI3566",
      "rutPagador": "800200300"
    }
  ],
  "fondeadoresGlobales": [
    {
      "rutFondeador": "700100200"
    }
  ],
  "rutOperador": "1128384790"
}
```

**Response 201:**
```json
{
  "idNegociacion": "NEG-2026-001",
  "rutEmpresa": "900341104",
  "tipoOperacion": { "tipo": "FACTORING", "clase": "DESCUENTO" },
  "facturas": [...],
  "fondeadoresAsignados": [...],
  "totalNegociado": 5000000.00,
  "estado": "PENDIENTE",
  "errores": []
}
```

| Código | Descripción |
|---|---|
| 201 | Negociación creada exitosamente |
| 400 | Cuerpo de la petición malformado o JSON inválido |
| 401 | Token JWT ausente, expirado o con firma inválida |
| 403 | El rol del token no está autorizado para este endpoint |
| 404 | Operador o factura no encontrada |
| 422 | Regla de negocio violada (cupo insuficiente, factura vencida, etc.) |
| 500 | Error interno del servidor |

---

### POST /negociaciones/{idNegociacion}/endosos — Registrar Endoso

**Roles requeridos:** `ADMINISTRADOR`, `EMPRESA`, `FONDEADOR`

**Path Variable:** `idNegociacion` — Identificador único de la negociación en MongoDB.

**Request Body:**
```json
{
  "rutActor": "900341104",
  "numerosFactura": ["FECI3566", "FECI3567"]
}
```

**Response 201:**
```json
{
  "idNegociacion": "NEG-2026-001",
  "estadoEndoso": "PENDIENTE",
  "facturasEndosadas": ["FECI3566"],
  "facturasRechazadas": ["FECI3567"],
  "plantillaHtml": "<html>...</html>"
}
```

| Código | Descripción |
|---|---|
| 201 | Endoso registrado exitosamente |
| 400 | Cuerpo malformado o lista de facturas vacía |
| 401 | Token JWT ausente, expirado o inválido |
| 403 | El rol no está autorizado |
| 404 | Negociación no encontrada |
| 422 | Regla de negocio violada (factura no endosable, endoso duplicado) |
| 500 | Error interno del servidor |

---

### POST /validaciones/facturas/vencimiento — Validar Vencimiento

**Roles requeridos:** `ADMINISTRADOR`, `OPERARIO`, `EMPRESA`, `FONDEADOR`

**Request Body:**
```json
{
  "numeroFactura": "FECI3566",
  "fechaProceso": "2026-06-07"
}
```

**Response 200:**
```json
{
  "valida": true,
  "vencida": false,
  "diasHastaVencimiento": 45,
  "codigo": "FACTURA_VIGENTE",
  "mensaje": "La factura está vigente con 45 días hasta su vencimiento."
}
```

Posibles valores del campo `codigo`:

| Valor | Descripción |
|---|---|
| `FACTURA_VIGENTE` | Factura con días suficientes hasta vencimiento |
| `FACTURA_VENCIDA` | Factura vencida o con menos días del mínimo requerido |
| `SIN_FECHA_VENCIMIENTO` | La factura no tiene fecha de vencimiento registrada |

| Código HTTP | Descripción |
|---|---|
| 200 | Resultado de validación retornado |
| 400 | Cuerpo malformado |
| 401 | Token JWT inválido |
| 403 | Rol no autorizado |
| 404 | Factura no encontrada |
| 500 | Error interno |

---

### POST /negociaciones/solicitar-otp — Solicitar Código OTP

**Roles requeridos:** `ADMINISTRADOR`, `EMPRESA`, `FONDEADOR`

**Request Body:**
```json
{
  "idNegociacion": "NEG-2026-001",
  "rutOperador": "1128384790",
  "rutEmpresa": "900341104",
  "rolActor": "FONDEADOR",
  "facturas": [
    {
      "numeroFactura": "FECI3566",
      "aprobado": true,
      "observacion": "Aprobado"
    }
  ],
  "observacion": "Revisión completada"
}
```

**Response 202:**
```
OTP solicitado
```

| Código HTTP | Descripción |
|---|---|
| 202 | Solicitud de OTP recibida y en procesamiento asíncrono |
| 400 | Cuerpo malformado |
| 401 | Token JWT inválido |
| 403 | Rol no autorizado |
| 404 | Negociación no encontrada |
| 500 | Error interno |

---

### POST /negociaciones/confirmar-otp — Confirmar OTP y Aprobar Endoso

**Roles requeridos:** `ADMINISTRADOR`, `EMPRESA`, `FONDEADOR`

> La IP del cliente se captura automáticamente desde el request HTTP y se registra para auditoría.

**Request Body:**
```json
{
  "idNegociacion": "NEG-2026-001",
  "rutOperador": "1128384790",
  "rutEmpresa": "900341104",
  "otp": "482931"
}
```

**Response 202:**
```
Validando OTP...
```

| Código HTTP | Descripción |
|---|---|
| 202 | OTP recibido, validación en procesamiento asíncrono |
| 400 | Cuerpo malformado |
| 401 | Token JWT inválido |
| 403 | Rol no autorizado |
| 404 | Negociación no encontrada |
| 422 | OTP inválido o expirado |
| 500 | Error interno |

---

### Estructura de Error (todos los endpoints)

```json
{
  "timestamp": "2026-06-07T12:00:00",
  "status": 422,
  "error": "Unprocessable Entity",
  "codigo": "FACTURA_VENCIDA",
  "mensaje": "La factura FECI3566 está vencida o no cumple los días mínimos requeridos.",
  "path": "/negociar/factura",
  "detalles": []
}
```

---

## 8. Reglas de Negocio

### Negociaciones

- Una negociación requiere al menos una factura negociable.
- El operador debe existir en el sistema (`users` MySQL) antes de crear la negociación.
- El proceso de cada factura es **independiente**: el fallo de una factura no bloquea las demás.
- Las facturas rechazadas se reportan en el campo `errores` de la respuesta sin lanzar excepción.
- El `totalNegociado` refleja únicamente el valor de las facturas exitosamente negociadas.

### Facturas

- Solo se pueden negociar facturas en estado `SIN_NEGOCIAR`.
- La fecha de vencimiento de la factura debe superar la fecha de proceso en al menos **10 días** (configurable en `negociacion.dias-minimos-vencimiento`).
- En operaciones **FACTORING**: solo el **proveedor** de la factura puede iniciar la negociación.
- En operaciones **CONFIRMING**: solo el **pagador** de la factura puede iniciar la negociación.
- El valor utilizado para validar cupos es `valorNetoFactura` si está disponible; de lo contrario, `valorFactura`.
- Una factura negociada exitosamente cambia su estado de firma a `SIN_CESION` (Factoring) o `SIN_DESCUENTO` (Confirming).

### Validación de Cupos (cascada)

La validación de cupos sigue el siguiente orden; si cualquier nivel falla, la factura se rechaza sin consumir cupos:

1. **Cupo del operador** — verificado en MySQL (`users`).
2. **Cupo de la empresa** — verificado en MySQL (`empresa_quotas`).
3. **Cupo de los fondeadores** — verificado en MySQL (`fondeador_quotas`). Si la factura tiene fondeadores propios, estos tienen precedencia sobre `fondeadoresGlobales`.

Los cupos se descuentan únicamente cuando todos los niveles son válidos.

### Endosos

- El endoso solo puede registrarse sobre facturas que pertenecen a la negociación indicada.
- En operaciones **FACTORING**: solo el **proveedor** puede registrar el endoso.
- En operaciones **CONFIRMING**: solo el **pagador** puede registrar el endoso.
- Una factura no puede tener más de un endoso activo (sin estado `RECHAZADO`).
- Las facturas endosadas cambian a estado `EN_REVISION`.
- Se genera un documento HTML formal del endoso para su posterior firma digital.
- El resultado puede ser parcial: algunas facturas endosadas y otras rechazadas en la misma solicitud.

### Aprobación con OTP

- La aprobación es **asíncrona**: las respuestas HTTP son `202 Accepted`.
- Un OTP expirado o inválido resulta en rechazo del endoso (`422` al confirmar).
- Los roles de aprobación requeridos según tipo de operación:
  - **FACTORING**: requiere aprobación de `PAGADOR` y `FONDEADOR`.
  - **CONFIRMING**: requiere aprobación de `PROVEEDOR`.
- Una aprobación duplicada del mismo actor y factura es ignorada (idempotencia).
- Si **todos** los actores requeridos aprueban: la factura avanza a `CESION` (Factoring) o `DESCUENTO` (Confirming), y se publica `LiquidacionSolicitadaEvent`.
- Si **algún** actor rechaza: se revierten todos los cupos consumidos y se publica `LiquidacionFallidaEvent`.
- La IP del cliente se captura automáticamente al confirmar el OTP para auditoría.

### Reversión de Cupos

- La reversión se activa al consumir el evento `liquidacion.fallida` de Kafka.
- Se revierten en el orden inverso: fondeadores → empresa → operador.
- La factura regresa al estado `SIN_NEGOCIAR`.

---

## 9. Modelo de Datos

### MongoDB — Colecciones

#### negociaciones
```
{
  _id: ObjectId,
  id: String,
  tipoOperacion: { tipo, clase },
  rutEmpresa: String,
  rutPagador: String,
  rutPagadores: [String],
  rutProveedor: String,
  rutFondeadores: [String],
  totalNegociado: Decimal128,
  facturas: [FacturaNegociada],
  estado: EstadoNegociacion,
  errores: [String],
  aprobaciones: { Map<rutActor, Boolean> },
  fechaCreacion: Date,
  fechaUltimaActualizacion: Date
}
```

#### facturas
```
{
  _id: ObjectId,
  numeroFactura: String,         // índice
  codigoAlternoFactura: String,
  rutPagador: String,
  nombrePagador: String,
  rutProveedor: String,
  nombreProveedor: String,
  fechaEmisionFactura: Date,
  fechaVencimientoFactura: Date,
  moneda: String,
  valorFactura: Decimal128,
  valorNetoFactura: Decimal128,
  estadoNegociacion: String,     // SIN_NEGOCIAR, EN_NEGOCIACION, NEGOCIADA
  estadoFirma: EstadoFirma,
  actores: [ActorFactura]
}
```

#### endosos
```
{
  _id: ObjectId,
  negociacionId: String,
  numeroFactura: String,
  rutActorEndosa: String,
  rutProveedor: String,
  rutPagador: String,
  rutFondeador: String,
  tipoOperacion: { tipo, clase },
  estado: EstadoEndoso,
  otp: String,
  aprobaciones: [AprobacionActorDto],
  documentoUrl: String,
  documentoHtml: String,
  documentoPdf: BinData,
  nombreArchivoPdf: String,
  fechaCreacion: Date,
  fechaAprobacion: Date,
  fechaActualizacion: Date
}
```

#### pending_otps
```
{
  _id: ObjectId,
  idNegociacion: String,
  rutEmpresa: String,
  rutOperador: String,
  rolActor: String,
  fechaSolicitud: Date
}
```

#### outbox_events
```
{
  _id: ObjectId,
  tipo: String,
  payload: String (JSON),
  propietario: String,           // "ms-negociador-orq"
  enviado: Boolean,
  intentos: Integer,
  fechaCreacion: Date,
  fechaEnvio: Date
}
```

### MySQL — Tablas

| Tabla | Descripción | Campos clave |
|---|---|---|
| `users` | Usuarios y operadores del sistema | `ide_user`, `username` (RUT), `rol_user_ide`, `company_ide` |
| `companies` | Empresas, proveedores, pagadores, fondeadores | `ide_company`, `name_company`, `nit` |
| `empresa_quotas` | Cupos por empresa | `company_id`, `current_quota` |
| `fondeador_quotas` | Cupos por fondeador/proveedor/pagador | `funder_id`, `supplier_id`, `payer_id`, `supplier_current_quota`, `payer_current_quota` |
| `message_templates` | Plantillas de email | Pendiente por definir estructura exacta |

### Índices Detectados

- `facturas.numeroFactura` — búsqueda directa por número de factura.
- `negociaciones.id` — acceso por ID de negociación.
- `endosos.negociacionId` — consultas de endosos por negociación.

---

## 10. Seguridad

### Esquema de Autenticación

- **Tipo:** Bearer Token (JWT)
- **Header:** `Authorization: Bearer <token>`
- **Algoritmo:** HMAC-HS256
- **Librería:** `io.jsonwebtoken:jjwt-api:0.12.6`

### Estructura del Token JWT

| Claim | Tipo | Descripción |
|---|---|---|
| `sub` | String | Identificador del sujeto (username/RUT) |
| `rol` | String | Rol del usuario: `ADMINISTRADOR`, `EMPRESA`, `OPERARIO`, `FONDEADOR` |
| `iat` | Long | Fecha de emisión (Unix timestamp) |
| `exp` | Long | Fecha de expiración (Unix timestamp) |

> ⚠️ El claim es `rol` (singular), **no** `roles`. Solo se admite un rol por token.

### Estrategia de Autorización

La autorización se implementa con SpEL a través del bean `@sec` (`SecurityEvaluator`):

```java
@PreAuthorize("@sec.tieneRol(authentication, 'negociacion')")
```

Los grupos de roles permitidos por endpoint son totalmente configurables en `application.yml` bajo `security.roles.*`. No existen roles hardcodeados en el código.

### Roles por Endpoint

| Endpoint | Roles Permitidos |
|---|---|
| `POST /negociar/factura` | ADMINISTRADOR, EMPRESA, OPERARIO |
| `POST /negociaciones/{id}/endosos` | ADMINISTRADOR, EMPRESA, FONDEADOR |
| `POST /validaciones/facturas/vencimiento` | ADMINISTRADOR, OPERARIO, EMPRESA, FONDEADOR |
| `POST /negociaciones/solicitar-otp` | ADMINISTRADOR, EMPRESA, FONDEADOR |
| `POST /negociaciones/confirmar-otp` | ADMINISTRADOR, EMPRESA, FONDEADOR |

### Endpoints Públicos (sin autenticación)

```
GET  /swagger-ui/**
GET  /swagger-ui.html
GET  /v3/api-docs/**
```

### Respuestas de Error de Seguridad

- **401 Unauthorized** — Token ausente, expirado o con firma inválida. Manejado por `CustomAuthenticationEntryPoint`.
- **403 Forbidden** — Token válido pero el rol no tiene permiso. Manejado por `CustomAccessDeniedHandler`.

---

## 11. Observabilidad

### Spring Boot Actuator

Spring Boot Actuator está presente como dependencia. Los endpoints activos son **Pendiente por definir** en configuración explícita; se asume configuración por defecto de Spring Boot 3.x.

### Health Check

```
GET /actuator/health
```

Respuesta esperada:
```json
{
  "status": "UP"
}
```

### Métricas

- **JaCoCo**: Cobertura de código (umbral mínimo: 80% líneas, 85% branches).
- **SonarQube**: Calidad de código, deuda técnica y cobertura integrada.

### Logging

- Framework: SLF4J (Logback por defecto en Spring Boot).
- Lombok `@Slf4j` en clases que requieren logging.
- Nivel de log en producción: **Pendiente por definir** (no hay configuración explícita en `application-prod.yml`).

### Trazabilidad

- **Pendiente por definir**: No se detectó configuración de distributed tracing (Zipkin, Jaeger, Spring Cloud Sleuth/Micrometer Tracing).

---

## 12. Pruebas

### Pruebas Unitarias

Ubicación: `src/test/java/com/negociadororq/`

| Clase de prueba | Cobertura |
|---|---|
| `DomainExceptionsTest` | Jerarquía de excepciones de dominio |
| `GlobalExceptionHandlerTest` | Manejo global de excepciones HTTP |
| `AprobacionEndosoDescuentoServiceImplTest` | Lógica de aprobación de endosos |
| `DocumentoEndosoServiceImplTest` | Generación de documentos HTML/PDF |
| `JwtRolConverterTest` | Conversión de claims JWT a authorities |
| `SecurityEvaluatorTest` | Evaluación de roles con SpEL |

**Ejecutar:**
```bash
./gradlew test
```

### Pruebas de Arquitectura (ArchUnit)

Ubicación: `src/architecture-test/java/ArchitectureTest.java`

Reglas verificadas:

| Regla | Descripción |
|---|---|
| `domain_no_debe_depende_de_nadie` | El dominio no importa clases de application, web ni infraestructura |
| `application_solo_puede_depende_de_domain` | Application solo depende de domain, java, jakarta, spring y swagger |
| `infraestructura_solo_puede_depende_de_application_y_domain` | Infraestructura solo depende de capas internas |
| `web_solo_puede_depende_de_application` | La capa web no depende directamente de infraestructura |
| `controllers_deben_estar_en_web` | Clases `*Controller` solo en `..web.controller..` |
| `services_deben_estar_en_aplication_o_domain` | Clases `*Service` solo en capas correctas |
| `no_deberia_haber_ciclos_entre_capas` | Sin dependencias circulares entre capas |
| `domain_no_debe_usar_anotaciones_de_spring` | El dominio es agnóstico de Spring |

**Ejecutar:**
```bash
./gradlew architectureTest
```

### Pruebas de Aceptación (Karate)

Ubicación: `src/automated-test/`

| Runner | Tags | Descripción |
|---|---|---|
| `FullRunner` | `@full` | Suite completa de todos los escenarios |
| `SmokeRunner` | `@smoke` | Pruebas rápidas sin dependencia de BD |
| `RegressionRunner` | `@regression` | Flujos de negocio completos |
| `ProdRunner` | `@production-safe` | Solo operaciones de lectura, seguras para producción |

**Ejecutar:**
```bash
./gradlew automatedTest
```

### Pruebas de Rendimiento (JMeter)

Ubicación: `external-test/jmeter/`

| Archivo | Descripción |
|---|---|
| `negociador-orq-test-plan.jmx` | Plan de prueba parametrizable (1/10/100 hilos) |
| `desarrollo.properties` | Configuración para ambiente de desarrollo |

**Ejecutar:**
```bash
# Desde el directorio external-test/jmeter/
jmeter -n -t negociador-orq-test-plan.jmx -q desarrollo.properties
```

Parámetros configurables via `-J` o en el archivo `.properties`:

| Parámetro | Defecto | Descripción |
|---|---|---|
| `threads` | 1 | Número de hilos concurrentes |
| `rampup` | 1 | Tiempo de arranque en segundos |
| `loops` | 1 | Número de iteraciones |
| `env` | `desarrollo` | Ambiente (development, qa; producción solo lectura) |
| `token` | — | Token JWT para autenticación |

### Cobertura

Umbrales mínimos configurados en Gradle (JaCoCo):

| Métrica | Umbral mínimo |
|---|---|
| Líneas | 80% |
| Branches | 85% |

Clases excluidas de la cobertura: DTOs, enums, modelos de dominio, eventos y la clase `MsNegociadorOrqApplication`.

---

## 13. Estructura del Proyecto

```
ms-negociador-orq/
├── build.gradle
├── settings.gradle
├── config/
│   ├── checkstyle/
│   │   └── checkstyle.xml
│   ├── pmd/
│   │   └── ruleset.xml
│   └── spotbugs/
│       └── exclude.xml
├── external-test/
│   └── jmeter/
│       ├── negociador-orq-test-plan.jmx
│       └── desarrollo.properties
└── src/
    ├── main/
    │   ├── java/com/negociadororq/
    │   │   ├── MsNegociadorOrqApplication.java
    │   │   ├── application/
    │   │   │   ├── dto/                         # NegociacionRequestDto, EndosoResponseDto, etc.
    │   │   │   ├── exception/
    │   │   │   │   ├── dto/                     # ErrorResponse
    │   │   │   │   └── handler/                 # GlobalExceptionHandler
    │   │   │   ├── port/                        # Interfaces de puertos de salida
    │   │   │   ├── service/                     # NegociacionFactoringComfirmingServiceImpl, etc.
    │   │   │   ├── util/                        # IpUtils
    │   │   │   └── web/
    │   │   │       └── controller/              # 4 controladores REST
    │   │   ├── domain/
    │   │   │   ├── enums/                       # EstadoNegociacion, EstadoEndoso, EstadoFirma, etc.
    │   │   │   ├── event/                       # OtpRequestEvent, LiquidacionSolicitadaEvent, etc.
    │   │   │   └── model/                       # Negociacion, Factura, Endoso, etc.
    │   │   └── infrastructure/
    │   │       ├── kafka/
    │   │       │   ├── config/                  # Configuración de Kafka
    │   │       │   ├── consumer/                # LiquidacionFallidaConsumer, OtpValidatedListener
    │   │       │   └── producer/                # Publicadores de eventos
    │   │       ├── persistence/
    │   │       │   ├── mongodb/                 # Adapters y repositories MongoDB
    │   │       │   └── mysql/                   # Adapters y repositories JPA/MySQL
    │   │       ├── scheduler/                   # OutboxScheduler, FacturaVencimientoScheduler
    │   │       ├── security/                    # JjwtDecoder, JwtRolConverter, SecurityEvaluator
    │   │       │   └── config/                  # SecurityConfig
    │   │       └── web/
    │   │           └── OpenApiConfig.java
    │   └── resources/
    │       ├── application.yml
    │       ├── application-prod.yml
    │       └── templates/                       # Plantillas HTML para endosos
    ├── test/
    │   └── java/com/negociadororq/              # JUnit 5 + Mockito
    ├── automated-test/
    │   └── java/com/negociadororq/runner/       # Karate runners (Full, Smoke, Regression, Prod)
    │   └── resources/features/                  # Escenarios Karate (.feature)
    └── architecture-test/
        └── java/
            └── ArchitectureTest.java
```

---

## 14. Diagramas

### Arquitectura General

```mermaid
graph TB
    subgraph "Clientes"
        C1[Frontend / API Gateway]
        C2[Otros Microservicios]
    end

    subgraph "ms-negociador-orq"
        subgraph "Application Layer"
            CTRL[REST Controllers]
            SVC[Services]
            PORT[Output Ports]
        end
        subgraph "Domain Layer"
            MODEL[Domain Models]
            ENUM[Enumeraciones]
            EVENT[Domain Events]
        end
        subgraph "Infrastructure Layer"
            SEC[Security / JWT]
            MONGO_A[MongoDB Adapters]
            MYSQL_A[MySQL Adapters]
            KAFKA_A[Kafka Producers/Consumers]
            SCHED[Schedulers / Outbox]
            OPENAPI[OpenAPI Config]
        end
    end

    subgraph "Bases de Datos"
        MONGO[(MongoDB\nNegociaciones\nFacturas\nEndosos)]
        MYSQL[(MySQL\nUsuarios\nEmpresas\nCupos)]
    end

    subgraph "Mensajería"
        KAFKA[Apache Kafka]
    end

    subgraph "Servicios Externos"
        OTP_SVC[Servicio OTP]
        LIQ_SVC[Servicio Liquidación]
        EMAIL_SVC[Servicio Notificaciones]
    end

    C1 -->|HTTP + JWT| CTRL
    C2 -->|HTTP + JWT| CTRL
    CTRL --> SVC
    SVC --> PORT
    SVC --> MODEL
    PORT --> MONGO_A
    PORT --> MYSQL_A
    PORT --> KAFKA_A
    MONGO_A --> MONGO
    MYSQL_A --> MYSQL
    KAFKA_A --> KAFKA
    SCHED --> KAFKA
    KAFKA --> OTP_SVC
    KAFKA --> LIQ_SVC
    KAFKA --> EMAIL_SVC
    OTP_SVC -->|otp.validate.response| KAFKA_A
    LIQ_SVC -->|liquidacion.fallida| KAFKA_A
```

### Flujo Principal de Negocio

```mermaid
sequenceDiagram
    participant C as Cliente
    participant API as ms-negociador-orq
    participant DB_M as MongoDB
    participant DB_S as MySQL
    participant K as Kafka

    Note over C,K: 1. Crear Negociación
    C->>+API: POST /negociar/factura
    API->>DB_S: Validar operador existe
    loop Por cada factura
        API->>DB_M: Consultar factura
        API->>DB_S: Validar cupos (cascada)
        API->>DB_S: Descontar cupos
        API->>DB_M: Actualizar estado factura
    end
    API->>DB_M: Guardar Negociacion
    API->>DB_M: Guardar OutboxEvent (notificacion)
    API-->>-C: 201 NegociacionResponseDto

    Note over C,K: 2. Registrar Endoso
    C->>+API: POST /negociaciones/{id}/endosos
    API->>DB_M: Buscar negociación
    API->>DB_M: Validar facturas
    API->>DB_M: Guardar Endoso (EN_REVISION)
    API->>DB_M: Guardar OutboxEvent (notificacion)
    API-->>-C: 201 EndosoResponseDto

    Note over C,K: 3. Aprobación OTP (asíncrona)
    C->>+API: POST /negociaciones/solicitar-otp
    API->>DB_M: Guardar PendingOtp
    API->>DB_M: Guardar OutboxEvent (otp.requested)
    API-->>-C: 202 Accepted
    K-->>API: otp.validate.response (OTP válido)
    API->>DB_M: Actualizar estados (CESION/DESCUENTO)
    API->>DB_M: Guardar OutboxEvent (liquidacion.solicitada)
```

### Flujo de una Solicitud HTTP

```mermaid
sequenceDiagram
    participant C as Cliente HTTP
    participant SF as Spring Security Filter
    participant JD as JjwtDecoder
    participant JC as JwtRolConverter
    participant SE as SecurityEvaluator
    participant CTRL as Controller
    participant SVC as Service
    participant REPO as Repository

    C->>SF: HTTP Request + Authorization header
    SF->>JD: Extraer y validar JWT
    JD-->>SF: Resultado validación
    SF->>JC: Convertir claim 'rol' a GrantedAuthority
    JC-->>SF: Collection<GrantedAuthority>
    SF->>CTRL: SecurityContext poblado
    CTRL->>SE: @PreAuthorize("@sec.tieneRol(auth, 'grupo')")
    SE-->>CTRL: Autorización concedida / denegada
    alt Autorizado
        CTRL->>SVC: Llamar servicio
        SVC->>REPO: Persistencia / consultas
        REPO-->>SVC: Datos
        SVC-->>CTRL: Response DTO
        CTRL-->>C: HTTP 2xx + Body JSON
    else No autorizado
        CTRL-->>C: HTTP 403 + ErrorResponse
    end
```

### Dependencias entre Capas

```mermaid
graph LR
    subgraph "Capa Web (..web..)"
        CTRL[Controllers\nOpenApiConfig]
    end
    subgraph "Capa Application (..application..)"
        SVC[Services]
        DTO[DTOs]
        PORT[Ports]
        EXC[Exceptions]
    end
    subgraph "Capa Domain (..domain..)"
        MODEL[Models]
        ENUM[Enums]
        EVENT[Events]
    end
    subgraph "Capa Infrastructure (..infrastructure..)"
        PERSIST[Persistence Adapters]
        KAFKA_I[Kafka Adapters]
        SEC_I[Security Config]
        SCHED_I[Schedulers]
    end

    CTRL --> SVC
    CTRL --> DTO
    SVC --> PORT
    SVC --> MODEL
    SVC --> EXC
    PORT --> MODEL
    PERSIST --> PORT
    PERSIST --> MODEL
    KAFKA_I --> EVENT
    KAFKA_I --> SVC
    SEC_I --> PORT
    SCHED_I --> PORT

    MODEL --> ENUM
    MODEL --> EVENT

    style CTRL fill:#dce8f5
    style SVC fill:#dce8f5
    style DTO fill:#dce8f5
    style PORT fill:#dce8f5
    style EXC fill:#dce8f5
    style MODEL fill:#d5f5e3
    style ENUM fill:#d5f5e3
    style EVENT fill:#d5f5e3
    style PERSIST fill:#fde8d8
    style KAFKA_I fill:#fde8d8
    style SEC_I fill:#fde8d8
    style SCHED_I fill:#fde8d8
```

---

## 15. Ambientes

### Desarrollo

| Propiedad | Valor |
|---|---|
| Puerto | `8083` |
| MySQL | `localhost:3306/dbcompany` (usuario: `root`, contraseña: `root`) |
| MongoDB | `mongodb://localhost:27017/DB_Facturas` |
| Kafka | `localhost:9092` |
| Swagger UI | Habilitado en `http://localhost:8083/swagger-ui.html` |
| DDL Hibernate | `update` (crea/actualiza tablas automáticamente) |
| Show SQL | `true` |

### QA

| Propiedad | Valor |
|---|---|
| MySQL | `${DB_HOST}/dbcompany` (credenciales por variable de entorno) |
| MongoDB | `${MONGO_URI}` / `${MONGO_DB}` (defecto: `DB_Facturas_QA`) |
| Kafka | `${KAFKA_SERVERS}` (defecto: `localhost:9092`) |
| Swagger UI | Habilitado |
| DDL Hibernate | `update` |

### Producción

| Propiedad | Valor |
|---|---|
| MySQL | `${DB_HOST}/dbcompany?useSSL=true` (todas las credenciales por variables de entorno) |
| MongoDB | `${MONGO_URI}` (obligatorio) |
| Kafka | `${KAFKA_SERVERS}` (obligatorio) |
| Swagger UI | **Deshabilitado** (`springdoc.swagger-ui.enabled: false`) |
| OpenAPI docs | **Deshabilitado** (`springdoc.api-docs.enabled: false`) |
| DDL Hibernate | `validate` (no modifica el esquema) |
| Show SQL | `false` |
| HikariCP max pool | `20` |
| HikariCP min idle | `5` |

---

## 16. Troubleshooting

### Puerto 8083 Ocupado

**Síntoma:** `Port 8083 was already in use.`

**Solución:**
```bash
# En Windows
netstat -ano | findstr :8083
taskkill /PID <PID> /F

# En Linux/Mac
lsof -i :8083
kill -9 <PID>
```

O cambiar el puerto en `application.yml`:
```yaml
server:
  port: 8084
```

---

### Error de Conexión a MongoDB

**Síntoma:** `com.mongodb.MongoTimeoutException: Timed out after 30000 ms while waiting to connect`

**Causas y Soluciones:**
1. MongoDB no está corriendo → Iniciar el servicio MongoDB.
2. URI incorrecta → Verificar `spring.data.mongodb.uri` en el perfil activo.
3. Credenciales incorrectas → Verificar usuario y contraseña en la URI.
4. Firewall bloqueando el puerto 27017 → Revisar reglas de red.

---

### Error de Conexión a MySQL

**Síntoma:** `com.mysql.cj.jdbc.exceptions.CommunicationsException: Communications link failure`

**Causas y Soluciones:**
1. MySQL no está corriendo → Iniciar el servicio MySQL.
2. Host/puerto incorrecto → Verificar `spring.datasource.url`.
3. Base de datos `dbcompany` no existe → Crear la base de datos.
4. Credenciales incorrectas → Verificar usuario y contraseña.

---

### Error JWT — 401 Unauthorized

**Síntoma:** Respuesta `{"status": 401, "codigo": "UNAUTHORIZED", ...}`

**Causas y Soluciones:**
1. Token expirado → Generar un nuevo token con `JwtTestTokenGenerator`.
2. Header mal formado → Asegurarse de usar `Authorization: Bearer <token>` (con espacio después de "Bearer").
3. Secret incorrecto → Verificar que el token fue firmado con el mismo secret configurado en `security.jwt.secret`.
4. Claim `rol` ausente → El token debe incluir el claim `rol` (no `roles`).
5. Token generado con otro algoritmo → Solo se acepta HS256.

---

### Swagger No Disponible

**Síntoma:** `404 Not Found` en `/swagger-ui.html`

**Causas y Soluciones:**
1. Perfil `prod` activo → Swagger está deshabilitado en producción por diseño.
2. Dependencia faltante → Verificar que `springdoc-openapi-starter-webmvc-ui` está en `build.gradle`.
3. `springdoc.swagger-ui.enabled: false` en el yml activo → Cambiar a `true`.

---

### Error de Checkstyle — `NewlineAtEndOfFile`

**Síntoma:** `BUILD FAILED` con mensaje `El fichero no termina con un retorno de carro.`

**Solución:** Asegurarse de que todos los archivos Java terminen con una línea en blanco (`\n`). La mayoría de IDEs tienen opción para configurarlo automáticamente al guardar.

---

### Kafka — Eventos No Publicados

**Síntoma:** Los eventos de dominio no llegan a los consumidores externos.

**Causas y Soluciones:**
1. Kafka no está corriendo → Iniciar el broker.
2. Topics no creados → Crear los topics listados en la sección de [Requisitos Previos](#3-requisitos-previos).
3. Outbox no procesando → Verificar que `OutboxScheduler` esté activo (ejecuta cada 5 segundos). Revisar la colección `outbox_events` en MongoDB para ver si hay eventos con más de 5 intentos fallidos.

---

### Violaciones de Arquitectura (ArchUnit)

**Síntoma:** `BUILD FAILED` en `architectureTest` con `Architecture Violation`

**Causas y Soluciones:**
1. Dependencia no permitida entre capas → Revisar las reglas en `ArchitectureTest.java` y ajustar la ubicación del código.
2. Controller fuera del paquete `web.controller` → Mover la clase al paquete correcto.
3. Librería nueva no incluida en las reglas → Agregar el paquete de la librería a la lista de permitidos en la regla correspondiente.

---

## 17. Consideraciones Técnicas

### Decisiones Arquitectónicas

#### 1. Arquitectura Hexagonal
Se eligió Ports & Adapters para aislar la lógica de negocio de los detalles de infraestructura. Esto permite reemplazar MongoDB, MySQL o Kafka sin modificar los servicios de aplicación. La interfaz `Port` actúa como contrato entre capas.

#### 2. Patrón Outbox para Eventos Kafka
Los eventos de dominio no se publican directamente a Kafka sino que se persisten primero en MongoDB (`outbox_events`) y son enviados por el `OutboxScheduler` cada 5 segundos. Esto garantiza **consistencia eventual** entre la persistencia y la mensajería: si Kafka no está disponible temporalmente, los eventos se enviarán cuando el broker se recupere.

#### 3. Transacciones `REQUIRES_NEW` por Factura
El procesamiento de cada factura en una negociación se ejecuta en una transacción independiente (`Propagation.REQUIRES_NEW`). Esto permite el **fallo parcial**: una factura con cupo insuficiente no revierte las facturas ya procesadas exitosamente.

#### 4. Roles Externalizados en YML
Los grupos de roles permitidos por endpoint están en `application.yml` y no en el código. Esto permite ajustar permisos sin recompilar, aunque requiere redeploy del servicio.

#### 5. Aprobación Completamente Asíncrona
El flujo de OTP devuelve `202 Accepted` inmediatamente. La validación y aprobación final ocurre cuando el servicio externo de OTP responde por Kafka. Esto previene timeouts en flujos de larga duración pero requiere que los clientes implementen polling o webhooks para conocer el resultado final.

### Patrones Utilizados

| Patrón | Dónde se Aplica |
|---|---|
| Hexagonal / Ports & Adapters | Arquitectura general del microservicio |
| Outbox Pattern | Publicación garantizada de eventos Kafka |
| Repository Pattern | Acceso a datos MongoDB y MySQL |
| Factory / Builder | Construcción de DTOs y entidades (Lombok @Builder) |
| Strategy | Lógica diferenciada por tipo de operación (FACTORING vs CONFIRMING) |
| Chain of Responsibility | Validación en cascada de cupos |
| Observer (via Kafka) | Reacción a eventos de dominio en servicios externos |