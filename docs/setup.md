# Janery — creación del proyecto desde cero

**Objetivo:** guía para crear el esqueleto en una carpeta nueva. Claude debe comprobar versiones disponibles y registrar las versiones exactas que use.

## 1. Requisitos previos

- JDK 21.
- Maven compatible con Java 21 (o Maven Wrapper generado en el proyecto).
- Node.js compatible con Angular 20 y npm.
- Angular CLI 20.x.
- PostgreSQL instalado y servicio iniciado.
- Git recomendado.
- Editor/IDE a elección.

Antes de instalar o cambiar herramientas, comprobar qué está instalado y reportar versiones.

## 2. Estructura inicial

Crear:

```text
janery/
├── backend/
├── frontend/
├── docs/
├── .env.example
├── .gitignore
└── README.md
```

No crear múltiples proyectos backend/frontend ni duplicar repositorios.

## 3. Backend

Generar una aplicación Spring Boot 3.5.x con:
- Spring Web.
- Spring Data JPA.
- Validation.
- Spring Security.
- OAuth2 Resource Server.
- PostgreSQL Driver.
- Flyway.
- Lombok opcional.

Usar Java 21 y Maven. Generar Maven Wrapper. Definir paquete raíz coherente, por ejemplo `ec.janery`, previa decisión documentada.

Configurar `application.yml` para leer variables de entorno, no credenciales literales. Configurar Flyway y evitar que Hibernate cree/actualice esquema automáticamente.

## 4. Frontend

Generar Angular 20.x con TypeScript y routing. Usar standalone components. Configurar environments sin secretos. Crear layouts públicos, panel autenticado y administración solo cuando se implementen las fases correspondientes.

## 5. PostgreSQL

- La base de datos se llama `janery`.
- El propietario ha indicado que las tablas actuales de prueba de Janery deben eliminarse y recrearse.
- Antes de cualquier eliminación, inspeccionar conexión, host, puerto, base actual y esquemas.
- No ejecutar `DROP DATABASE`.
- No tocar otros esquemas o bases.
- Mostrar listado exacto de objetos que se eliminarán y pedir confirmación explícita antes del primer `DROP`.
- Respaldar previamente objetos/datos si existen y es posible.
- Preferir un esquema dedicado `janery` dentro de la base `janery`, previa inspección y aprobación.
- Flyway crea esquema/tablas desde migraciones versionadas.
- La clave de PostgreSQL debe introducirse localmente en variables de entorno o `.env`, nunca escribirse en documentación/código/Git.

Variables sugeridas:
- `JANERY_DB_HOST`
- `JANERY_DB_PORT`
- `JANERY_DB_NAME`
- `JANERY_DB_SCHEMA`
- `JANERY_DB_USER`
- `JANERY_DB_PASSWORD`
- `JANERY_JWT_SECRET`

` .env.example` debe tener valores vacíos o ficticios. Añadir `.env` a `.gitignore`.

## 6. Migraciones

- Crear migraciones Flyway versionadas y ordenadas.
- No usar `ddl-auto=update`.
- Preferir validación de esquema tras migración.
- Cada migración debe ser revisada y probada.
- No ejecutar migraciones destructivas hasta aprobación del plan de reinicio.

## 7. Primera verificación

- Backend arranca sin errores y conecta a la base correcta.
- Endpoint health disponible.
- Frontend compila y muestra una página inicial.
- Prueba de conectividad a API.
- No incluir credenciales en logs.
- README documenta cómo ejecutar ambos proyectos.

## 8. Registro de decisiones

Anotar versiones exactas, comandos utilizados, estructura generada y decisiones no previstas en el README raíz.
