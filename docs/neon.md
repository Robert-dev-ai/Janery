# Trabajo con Neon desde varias computadoras

**Si eres una IA o una persona que empieza en una computadora nueva, lee este documento antes de programar.**

La base de datos de trabajo está en [Neon](https://console.neon.tech), en la nube, para usar los mismos datos desde cualquier computadora. Ya no se usa un PostgreSQL local. El código viaja por GitHub y los datos viven en Neon.

| Qué | Dónde vive | Cómo llega a otra computadora |
|---|---|---|
| Código y migraciones | GitHub | `git clone` y `git pull` |
| Datos | Neon | Ya están ahí |
| `.env` (contraseñas) | Cada computadora, ignorado por Git | Copiarlo a mano |

## Cómo está organizada la base

Proyecto de Neon: `janery`. El programa usa la base `neondb` y el esquema `janery`. La conexión debe ser directa, con un host sin `-pooler`.

| Rama de Neon | Uso |
|---|---|
| `development` | **Datos reales desde el 02/11/2026.** El `.env` apunta aquí |
| `production` | Sin tablas. No usarla para programar ni para pruebas |

**`development` tiene datos reales: nunca reiniciarla (`neon branches reset`), borrarla ni cargarle datos falsos.**

Bases de la rama `development` (decisión del propietario, 02/11/2026: solo estas dos):

| Base | Uso |
|---|---|
| `neondb` | El programa, en el esquema `janery`. Las pruebas usan los esquemas `janery_test` y `janery_simulacion_test`, que recrean en cada ejecución; nunca tocan `janery` |
| `janery_simulacion` | El simulador de fechas (`simulacion.ps1`) |

La base `postgres` es interna de Neon.

## Preparar una computadora nueva

1. Instalar Git, Java 21 y Node. Para el programa normal no hace falta PostgreSQL.
2. Clonar el proyecto:

   ```powershell
   git clone https://github.com/Robert-dev-ai/Janery.git
   cd Janery
   ```

3. Copiar a la raíz el `.env` de otra computadora, por un medio seguro. No rehacerlo desde `.env.example`: `JANERY_JWT_SECRET` y las contraseñas deben ser los mismos.
4. Instalar el frontend: `cd frontend; npm install`.
5. Arrancar, cada uno en su terminal, y abrir `http://localhost:4200`:

   ```powershell
   cd backend;  .\mvnw.cmd spring-boot:run
   cd frontend; npm start
   ```

Opcional, para administrar Neon desde la terminal, con la cuenta del propietario (su correo es `JANERY_ADMIN_CORREO`):

```powershell
npm install -g neon
neon login
neon link
neon checkout development --no-env-pull   # sin esa opción puede sobrescribir el .env
```

Opcional, para usar Claude Code con Neon: `neon plugins --global --agent claude-code` y, dentro de una sesión interactiva, `/mcp` para autorizar el servidor MCP.

## Trabajo diario

1. Al empezar: `git pull` en `main` y crear una rama, `git checkout -b feature/nombre`.
2. Si la estructura de la base cambia, añadir una migración nueva en `backend/src/main/resources/db/migration/` (`V11__descripcion.sql`, y así en adelante).
3. Probar con las pruebas automáticas o con el simulador (`janery_simulacion`), no con operaciones inventadas en la aplicación normal: sus datos son reales.
4. Al terminar: `git add`, `git commit`, `git push`, y fusionar en `main`.
5. En otra computadora: `git pull` antes de arrancar el backend.

Una migración nueva se aplica sobre los datos reales al arrancar el backend: revisarla antes y hacer un respaldo si cambia o borra datos. Las ramas de Neon no se fusionan.

## Reglas

- No crear ni cambiar tablas a mano desde la consola de Neon o DBeaver: esos cambios no llegan a otras bases y pueden impedir que el backend arranque. Mirar los datos sí está permitido; cargar datos falsos en `neondb`, no.
- No editar una migración ya aplicada: los cambios van en un archivo con versión nueva.
- No trabajar con dos computadoras a la vez con migraciones distintas: todas comparten la misma base.
- No apuntar el `.env` a `production` ni tocar esa rama sin autorización del propietario.
- No usar un host con `-pooler`: no conserva `SET search_path`, del que dependen las consultas.
- No subir, copiar a mensajes ni mostrar en pantalla el contenido del `.env`. Este repositorio es público.
- Apagar el backend y desconectar DBeaver al terminar: las conexiones abiertas consumen el cómputo del plan gratuito.
- Correr solo la clase de pruebas que se esté tocando (`.\mvnw.cmd test -Dtest=SociosIntegrationTest`). La ejecución completa contra Neon tarda unos 20 minutos.

## Simulador de fechas

Sirve para probar el paso de los días sin esperar. Está en `main`, y solo se activa si se arranca con este script; el programa normal no lo usa.

1. Apagar el backend normal, porque usan el mismo puerto.
2. Ejecutar `.\simulacion.ps1` desde la raíz. Crea la base `janery_simulacion` en la rama a la que apunte el `.env`, si no existe.
3. Iniciar sesión con el presidente y usar la barra "Modo simulación" de la parte superior.
4. Para repetir una prueba: `.\simulacion.ps1 -Reiniciar`, que borra los datos simulados y vuelve a la fecha real.

El script necesita `psql`. Si no está, instalar PostgreSQL y marcar solo **Command Line Tools**. Más detalles en [testing.md](testing.md).
