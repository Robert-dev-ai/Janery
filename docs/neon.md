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
| `development` | Trabajo diario con datos falsos. El `.env` apunta aquí |
| `production` | Versión final. Sin tablas hasta el primer despliegue. No usarla para programar ni para pruebas |

Las pruebas crean los esquemas `janery_test` y `janery_simulacion_test`, y la simulación usa la base `janery_simulacion`. En Neon pueden quedar bases `janery` que sobran: el programa no las usa.

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
3. Probar en `development` con datos falsos.
4. Al terminar: `git add`, `git commit`, `git push`, y fusionar en `main`.
5. En otra computadora: `git pull` antes de arrancar el backend.

La estructura llega a `production` por las migraciones al desplegar; los datos falsos no viajan porque no están en esos archivos. Las ramas de Neon no se fusionan. Para limpiar `development` y dejarla igual que `production`, lo que borra sus datos falsos:

```powershell
neon branches reset development --parent
```

## Reglas

- No crear ni cambiar tablas a mano desde la consola de Neon o DBeaver: esos cambios no llegan a otras bases y pueden impedir que el backend arranque. Mirar y cargar datos falsos sí está permitido.
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
