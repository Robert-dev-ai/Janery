# Janery — seguridad y control de acceso

## 1. Identidad y autenticación

- El registro inicial crea credenciales de acceso.
- El registro completo de persona operativa se realiza después, cuando decide operar con Janery.
- Un usuario puede tener una persona asociada y condición de cliente/socio conforme al proceso aprobado.
- Usar Spring Security.
- OAuth2 Resource Server/JWT para proteger API, con estrategia de emisión/validación de tokens documentada.
- Contraseñas almacenadas solo mediante hash adaptativo seguro; nunca texto plano.
- No incluir secretos JWT, contraseñas ni claves de base de datos en código o Git.

## 2. Autorización

- Roles/cargos y permisos se administran de forma controlada.
- Backend verifica permisos en cada endpoint y acción.
- Angular puede ocultar opciones no autorizadas para UX, pero no es barrera de seguridad.
- Aplicar mínimo privilegio.
- Separar rol técnico de usuario, condición de socio y cargo organizativo.
- Las funciones exactas de cargos están pendientes; no asignar permisos financieros por suposición.

## 3. Protección de datos

- No exponer datos financieros de otras personas.
- Validar titularidad y permisos en cada consulta.
- No registrar contraseñas, tokens ni secretos en auditoría/logs.
- Proteger variables de entorno y archivos `.env`.
- No incluir credenciales reales en ZIP, documentación, repositorio ni `.env.example`.
- Configurar CORS con orígenes permitidos explícitos.
- Validar y limitar entradas; evitar errores que revelen estructura interna.

## 4. Sesiones y tokens

Documentar duración de access token, refresh token si se usa, revocación, logout y almacenamiento seguro en frontend antes de cerrar el diseño. No improvisar un almacenamiento inseguro de tokens.

## 5. Auditoría de seguridad

Registrar eventos relevantes: altas/bajas de usuario, cambios de permisos/cargos, cambios de parámetros, intentos de operaciones sensibles, aprobaciones y anulaciones. Evitar registrar secretos.
