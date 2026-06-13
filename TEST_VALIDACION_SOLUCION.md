# Test de Validación: Estado del Endoso Independiente de Liquidación

## Caso 1: FACTORING - Liquidación Falla después de Aceptación

### Setup
- Operación: FACTORING
- Actores: PAGADOR (900PAG), FONDEADOR (700FOND)
- Requeridos: {PAGADOR, FONDEADOR}

### Pasos
1. Crear negociación FACTORING
2. Crear endoso → Estado: EN_REVISION
3. PAGADOR aprueba → Aprobaciones: [PAGADOR=true]
4. FONDEADOR aprueba → Aprobaciones: [PAGADOR=true, FONDEADOR=true]
   - ✓ Todos los actores requeridos aprobaron
   - EstadoEndoso → ACEPTADO
   - Publica LiquidacionSolicitadaEvent
5. ms-liquidador-neg falla al validar vencimiento (401 Unauthorized)
   - Publica LiquidacionFallidaEvent
   - ReversionCuposServiceImpl se ejecuta

### Verificación
- EstadoEndoso = ACEPTADO ✓ (no cambió)
- EstadoFirma = RECHAZADA ✓ (cambió por liquidación fallida)

---

## Caso 2: CONFIRMING - Liquidación Falla después de Aceptación

### Setup
- Operación: CONFIRMING
- Actores: PROVEEDOR (800PROV), PAGADOR (900PAG)
- Requeridos: {PROVEEDOR}

### Pasos
1. Crear negociación CONFIRMING
2. Crear endoso → Estado: EN_REVISION
3. PROVEEDOR aprueba → Aprobaciones: [PROVEEDOR=true]
   - ✓ El único actor requerido aprobó
   - EstadoEndoso → ACEPTADO
   - Publica LiquidacionSolicitadaEvent
4. ms-liquidador-neg falla
   - ReversionCuposServiceImpl se ejecuta

### Verificación
- EstadoEndoso = ACEPTADO ✓ (no cambió)
- EstadoFirma = RECHAZADA ✓ (cambió por liquidación fallida)

---

## Caso 3: FACTORING - Rechazo de Actor Requerido

### Setup
- Operación: FACTORING
- Actores: PAGADOR (900PAG), FONDEADOR (700FOND)
- Requeridos: {PAGADOR, FONDEADOR}

### Pasos
1. Crear negociación FACTORING
2. Crear endoso → Estado: EN_REVISION
3. PAGADOR rechaza → Aprobaciones: [PAGADOR=false]
   - ❌ Actor requerido rechazó
   - EstadoEndoso → RECHAZADO
   - EstadoFirma → RECHAZADA

### Verificación
- EstadoEndoso = RECHAZADO ✓ (por rechazo de PAGADOR)
- EstadoFirma = RECHAZADA ✓

---

## Caso 4: CONFIRMING - Rechazo de Actor Requerido

### Setup
- Operación: CONFIRMING
- Actores: PROVEEDOR (800PROV)
- Requeridos: {PROVEEDOR}

### Pasos
1. Crear negociación CONFIRMING
2. Crear endoso → Estado: EN_REVISION
3. PROVEEDOR rechaza → Aprobaciones: [PROVEEDOR=false]
   - ❌ El único actor requerido rechazó
   - EstadoEndoso → RECHAZADO
   - EstadoFirma → RECHAZADA

### Verificación
- EstadoEndoso = RECHAZADO ✓ (por rechazo de PROVEEDOR)
- EstadoFirma = RECHAZADA ✓

---

## Resumen de Cambios de Código

### Archivo 1: ReversionCuposServiceImpl.java
- Removidas líneas 203-207 que cambiaban EstadoEndoso
- Ahora solo cambia EstadoFirma a RECHAZADA

### Archivo 2: AprobacionEndosoDescuentoServiceImpl.java
- Cambiado línea 215-219
- Ahora filtra rechazos solo de actores REQUERIDOS
- Movida inicialización de `actoresRequeridos` antes del chequeo de rechazo

### Archivo 3: ReversionCuposServiceImplTest.java
- Test actualizado para verificar que NO se llama actualizarEstadoEndosoPorNoFactura
- Verifica con `verify(endosoPatchPort, never()).actualizarEstadoEndosoPorNoFactura(...)`

---

## Resultado Esperado

✅ El estado del endoso es **INDEPENDIENTE** de la liquidación
- Refleja SOLO las decisiones de aprobación de los actores
- Los cambios en liquidación NO afectan el estado del endoso
- El estado de firma sí refleja el resultado de la liquidación
