# Janery — arquitectura y tecnologías

## 1. Tecnologías acordadas

### Backend
- Java 21.
- Spring Boot 3.5.x.
- Spring Data JPA.
- Bean Validation.
- Spring Security.
- OAuth2 Resource Server/JWT.
- Flyway.
- PostgreSQL.
- Lombok solo si aporta claridad y se utiliza consistentemente.

### Frontend
- Angular 20.x.
- TypeScript.
- Componentes standalone.
- Angular Router.
- Formularios reactivos para formularios complejos.
- Cliente HTTP mediante servicios Angular e interceptores cuando corresponda.

### Herramientas
- Maven para backend.
- npm para frontend.
- Git recomendado para control de versiones.
- IDE/editor a elección (por ejemplo IntelliJ IDEA y VS Code).
- PostgreSQL y cliente SQL como DBeaver opcional.

Las versiones exactas se deben verificar al crear el proyecto, fijar en los archivos de construcción y no actualizar a versiones mayores sin justificarlo.

## 2. Arquitectura general

### Backend: monolito modular
Organizar por funcionalidades de negocio. Dentro de cada módulo, separar responsabilidades según necesidad:
- `controller` / API.
- `dto`.
- `service`.
- `repository` (Spring Data JPA).
- `entity` / persistencia.
- `mapper`.
- excepciones específicas cuando correspondan.

No crear DAO paralelo a Repository. No crear interfaces/capas sin utilidad real. No usar microservicios inicialmente.

Módulos previstos (nombres orientativos, adaptables):
- usuario/autenticacion
- persona
- cuenta
- credito
- ahorro
- encaje
- socio
- aportes/participacion
- caja
- parametros
- auditoria
- reportes
- roles/permisos
- configuracion

### Frontend: por funcionalidades
- `core`: autenticación, guards, interceptores, configuración global y servicios transversales.
- `shared`: componentes, pipes y utilidades genuinamente reutilizables.
- `features`: funcionalidades de negocio, cada una con rutas, componentes y servicios propios.
- `layout`: layouts públicos, autenticados y administrativos, si aporta claridad.

No imponer una carpeta `pages/`; usarla solo si la estructura real lo justifica.

## 3. Principios
- DTO para contratos API; no exponer entidades JPA directamente.
- Reglas de negocio en servicios/dominio del backend.
- Controllers delgados.
- Frontend no decide tasas oficiales, elegibilidad, saldos ni permisos.
- Parámetros financieros persistidos y versionados.
- Movimientos financieros separados de auditoría de cambios.
- Transacciones para operaciones monetarias.
- Idempotencia o mecanismo equivalente para prevenir pagos/desembolsos duplicados.
- Pruebas unitarias para cálculos y pruebas de integración controladas.
- Autorización siempre en backend.

## 4. Estructura de carpetas propuesta

```text
janery/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/.../janery/
│       │   ├── config/
│       │   ├── common/
│       │   ├── usuario/
│       │   ├── persona/
│       │   ├── cuenta/
│       │   ├── credito/
│       │   ├── ahorro/
│       │   ├── socio/
│       │   ├── caja/
│       │   ├── parametros/
│       │   ├── auditoria/
│       │   └── reportes/
│       └── main/resources/
│           ├── application.yml
│           └── db/migration/
├── frontend/
│   ├── package.json
│   └── src/app/
│       ├── core/
│       ├── shared/
│       ├── layout/
│       └── features/
├── docs/
├── .env.example
├── .gitignore
└── README.md
```

La estructura es una guía inicial, no una obligación de crear carpetas vacías. Ajustar nombres y paquetes con criterio y consistencia.

## 5. PostgreSQL y Flyway

- Base de datos: `janery`.
- El esquema lógico de la aplicación preferido es `janery`, dentro de la base de datos `janery`, si la inspección confirma que puede crearse sin afectar objetos ajenos.
- No confundir base de datos y esquema.
- Flyway debe versionar toda creación y modificación de tablas.
- No usar `ddl-auto=update` en producción; preferir `validate` o configuración controlada.
- Configurar conexión mediante variables de entorno.
- No guardar claves reales en `application.yml`, repositorio ni documentación.

## 6. Construcción del proyecto

Usar Spring Initializr o Maven para generar el backend con dependencias acordadas; usar Angular CLI para el frontend. Registrar los comandos y versiones efectivamente utilizados en `docs/setup.md` o README final.

No introducir librerías adicionales grandes sin explicar su utilidad y obtener aprobación si alteran sustancialmente el stack.
