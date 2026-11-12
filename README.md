# JANERY — caja de ahorro y crédito

**Base de datos:** PostgreSQL 18 en Neon, base `neondb`, esquema `janery` ([docs/neon.md](docs/neon.md))  
**Idioma:** español  
**Estado:** aplicación construida (fases 0 a 7) y en uso con datos reales. Las reglas de negocio no deben alterarse sin autorización del propietario.

## Qué contiene

| Ruta | Contenido |
|---|---|
| `backend/` | API en Spring Boot (Java 21), monolito modular |
| `frontend/` | Aplicación Angular 20 |
| `docs/` | Especificación, reglas de negocio, operación y auditorías |
| `.github/workflows/ci.yml` | Compila y prueba backend y frontend en GitHub, con un PostgreSQL desechable |
| `simulacion.ps1` | Arranca el simulador de fechas ([docs/testing.md](docs/testing.md), sección 6) |
| `respaldo.ps1` | Respaldo lógico de la base del `.env` ([docs/operacion.md](docs/operacion.md), sección 4) |
| `.env.example` | Variables de configuración, sin valores reales. El `.env` real no se sube |
| `neon.ts`, `package.json` | Configuración de la CLI de Neon (`neon checkout`, ramas). La aplicación no los usa |
| `respaldos/` | Respaldos locales, ignorados por Git. Contienen datos personales |

## Lectura obligatoria

Leer todos los documentos de `docs/` antes de programar:

1. `requisitos.md`
2. `reglas-negocio.md`
3. `arquitectura.md`
4. `base-datos.md`
5. `api.md`
6. `frontend.md`
7. `seguridad.md`
8. `configuracion-parametros.md`
9. `roles-y-areas.md`
10. `historial-y-auditoria.md`
11. `setup.md`
12. `testing.md`
13. `despliegue.md`
14. `decisiones-pendientes.md`
15. `neon.md`
16. `diseno.md`
17. `operacion.md`: arranque, respaldos, migraciones y fallos
18. `fase-1-diseno.md`: diseño aprobado y plan de reinicio (histórico)
19. `auditoria-backend.md`: hallazgos y correcciones de la auditoría del backend

## Instrucciones críticas

- Janery se creó desde cero en esta carpeta. No reutilizar el antiguo proyecto de prueba.
- Mantener las reglas de negocio documentadas. No reinterpretar, simplificar, cambiar ni completar tasas, porcentajes, límites, fórmulas o condiciones sin preguntar al propietario.
- Si una regla está pendiente, documentarla y no implementarla por suposición.
- Esta autorización se refiere a los objetos de Janery dentro de la base `janery`; NO autoriza borrar la base completa, otros esquemas, otras bases ni datos ajenos.
- Antes de eliminar objetos: comprobar conexión, host/puerto, base actual (`janery`), esquema objetivo, listar objetos afectados y realizar respaldo lógico si existen datos que se puedan respaldar. Mostrar el plan de eliminación y solicitar confirmación explícita justo antes del primer `DROP`.
- Nunca ejecutar `DROP DATABASE`. No borrar objetos de `public` ni de otros esquemas sin demostrar que pertenecen exclusivamente a Janery y obtener confirmación expresa.
- Preferir un esquema PostgreSQL dedicado `janery` dentro de la base `janery`, sujeto a inspección y aprobación del plan. Base de datos y esquema son conceptos distintos.
- Flyway debe crear el esquema y las tablas de forma reproducible desde migraciones versionadas.
- No guardar contraseñas en estos documentos, código, Git, capturas ni mensajes de commit. Obtener credenciales mediante variables de entorno o un archivo `.env` local ignorado por Git. La clave que el usuario compartió en el chat debe tratarse como secreta y no copiarse al ZIP.
- Crear `.env.example` con nombres de variables y valores ficticios/vacíos, nunca con credenciales reales.
- Tasas, importes, límites y parámetros se administran mediante un CMS dentro de la aplicación, persistidos en backend/base de datos, con vigencia e historial.
- Los cálculos oficiales se ejecutan en backend, no en Angular.
- Toda operación financiera y cambio administrativo relevante debe tener historial.
- La autorización se aplica en backend, no solo ocultando botones.
- Trabajar por fases, presentar avances y probar antes de declarar terminada una función.

## Tecnologías acordadas

- Backend: Java 21, Spring Boot 3.5.x, Spring Data JPA, Bean Validation, Spring Security, OAuth2 Resource Server/JWT, Flyway, PostgreSQL; Lombok solo si aporta claridad.
- Frontend: Angular 20.x y TypeScript, componentes standalone y routing; aplicación dinámica, con estilos en Tailwind CSS.
- Backend: monolito modular organizado por funcionalidades.
- Frontend: organizado por funcionalidades; `core` para infraestructura global y `shared` para elementos realmente reutilizables.
- No usar microservicios inicialmente.
- No duplicar DAO y Repository.
- DTO para contratos API; no exponer entidades JPA directamente.

Las versiones exactas deben verificarse al generar el proyecto y quedar fijadas en los archivos de construcción. No actualizar versiones mayores sin justificarlo.

## Fases de trabajo

### Fase 0 — análisis
Leer todos los documentos, comprobar herramientas instaladas y resumir el alcance y pendientes.

### Fase 1 — diseño
Proponer estructura, módulos, modelo de datos, migraciones y plan de reinicio de tablas. Esperar aprobación antes del primer `DROP`.

### Fase 2 — esqueleto
Crear backend, frontend, configuración PostgreSQL/Flyway, variables de entorno, health check y pruebas mínimas.

### Fase 3 — núcleo
Usuarios, personas, autenticación, permisos, cuenta principal, apertura de cuenta, libro de movimientos, auditoría y CMS de parámetros.

### Fase 4 — productos
Ahorros, plazo fijo, créditos a letras, encajes, ahorro letras, emergentes y promociones estacionales.

### Fase 5 — socios y fondos
Socios, aportes obligatorios/personales, participación, ganancias/pérdidas, fondo de reserva, caja principal y caja chica.

### Fase 6 — áreas e informes
Áreas por cargo, informes, exportaciones y pruebas integrales.

### Fase 7 — entrega
README de ejecución, despliegue, migraciones, pruebas y documentación actualizada.

## Criterio de finalización

Cada módulo debe incluir migraciones, backend, frontend cuando corresponda, validaciones, autorización, auditoría, pruebas y documentación. No marcar una fase como terminada si quedan errores críticos o reglas de negocio inventadas.

---

# Trabajo con Neon desde varias computadoras

**Si eres una IA o una persona que empieza en una computadora nueva, lee [docs/neon.md](docs/neon.md) antes de programar.** La base de trabajo está en Neon, el código en GitHub, y el `.env` se copia a mano en cada computadora.

---

# Registro de construcción

## Estado

Fases 0 a 7 construidas, con valores provisionales y fórmulas por confirmar ([reglas-negocio.md](docs/reglas-negocio.md), secciones 10 y 11). Desde el 02/11/2026 la base de Neon (rama `development`) contiene datos reales, aunque quedan tareas marcadas como necesarias antes de publicar en [decisiones-pendientes.md](docs/decisiones-pendientes.md). Operación diaria: [operacion.md](docs/operacion.md). Publicación: [despliegue.md](docs/despliegue.md).

| Módulo | Contenido |
|---|---|
| `usuario` | Registro, sesión, cargos y permisos |
| `persona` | Datos personales |
| `cuenta` | Cuenta principal, libro de movimientos y retiros de dinero |
| `parametros` | CMS con vigencias e historial |
| `credito` | Letras, emergentes, promociones, encajes y ahorro letras |
| `ahorro` | Ahorro normal y plazo fijo |
| `socio` | Aportes, participación, resultados, cierre anual y caja chica |
| `caja` | Caja principal, caja chica y fondo de reserva |
| `reportes` | Informes y exportación CSV |
| `auditoria` | Registro de acciones |
| `contabilidad` | Balance, libros, indicadores y registro manual |
| `consultas` | Área general de socios, personas e historial crediticio |
| `pago` | Pagos de varias obligaciones a la vez, confirmados por el personal |
| `app` | Información pública, anuncios, pendientes y uso de la aplicación |
| `tiemporeal` | Avisos en vivo a las pantallas (SSE) |
| `simulacion` | Reloj simulado y copia de datos; solo con el perfil `simulacion` |
| `config`, `common` | Seguridad, errores, correlación de peticiones y protección de datos reales |

## Ejecución local

La base de trabajo está en Neon: ver [neon.md](docs/neon.md).

```powershell
# 1. Configuración: copiar el .env de otra computadora (ignorado por Git)

# 2. Backend, en http://localhost:8080
cd backend
.\mvnw.cmd spring-boot:run

# 3. Frontend, en http://localhost:4200
cd frontend
npm install
npm start
```

Pruebas:

```powershell
cd backend;  .\mvnw.cmd test
cd frontend; npx ng test --watch=false --browsers=ChromeHeadless
```

Las pruebas de integración del backend usan la base del `.env` (esquemas `janery_test` y `janery_simulacion_test`). Hoy esa base guarda los datos reales: preferir la CI de GitHub, que usa un PostgreSQL desechable, o correr solo la clase que se esté tocando. Flyway rechaza limpiar cualquier esquema que no termine en `_test` fuera de una base de simulación (`ProteccionDatosReales`).

- Comprobación del backend: `GET /actuator/health`. Devuelve `503` si no conecta con PostgreSQL.
- Conectividad de la API: `GET /api/v1/public/estado`.

## Versiones fijadas

| Herramienta | Versión |
|---|---|
| JDK | Oracle 21.0.13 |
| Spring Boot | 3.5.16 |
| Maven (Wrapper 3.3.4) | 3.10.0 |
| Node.js / npm | 24.22.1 / 11.19.1 |
| Angular / Angular CLI | 20.3.34 / 20.3.42 |
| TypeScript | 5.9.3 |
| PostgreSQL (Neon) | 18 |

## Comandos de generación

- Frontend: `npx @angular/cli@20.3.42 new frontend --routing --style=css --ssr=false --skip-git --zoneless=false --ai-config=none --defaults`
- Backend: `pom.xml` escrito a mano y Maven Wrapper tomado de Spring Initializr, porque Initializr ya solo genera Spring Boot 4 o superior.

## Decisiones

- La raíz del proyecto es esta carpeta, que contiene `backend/`, `frontend/`, `docs/` y este README.
- Paquete raíz: `ec.janery`, el ejemplo de `docs/setup.md`.
- Se añadió `spring-boot-starter-actuator`, no incluido en la lista de `docs/setup.md`, para el health check exigido.
- Las pruebas del backend necesitan PostgreSQL: el del `.env` en local, o el de la CI en GitHub. Usan los esquemas `janery_test` y `janery_simulacion_test`, que recrean en cada ejecución; nunca tocan `janery`.
- Lombok se usa en las entidades, para evitar getters repetitivos.
- El presidente inicial se crea al arrancar a partir de `JANERY_ADMIN_CORREO` y `JANERY_ADMIN_PASSWORD`, si ese correo aún no existe. Después del primer arranque, vaciar `JANERY_ADMIN_PASSWORD` en el `.env`.
- Decisiones del propietario y criterios por confirmar: [docs/decisiones-pendientes.md](docs/decisiones-pendientes.md).

El diseño propuesto y el plan de reinicio están en [docs/fase-1-diseno.md](docs/fase-1-diseno.md).
