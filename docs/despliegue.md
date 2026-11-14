# Janery — configuración y despliegue

## 1. Entornos

Mantener configuración diferenciada para desarrollo, pruebas y producción. No reutilizar datos reales en pruebas.

## 2. Secretos y variables

- Credenciales PostgreSQL y secreto JWT solo mediante variables de entorno o gestor de secretos.
- `.env` local ignorado por Git.
- `.env.example` sin secretos reales.
- Nunca subir contraseñas, tokens, claves o dumps sensibles al repositorio.

## 3. Construcción

- Integración continua: `.github/workflows/ci.yml` compila y prueba backend y frontend en cada push a `main` y en cada pull request, con un PostgreSQL 18 desechable. No usa secretos ni Neon.
- Backend: Maven Wrapper para compilar, probar y empaquetar.
- Frontend: npm scripts para instalar, probar y compilar.
- Documentar comandos exactos que se utilicen.
- Fijar versiones de dependencias y revisar vulnerabilidades.

## 4. Base de datos

- PostgreSQL con base `janery`.
- Migraciones Flyway versionadas.
- Respaldos antes de migraciones importantes.
- No permitir que una instancia de desarrollo apunte accidentalmente a producción.
- Verificar host, puerto y nombre de base antes de operaciones destructivas.

## 5. Publicación

Decisión del propietario (12/11/2026): publicar gratis en **Render**, con la base en Neon. Pasos en la sección 8. La publicación manual en un servidor propio sigue siendo posible:

```powershell
cd backend;  .\mvnw.cmd clean package   # backend/target/janery-backend-*.jar
cd frontend; npm ci; npm run build      # frontend/dist/frontend/browser
```

- El proxy reverso sirve los archivos del frontend y reenvía `/api/` al backend. La versión de producción del frontend llama a `/api/v1`, en el mismo dominio.
- Toda ruta desconocida del frontend debe devolver `index.html`.
- HTTPS es obligatorio: la cookie de sesión es `Secure`.
- `/actuator/health` no debe publicarse a internet.

Antes de operar con datos reales: contraseña fuerte de PostgreSQL con un usuario propio en lugar de `postgres`, secreto JWT nuevo, respaldos automáticos fuera del servidor y las tareas de `decisiones-pendientes.md` marcadas como necesarias antes de publicar.

## 6. Operación

Arranque, parada, logs, health checks, respaldos, restauración, actualización y fallos: ver [operacion.md](operacion.md).

## 7. Simulador publicado

El administrador prueba en una instancia aparte, que no toca la base real:

- Backend: el mismo `.jar` con `SPRING_PROFILES_ACTIVE=simulacion`, las mismas variables `JANERY_DB_*` de la base real y `JANERY_SIMULADOR_ORIGENES` con el dominio del simulador. La base real se lee por una conexión de solo lectura: «Iniciar simulación» copia **todos** sus datos (personas, cuentas, créditos, auditoría y hashes de contraseña) a la base del simulador, y «Finalizar» deja solo al personal y los parámetros. El simulador publicado guarda, por tanto, una copia de los datos personales: protéjalo como a la instancia real. Usa la base `janery_simulacion`, en el mismo servidor PostgreSQL; créela una vez antes de arrancarlo. Escucha en `JANERY_SIMULADOR_PUERTO` (8081 por defecto).
- Frontend: `npm run build:simulador` → `frontend/dist/simulador/browser`. Llama a `/api/v1` en su propio dominio.
- Dominio propio, por ejemplo `simulador.` delante del dominio real: la cookie de sesión va en `/api/v1/auth` y no funciona bajo una subcarpeta.
- En la instancia real, `JANERY_SIMULADOR_URL` con la dirección del simulador: así aparece en Administración → Simulador.
- El perfil `simulacion` se niega a arrancar si la URL de la base no contiene «simulacion». Además, iniciar o finalizar una simulación pregunta a la propia base su nombre y se niega a vaciarla si no es de simulación; y Flyway rechaza cualquier limpieza fuera de una base de simulación o de esquemas `*_test` (`ProteccionDatosReales`).

## 8. Publicación en Render (plan gratuito)

Decisión del propietario (12/11/2026), para unas 20 personas y sin costo: Render para el programa, Neon para la base y cron-job.org para que no se duerma. La futura app móvil usará la misma API (`/api/v1`); su inicio de sesión necesitará otra forma de guardar el token de refresco, porque la cookie es solo para el navegador.

### Cómo está armado

- **Un solo servicio** sirve el frontend y la API en el mismo dominio (`https://<servicio>.onrender.com`). El `Dockerfile` compila Angular, lo copia en `static/` del backend y empaqueta el `.jar`. `FrontendConfig` devuelve `index.html` en las rutas de Angular; la API sigue protegida igual (`SecurityConfig.esFrontend`).
- **`render.yaml`** describe el servicio: Docker, plan gratuito, región Ohio (junto a Neon, `us-east-2`), publica cada push a `main` y comprueba la salud en `/api/v1/public/estado`, que no toca la base.
- **El actuator** queda en el puerto interno 9090 (`MANAGEMENT_SERVER_PORT`), que Render no publica.
- **Neon puede suspenderse**: Hikari no mantiene conexiones abiertas en reposo (`minimum-idle: 0`, `idle-timeout` de 60 s), así la base no gasta horas de cómputo cuando nadie la usa.
- **La CI construye la imagen** (`imagen` en `ci.yml`) y comprueba que lleva el frontend.

### Que no se duerma

El plan gratuito de Render apaga el servicio tras 15 minutos sin visitas, y la siguiente visita espera cerca de un minuto. Para evitarlo, una tarea gratuita en [cron-job.org](https://cron-job.org) visita `https://<servicio>.onrender.com/api/v1/public/estado` cada 10 minutos. Las 750 horas gratuitas al mes de Render alcanzan para un servicio encendido todo el mes. Esa dirección no consulta la base: Neon sigue suspendiéndose.

### Pasos para publicar (una vez)

1. Antes, lo marcado como necesario en `decisiones-pendientes.md`, en especial separar los datos reales de la rama `development` de Neon y usar una contraseña de base propia.
2. En [render.com](https://render.com), entrar con GitHub, **New → Blueprint** y elegir el repositorio `Janery`: Render lee `render.yaml`.
3. Escribir los valores marcados `sync: false`: `JANERY_DB_HOST` (host directo, sin `-pooler`), `JANERY_DB_USER`, `JANERY_DB_PASSWORD` y `JANERY_CORS_ORIGINS` con la dirección pública. `JANERY_JWT_SECRET` lo genera Render: es distinto del local, así que las sesiones locales no sirven allá.
4. Esperar el primer despliegue y abrir la dirección pública.
5. Crear la tarea en cron-job.org (cada 10 minutos) con la dirección de estado.

Los planes gratuitos cambian: confirmar las condiciones vigentes de Render, Neon y cron-job.org antes de publicar.
