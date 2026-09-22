# Contribuir a ms-negociador-orq

> Este repo sigue la **[Definition of Done de Integrity](https://github.com/relativismofisico/ms-commons-util/blob/master/DEFINITION_OF_DONE.md)**.
> Leéla antes de abrir una rama. Este archivo solo agrega lo específico de
> `ms-negociador-orq` y un resumen operativo. La DoD es una sola para backend y frontend;
> su §6 mapea cada ítem a la herramienta de cada stack.

## Flujo

1. Rama `feature/<algo>` desde `master`.
2. Commits separados por *concern* (`feat:` / `fix(security):` / `fix(concurrencia):` / `fix(robustez):` / `refactor:` / `test:` / `test(karate):` / `docs:`). Diff revisado antes de cada commit.
3. Corré la checklist de la DoD. Adjuntá la evidencia mínima (§5 de la DoD) al PR.
4. Push (respaldo remoto). PR solo si se pide.

## Checklist rápida (ver la DoD para el detalle)

- [ ] **Unit** — Spock para toda lógica nueva/modificada (happy + ramas + bordes + fallos).
- [ ] **Cobertura** — `./gradlew jacocoTestCoverageVerification` en verde y sin bajar.
- [ ] **Aceptación** — si cambia el contrato de la API: escenario Karate nuevo/actualizado que **afirma el resultado real** (no `!= 500`).
- [ ] **Rendimiento** — si el cambio entra en §3.4 de la DoD: JMeter antes/después. Si no, **declarar por qué no aplica** en el PR.
- [ ] **Arquitectura** — `./gradlew architectureTest` en verde. **Gate duro.**
- [ ] **README** — actualizado y **completo** si el cambio toca algo que documenta.
- [ ] **Postman** — `external-test/smoke/postman/ms-negociador-orq.postman_collection.json` actualizado si el cambio toca la API.
- [ ] **Live** — `fix(security)` / `fix(concurrencia)`: verificación en vivo antes/después contra `_e2e-infra`, con el escenario del hallazgo.
- [ ] **Sin regresiones** — `./gradlew check` en verde.
- [ ] **Sin secretos** en el diff.

## Comandos

```bash
# suite completa (unit + arch + checkstyle/PMD/SpotBugs + cobertura) — sin Karate
./gradlew check

./gradlew test                              # solo unit
./gradlew architectureTest                  # solo arquitectura
./gradlew jacocoTestCoverageVerification    # solo cobertura

# aceptación (Karate) contra la infra local
cd ../_e2e-infra && ./run.sh ms-negociador-orq          # infra + app + seed + Karate
# o, con la app ya corriendo:
DOCKER_API_VERSION=1.51 ./gradlew automatedTest -Pkarate.env=dev

# rendimiento
cd external-test/jmeter && jmeter -n -t plans/<plan>.jmx -q config/<ambiente>.properties
```

## Specifics de ms-negociador-orq

- **Stack**: Spring Boot 3.5.4 · Gradle · Java 17 (toolchain).
- **Suite completa**: `./gradlew check` (excluye `automatedTest`).
- **Umbral de cobertura** (`coverage.gradle`): **BRANCH ≥ 0.85 · LINE ≥ 0.80**.
- **Arquitectura**: `./gradlew architectureTest` (`src/architecture-test/`). Capas: `domain` → nada; `application` → `domain` + `java`/`jakarta` + swagger + `com.factoring.commons`; `web` → + `org.springframework..`; `infrastructure` → todo, traduce excepciones de framework en el borde de los adapters.
- **Aceptación**: `./gradlew automatedTest -Pkarate.env=dev` — infra: `_e2e-infra/repos/ms-negociador-orq.env` (puerto 8083) — seeds `ms-negociador-orq.sql` + `ms-negociador-orq.mongo.js` (happy-path de endoso: `NEG-END-CUFE`).
- **Rendimiento**: planes en `external-test/jmeter/plans/` (negociador-orq-test-plan.jmx), config por ambiente en `external-test/jmeter/config/`.
- **Postman**: `external-test/smoke/postman/ms-negociador-orq.postman_collection.json` — con test scripts (`pm.test(...)`).
- **Notas**: Testcontainers (`IndirectFunderOfferSupplierQuotaConcurrencyIT`): con Docker Desktop ≥ 29 exportar `DOCKER_API_VERSION=1.51` (lo reenvía `test.gradle`); sin eso falla con `ContainerFetchException` — **es entorno, no el cambio**. Guards de ownership y de suplantación de empresa en `web/security/`: todo cambio en negociar/endoso/OTP verifica que la identidad actuante venga del JWT, no del body. La suite de endoso necesita la fila `ENDOSO_DOCUMENTO` en `messages_templates` (está en el seed SQL).
