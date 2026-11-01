# Janery — guía de operación

Para quien arranca, respalda y actualiza Janery. Los comandos son de PowerShell en Windows y se ejecutan desde la raíz del proyecto, salvo que se indique otra carpeta.

## 1. Configuración

Copie `.env.example` a `.env` y complete los valores. El archivo `.env` nunca se sube al repositorio.

| Variable | Uso |
|---|---|
| `JANERY_DB_HOST`, `_PORT`, `_NAME`, `_SCHEMA` | Ubicación de la base. Base `neondb` en Neon, esquema `janery` ([neon.md](neon.md)) |
| `JANERY_DB_USER`, `JANERY_DB_PASSWORD` | Credenciales de PostgreSQL |
| `JANERY_JWT_SECRET` | Firma de los tokens, mínimo 32 caracteres. Cambiarlo cierra todas las sesiones al caducar el token actual |
| `JANERY_CORS_ORIGINS` | Orígenes permitidos para el navegador, separados por comas |
| `JANERY_COOKIE_SEGURA` | `true` salvo en pruebas locales muy concretas; exige HTTPS fuera de `localhost` |
| `JANERY_SERVER_PORT` | Puerto del backend |
| `JANERY_FLYWAY_ENABLED` | `true`: aplica las migraciones pendientes al arrancar |
| `JANERY_ADMIN_CORREO`, `JANERY_ADMIN_PASSWORD` | Presidente inicial; se crea al arrancar si ese correo no existe |

## 2. Arranque y parada en desarrollo

```powershell
cd backend;  .\mvnw.cmd spring-boot:run   # http://localhost:8080
cd frontend; npm start                    # http://localhost:4200
```

Para detener cada uno, use `Ctrl+C` en su terminal. Los logs salen por esa misma terminal.

## 3. Comprobar que funciona

- `GET /actuator/health` devuelve `200` con `UP`. Devuelve `503` si no hay conexión con PostgreSQL.
- `GET /api/v1/public/estado` confirma que la API responde.
- Informe **Conciliación de saldos**: la columna Diferencia debe ser cero en todas las cuentas. Revíselo después de cada actualización.

## 4. Respaldo y restauración

Haga un respaldo antes de cada actualización. La carpeta `respaldos/` no se sube al repositorio, y los archivos contienen datos personales y financieros: guárdelos en un lugar protegido.

```powershell
$env:PGPASSWORD = '<clave>'
& "C:\Program Files\PostgreSQL\18\bin\pg_dump.exe" -h localhost -U postgres -d janery -Fc -f respaldos\janery_AAAAMMDD.dump
& "C:\Program Files\PostgreSQL\18\bin\pg_restore.exe" --list respaldos\janery_AAAAMMDD.dump   # comprueba que se puede leer
```

Para restaurar, primero detenga el backend. Esta orden reemplaza los datos actuales:

```powershell
& "C:\Program Files\PostgreSQL\18\bin\pg_restore.exe" -h localhost -U postgres -d janery --clean --if-exists respaldos\janery_AAAAMMDD.dump
```

Nunca ejecute `DROP DATABASE`.

## 5. Migraciones

Flyway aplica al arrancar las versiones pendientes de `backend/src/main/resources/db/migration`. Una migración ya aplicada no se edita: los cambios van en un archivo con versión nueva. No hay reversión automática; si una actualización falla, restaure el respaldo.

## 6. Pruebas

```powershell
cd backend;  .\mvnw.cmd test
cd frontend; npx ng test --watch=false --browsers=ChromeHeadless
```

Las pruebas del backend necesitan PostgreSQL y `.env`. Solo usan el esquema `janery_test`, que recrean en cada ejecución.

## 7. Actualizar a una versión nueva

1. Haga un respaldo.
2. Ejecute las pruebas.
3. Detenga el backend, despliegue la versión nueva y arranque. Flyway aplicará las migraciones.
4. Compruebe el health check y el informe de conciliación.

## 8. Ante un fallo

| Síntoma | Qué revisar |
|---|---|
| `Port 8080 was already in use` | Ya hay otro backend abierto. Ciérrelo antes de arrancar |
| `JANERY_JWT_SECRET debe definirse` | Falta el secreto en `.env`, o es demasiado corto |
| Health `503` | PostgreSQL detenido, o credenciales incorrectas |
| `Schema-validation` al arrancar | El código espera una tabla o columna que falta: revise las migraciones |
| Error 500 en pantalla | El mensaje trae un código de correlación; búsquelo en el log |
| El interés del ahorro normal no se abonó | El servidor estaba apagado el día 1 de ese mes; no se abona solo (`reglas-negocio.md`, sección 10) |
