# Estándar Corporativo de Respuestas HTTP — Plataforma Factoring

**Versión:** 1.0  
**Fecha:** 2026-06-05  
**Aplica a:** Todos los microservicios de la plataforma

---

## 1. Catálogo de Códigos HTTP

### Éxito (2xx)

| Código | Nombre | Cuándo usar |
|--------|--------|-------------|
| **200 OK** | OK | GET, POST de consulta/validación, PUT/PATCH actualizaciones. Respuesta con cuerpo. |
| **201 Created** | Created | POST que persiste un nuevo recurso. Incluir el recurso creado en el cuerpo. |
| **202 Accepted** | Accepted | POST de operaciones asíncronas (Kafka, eventos). El procesamiento real ocurre después. |
| **204 No Content** | No Content | DELETE exitoso, o actualización sin necesidad de devolver cuerpo. |

### Errores del cliente (4xx)

| Código | Nombre | Cuándo usar |
|--------|--------|-------------|
| **400 Bad Request** | Bad Request | Petición malformada, campos requeridos ausentes, tipo de operación no informado. |
| **401 Unauthorized** | Unauthorized | Token ausente o expirado (autenticación faltante). |
| **403 Forbidden** | Forbidden | Autenticado pero sin autorización para la operación de negocio. |
| **404 Not Found** | Not Found | Entidad no encontrada por su identificador. |
| **405 Method Not Allowed** | Method Not Allowed | Verbo HTTP incorrecto para el endpoint. |
| **409 Conflict** | Conflict | Recurso duplicado, estado incompatible (ej: endoso ya existe). |
| **422 Unprocessable Entity** | Unprocessable Entity | Petición válida sintácticamente pero viola regla de negocio (cupo, estado, permiso de rol). |
| **429 Too Many Requests** | Too Many Requests | Rate limiting excedido. |

### Errores del servidor (5xx)

| Código | Nombre | Cuándo usar |
|--------|--------|-------------|
| **500 Internal Server Error** | Internal Server Error | Error inesperado no manejado. NUNCA exponer detalles internos al cliente. |
| **503 Service Unavailable** | Service Unavailable | Dependencia externa no disponible (BD, Kafka caído). |

---

## 2. Estructura Estándar de Respuesta de Error

```json
{
  "timestamp": "2026-06-05T10:30:00.123",
  "status": 404,
  "error": "Not Found",
  "codigo": "FACTURA_NO_ENCONTRADA",
  "mensaje": "Factura no encontrada: FAC-2024-001",
  "path": "/validaciones/facturas/vencimiento",
  "detalles": null
}
```

### Campo `detalles` — solo en errores de validación (400 VALIDACION_FALLIDA):

```json
{
  "timestamp": "2026-06-05T10:30:00.123",
  "status": 400,
  "error": "Bad Request",
  "codigo": "VALIDACION_FALLIDA",
  "mensaje": "Errores de validación en el cuerpo de la petición",
  "path": "/negociar/factura",
  "detalles": [
    "rutEmpresa: no debe estar vacío",
    "facturas: la lista no puede ser nula"
  ]
}
```

### Reglas del campo `codigo`:
- Formato: `SNAKE_UPPER_CASE`
- Descriptivo del recurso o acción que falló
- Estable entre versiones (los clientes pueden hacer switch sobre este campo)

---

## 3. Catálogo de Excepciones Corporativas

### Jerarquía

```
NegociadorException (abstract, RuntimeException)
├── RecursoNoEncontradoException  → HTTP 404
├── ReglaDeNegocioException       → HTTP 422
├── ConflictoException            → HTTP 409
├── AccionNoPermitidaException    → HTTP 403
└── PeticionInvalidaException     → HTTP 400
```

### Catálogo de códigos de error de dominio

| Código                      | Excepción                    | HTTP | Descripción |
|-----------------------------|------------------------------|------|-------------|
| `FACTURA_NO_ENCONTRADA`     | RecursoNoEncontradoException | 404  | Factura no existe por número |
| `NEGOCIACION_NO_ENCONTRADA` | RecursoNoEncontradoException | 404  | Negociación no existe por ID |
| `OPERADOR_NO_ENCONTRADO`    | RecursoNoEncontradoException | 404  | Operador (usuario) no existe por RUT |
| `EMPRESA_NO_ENCONTRADA`     | RecursoNoEncontradoException | 404  | Empresa no existe por NIT |
| `FACTURAS_REQUERIDAS`       | PeticionInvalidaException    | 400  | Lista de facturas vacía o nula |
| `PETICION_INVALIDA`         | PeticionInvalidaException    | 400  | Campo requerido faltante o inválido |
| `VALIDACION_FALLIDA`        | (Spring MVC)                 | 400  | Error de @Valid en el body |
| `CUERPO_INVALIDO`           | (Spring MVC)                 | 400  | JSON malformado |
| `METODO_NO_PERMITIDO`       | (Spring MVC)                 | 405  | Verbo HTTP incorrecto |
| `ACCION_NO_PERMITIDA`       | AccionNoPermitidaException   | 403  | Actor sin permiso para la operación |
| `ENDOSO_DUPLICADO`          | ConflictoException           | 409  | Actor ya endosó esa factura |
| `CONFLICTO`                 | ConflictoException           | 409  | Estado incompatible genérico |
| `CUPO_INSUFICIENTE`         | ReglaDeNegocioException      | 422  | Cupo insuficiente (operador/empresa/fondeador) |
| `FACTURA_NO_NEGOCIABLE`     | ReglaDeNegocioException      | 422  | Factura en estado que impide negociación |
| `ROL_ACTOR_INDETERMINADO`   | ReglaDeNegocioException      | 422  | No se puede determinar el rol del actor |
| `REGLA_DE_NEGOCIO_VIOLADA`  | ReglaDeNegocioException      | 422  | Regla de negocio genérica violada |
| `ERROR_INTERNO`             | (fallback)                   | 500  | Error no manejado — sin detalles internos |

---

## 4. Estrategia GlobalExceptionHandler Reutilizable

### Estructura de archivos (por cada microservicio)

```
src/main/java/com/<ms>/
├── application/
│   └── exception/
│       ├── NegociadorException.java          ← base abstract
│       ├── RecursoNoEncontradoException.java ← 404
│       ├── ReglaDeNegocioException.java      ← 422
│       ├── ConflictoException.java           ← 409
│       ├── AccionNoPermitidaException.java   ← 403
│       ├── PeticionInvalidaException.java    ← 400
│       ├── dto/
│       │   └── ErrorResponse.java            ← estructura estándar
│       └── handler/
│           └── GlobalExceptionHandler.java   ← @RestControllerAdvice
```

### Instrucciones de reutilización

1. Copiar el package `application.exception` completo al nuevo microservicio.
2. Cambiar el package root (`com.negociadororq` → `com.nuevo-ms`).
3. Registrar en Spring Boot automáticamente por `@RestControllerAdvice`.
4. Agregar `@ExceptionHandler` adicionales si el MS tiene excepciones específicas.
5. NO modificar `ErrorResponse.java` — garantiza contrato uniforme entre todos los MS.

---

## 5. Convenciones para Endpoints REST

### Mapeo de operación → código HTTP

| Operación | Verbo | Código éxito | Ejemplo |
|-----------|-------|--------------|---------|
| Crear recurso | POST | **201 Created** | `POST /negociar/factura` |
| Operación asíncrona | POST | **202 Accepted** | `POST /negociaciones/solicitar-otp` |
| Consulta/validación | POST/GET | **200 OK** | `POST /validaciones/facturas/vencimiento` |
| Actualización parcial | PATCH | **200 OK** | `PATCH /negociaciones/{id}` |
| Reemplazo completo | PUT | **200 OK** | `PUT /negociaciones/{id}` |
| Eliminar | DELETE | **204 No Content** | `DELETE /negociaciones/{id}` |

### Regla para operaciones asíncronas (Kafka/Outbox)

Si el endpoint solo **encola** la operación (publica a Kafka, guarda en Outbox) y el resultado real
se procesa de forma asíncrona → **siempre usar 202 Accepted**.

Incluir en la documentación del endpoint qué mecanismo usa el cliente para conocer el resultado
(polling, webhook, otro evento).

---

## 6. Convenciones para Excepciones en Servicios

### Regla de oro

> **Solo las excepciones que NO son capturadas por otro servicio (y que llegan al controller) 
> necesitan ser excepciones personalizadas de la jerarquía.**

### Cuándo usar cada excepción

```java
// ✅ CORRECTO — entidad no existe
.orElseThrow(() -> new RecursoNoEncontradoException("Factura no encontrada: " + id, "FACTURA_NO_ENCONTRADA"));

// ✅ CORRECTO — regla de negocio (cupo, estado, etc.)
if (cupo.compareTo(valor) < 0) {
    throw new ReglaDeNegocioException("Cupo insuficiente", "CUPO_INSUFICIENTE");
}

// ✅ CORRECTO — permiso de negocio (no es 401/403 de auth, es autorización de dominio)
if (!empresa.equals(proveedor)) {
    throw new AccionNoPermitidaException("Solo el proveedor puede hacer FACTORING", "ACCION_NO_PERMITIDA");
}

// ✅ CORRECTO — duplicado
if (yaExiste) {
    throw new ConflictoException("El endoso ya existe para esta factura", "ENDOSO_DUPLICADO");
}

// ✅ CORRECTO — input inválido de negocio
if (facturas.isEmpty()) {
    throw new PeticionInvalidaException("Debe indicar al menos una factura", "FACTURAS_REQUERIDAS");
}

// ❌ INCORRECTO — no usar RuntimeException genérico en servicios que llegan al controller
throw new RuntimeException("No se encontró la factura");
```

### Excepciones internas (caught por otro servicio)

Si una excepción es capturada internamente por otro servicio (patrón try/catch de negocio),
puede seguir siendo `RuntimeException`. El `GlobalExceptionHandler` nunca la verá.

```java
// CORRECTO — esta excepción es capturada por el padre y tratada como rechazo de factura
try {
    procesarFactura(request, facturaReq);
} catch (Exception e) {
    errores.add(e.getMessage()); // se convierte en dato del response, no en error HTTP
}
```

---

## 7. Resumen de Cambios Implementados en ms-negociador-orq

### Endpoints modificados

| Endpoint | Antes | Después | Justificación |
|----------|-------|---------|---------------|
| `POST /negociar/factura` | 200 OK | **201 Created** | Crea un nuevo recurso `Negociacion` en MongoDB |
| `POST /negociaciones/{id}/endosos` | 200 OK | **201 Created** | Crea nuevos recursos `Endoso` en MongoDB |
| `POST /negociaciones/solicitar-otp` | 200 OK | **202 Accepted** | Solo encola evento Kafka/Outbox (asíncrono) |
| `POST /negociaciones/confirmar-otp` | 200 OK | **202 Accepted** | Solo encola evento Kafka/Outbox (asíncrono) |
| `POST /validaciones/facturas/vencimiento` | 200 OK (sin wrapper) | **200 OK** (con ResponseEntity) | Consulta/validación — mantiene 200, agrega wrapper |

### Excepciones reemplazadas

| Servicio | Mensaje original | Excepción nueva | HTTP |
|----------|-----------------|-----------------|------|
| `ValidacionFacturaServiceImpl` | "Factura no encontrada: {}" | RecursoNoEncontradoException / FACTURA_NO_ENCONTRADA | 404 |
| `NegociacionFactoringComfirmingServiceImpl` | "No existe operador con username {}" | RecursoNoEncontradoException / OPERADOR_NO_ENCONTRADO | 404 |
| `NegociacionFactoringComfirmingServiceImpl` | "Factura no encontrada: {}" (pre-loop) | RecursoNoEncontradoException / FACTURA_NO_ENCONTRADA | 404 |
| `NegociacionFactoringComfirmingServiceImpl` | "No existe company con NIT {}" | RecursoNoEncontradoException / EMPRESA_NO_ENCONTRADA | 404 |
| `EndosoDescuentoServiceImpl` | "Negociación no encontrada" | RecursoNoEncontradoException / NEGOCIACION_NO_ENCONTRADA | 404 |
| `EndosoDescuentoServiceImpl` | "Debe indicar al menos una factura" | PeticionInvalidaException / FACTURAS_REQUERIDAS | 400 |
| `AprobacionEndosoDescuentoServiceImpl` | "Negociación no encontrada" | RecursoNoEncontradoException / NEGOCIACION_NO_ENCONTRADA | 404 |
| `AprobacionEndosoDescuentoServiceImpl` | "El actor ya realizó el endoso" | ConflictoException / ENDOSO_DUPLICADO | 409 |
| `AprobacionEndosoDescuentoServiceImpl` | "No se pudo determinar el rol del actor" | ReglaDeNegocioException / ROL_ACTOR_INDETERMINADO | 422 |

### Excepciones NO reemplazadas (internas — capturadas por servicios padre)

Las siguientes excepciones en `ProcesarFacturaNegociacionServiceImpl` y `OperadorServiceImpl`
se mantienen como `RuntimeException` porque son capturadas por el try/catch de negocio en
`NegociacionFactoringComfirmingServiceImpl.crearNegociacion()` y se convierten en datos del
response (campo `errores`), nunca llegando al `GlobalExceptionHandler`.

---

## 8. Posibles Impactos en Consumidores Externos

### ⚠ Cambios que PUEDEN romper compatibilidad

| Cambio | Impacto | Mitigación |
|--------|---------|------------|
| `POST /negociar/factura`: 200 → 201 | Clientes que hacen `if (status == 200)` fallarán | Clientes deben aceptar rango `2xx` en vez de código exacto |
| `POST /negociaciones/{id}/endosos`: 200 → 201 | Igual que el anterior | Igual |
| `POST /negociaciones/solicitar-otp`: 200 → 202 | Igual que el anterior | Igual |
| `POST /negociaciones/confirmar-otp`: 200 → 202 | Igual que el anterior | Igual |

### ✅ Cambios que NO rompen compatibilidad

| Cambio | Por qué es seguro |
|--------|-------------------|
| `POST /validaciones/facturas/vencimiento`: sin ResponseEntity → con ResponseEntity 200 | El código HTTP sigue siendo 200, solo se agrega el wrapper |
| Errores 500 → códigos específicos (400, 404, 409, 422) | Los errores antes no tenían estructura útil. Los clientes que solo verifican `>= 400` no se ven afectados |
| Estructura `ErrorResponse` estándar | Reemplaza mensajes genéricos de Spring. Si el cliente parseaba el body de error, debe actualizarse |

### Recomendación para consumidores

```
// Incorrecto (frágil ante cambios de HTTP code)
if (response.status === 200) { ... }

// Correcto (tolerante a 2xx)
if (response.ok) { ... }          // fetch API
if (response.status < 300) { ... } // genérico
```