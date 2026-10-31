# Janery — configuración y despliegue

## 1. Entornos

Mantener configuración diferenciada para desarrollo, pruebas y producción. No reutilizar datos reales en pruebas.

## 2. Secretos y variables

- Credenciales PostgreSQL y secreto JWT solo mediante variables de entorno o gestor de secretos.
- `.env` local ignorado por Git.
- `.env.example` sin secretos reales.
- Nunca subir contraseñas, tokens, claves o dumps sensibles al repositorio.

## 3. Construcción

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

Definir posteriormente servidor, dominio, HTTPS, proxy reverso, CORS, backups, monitoreo y estrategia de restauración. No inventar proveedor de hosting ni desplegar sin aprobación.

La aplicación ya está preparada para publicarse así, cuando se defina el servidor:

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
