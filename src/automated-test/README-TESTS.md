# Pruebas Funcionales — ms-negociador-orq

Suite de pruebas funcionales automatizadas con [Karate Framework](https://github.com/karatelabs/karate) sobre los endpoints REST del microservicio.

---

## Estructura

```
src/automated-test/
├── java/com/negociadororq/
│   ├── auth/JwtTestHelper.java         ← genera tokens HS384 desde base64 secret
│   └── runner/
│       ├── SmokeRunner.java            ← @smoke
│       ├── RegressionRunner.java       ← @regression
│       ├── FullRunner.java             ← @full  (task por defecto)
│       └── ProdRunner.java             ← @production-safe
└── resources/
    ├── karate-config.js                ← TODA la logica de config (dev/qa/prod en un solo archivo)
    ├── karate-config-dev.js            ← stub vacio (requerido por Karate, logica en karate-config.js)
    ├── karate-config-qa.js             ← stub vacio
    ├── karate-config-prod.js           ← stub vacio
    └── com/negociadororq/
        ├── security/security.feature       @smoke @regression @full @production-safe
        ├── validacion/validacion.feature   @smoke @regression @full @production-safe
        ├── negociacion/negociacion.feature @regression @full
        ├── endoso/endoso.feature           @regression @full
        └── otp/
            ├── solicitar-otp.feature       @regression @full
            └── confirmar-otp.feature       @full
```

---

## Endpoints cubiertos

| Endpoint | Método | Roles | Tags mínimos |
|---|---|---|---|
| `/validaciones/facturas/vencimiento` | POST | ADMIN, OPERARIO, EMPRESA, FONDEADOR | @smoke |
| `/negociar/factura` | POST | ADMIN, EMPRESA, OPERARIO | @regression |
| `/negociaciones/{id}/endosos` | POST | ADMIN, EMPRESA, FONDEADOR | @regression |
| `/negociaciones/solicitar-otp` | POST | ADMIN, EMPRESA, FONDEADOR | @regression |
| `/negociaciones/confirmar-otp` | POST | ADMIN, EMPRESA, FONDEADOR | @full |

---

## Ejecución local (DEV)

**Prerequisito:** microservicio corriendo en `http://localhost:8083`

```bash
# Suite completa (FullRunner - @full)
./gradlew automatedTest

# Solo smoke (más rápido, sin datos en BD)
./gradlew smokeTest

# Regression (flujos de negocio)
./gradlew regressionTest
```

---

## Ejecución en QA (CI/CD)

```bash
export AMBIENTE_PIPE=qa
export BASE_URL=https://ms-negociador-orq.qa.internal
export JWT_SECRET=<secret-del-ambiente-qa>

# Datos de prueba (opcional — el smoke no los necesita)
export TEST_RUT_EMPRESA=800111222-9
export TEST_RUT_OPERADOR=12345678-9
export TEST_RUT_FONDEADOR=700999888-5
export TEST_FACTURA_NUM=FAC-QA-001
export TEST_FECHA_PROCESO=2024-06-01
export TEST_NEG_ID=NEG-QA-00001

./gradlew automatedTest        # suite completa
./gradlew regressionTest       # regression
./gradlew smokeTest            # solo smoke
```

---

## Ejecución en PRODUCCIÓN

Solo se ejecutan tests `@production-safe` (lectura, sin efectos secundarios).  
Los tokens deben obtenerse externamente (no se generan en prod).

```bash
export AMBIENTE_PIPE=prod
export BASE_URL=https://ms-negociador-orq.prod.internal

# Tokens pre-obtenidos (válidos, con los roles correctos)
export TEST_TOKEN_ADMIN=eyJhbGciOiJIUzM4...
export TEST_TOKEN_EMPRESA=eyJhbGciOiJIUzM4...
export TEST_TOKEN_FONDEADOR=eyJhbGciOiJIUzM4...
export TEST_TOKEN_OPERARIO=eyJhbGciOiJIUzM4...

./gradlew prodTest
```

> **IMPORTANTE:** Nunca definir `JWT_SECRET` en el pipeline de producción.  
> `prodTest` falla si `BASE_URL` no está definida (guarda de seguridad en Gradle).

---

## Sistema de tags

| Tag | Descripción | ¿Necesita datos en BD? | ¿Apto para prod? |
|---|---|---|---|
| `@smoke` | Auth + contratos HTTP básicos | No | Sí |
| `@regression` | Flujos de negocio completos | Sí (básicos) | No |
| `@full` | Suite completa incluyendo OTP | Sí (completos) | No |
| `@production-safe` | Solo lectura, sin efectos secundarios | Mínimos | Sí |

---

## Variables de entorno — referencia

| Variable | Obligatoria en | Descripción |
|---|---|---|
| `AMBIENTE_PIPE` | Todas | `dev` / `qa` / `prod` (default: `dev`) |
| `BASE_URL` | qa, prod | URL del microservicio |
| `JWT_SECRET` | qa | Secret HS384 (Base64) del ambiente |
| `TEST_TOKEN_ADMIN` | prod | Token ADMINISTRADOR pre-obtenido |
| `TEST_TOKEN_EMPRESA` | prod | Token EMPRESA pre-obtenido |
| `TEST_TOKEN_FONDEADOR` | prod | Token FONDEADOR pre-obtenido |
| `TEST_TOKEN_OPERARIO` | prod | Token OPERARIO pre-obtenido |
| `TEST_RUT_EMPRESA` | regression, full | RUT de empresa de prueba |
| `TEST_RUT_OPERADOR` | regression, full | RUT de operador de prueba |
| `TEST_RUT_FONDEADOR` | regression, full | RUT de fondeador de prueba |
| `TEST_FACTURA_NUM` | regression, full | Número de factura existente en BD |
| `TEST_FECHA_PROCESO` | regression, full | Fecha proceso (yyyy-MM-dd) |
| `TEST_NEG_ID` | regression, full | ID de negociación existente en MongoDB |

---

## Reporte de resultados

Los reportes HTML de Karate se generan en:
```
build/reports/karate/
```

Los reportes de JaCoCo (cobertura unit tests) en:
```
build/jacocoHtml/
```

---

## Integración CI/CD (GitHub Actions / Jenkins / GitLab)

```yaml
# Ejemplo GitHub Actions
- name: Smoke Tests
  env:
    AMBIENTE_PIPE: qa
    BASE_URL: ${{ secrets.QA_BASE_URL }}
    JWT_SECRET: ${{ secrets.QA_JWT_SECRET }}
  run: ./gradlew smokeTest

- name: Regression Tests
  env:
    AMBIENTE_PIPE: qa
    BASE_URL: ${{ secrets.QA_BASE_URL }}
    JWT_SECRET: ${{ secrets.QA_JWT_SECRET }}
    TEST_FACTURA_NUM: ${{ vars.TEST_FACTURA_NUM }}
    TEST_NEG_ID: ${{ vars.TEST_NEG_ID }}
  run: ./gradlew regressionTest
```