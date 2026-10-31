# Janery — contratos API iniciales

**Estado:** guía de diseño; rutas y payloads definitivos se aprobarán junto con cada módulo.

## 1. Convenciones

- API REST bajo `/api/v1`.
- JSON para solicitudes/respuestas.
- DTO de entrada y salida; no exponer entidades JPA.
- Códigos HTTP semánticos.
- Errores con estructura uniforme: código, mensaje seguro, detalles de validación y correlación.
- Paginación para listados.
- Fechas ISO-8601; zona horaria de negocio documentada.
- Montos y tasas deben incluir unidad/moneda y no perder precisión.

## 2. Grupos de endpoints previstos

### Acceso
- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh` (si se adopta refresh token)
- `POST /auth/logout`
- `GET /auth/me`

### Información pública
- `GET /public/informacion`
- `GET /public/productos`

### Persona y cuenta principal
- `POST /personas/perfil`
- `GET /personas/me`
- `POST /cuentas-principales/apertura`
- `GET /cuentas-principales/me`
- `GET /cuentas-principales/me/movimientos`
- `POST /cuentas-principales/me/cierre` (flujo simple inicial)

### Créditos
- `GET /creditos/productos`
- `POST /creditos/letras/solicitudes`
- `GET /creditos/me`
- `GET /creditos/{id}`
- `POST /creditos/emergentes/solicitudes`
- `POST /creditos/emergentes/{id}/disposiciones` (para cupo de socio, sujeto a reglas)
- `POST /creditos/{id}/pagos`

### Ahorros
- `GET /ahorros/me`
- `POST /ahorros/normales`
- `POST /ahorros/plazo-fijo`
- `GET /ahorros/{id}`
- `POST /ahorros/{id}/depositos`
- `POST /ahorros/{id}/retiros` (solo cuando la regla lo permita)

### Socios y fondos
- `GET /socios/me`
- `GET /socios/me/aportes`
- `POST /socios/me/aportes`
- `GET /socios/me/participacion`
- `GET /caja-chica` (solo permisos autorizados)
- `GET /tesoreria` (solo permisos autorizados)

### CMS y áreas
- `GET /admin/parametros`
- `POST /admin/parametros`
- `PUT /admin/parametros/{id}`
- `POST /admin/parametros/{id}/publicar`
- `GET /admin/parametros/{id}/historial`
- Rutas por área/cargo, definidas después de acordar sus funciones.

## 3. Reglas de API

- La autorización debe validarse en backend.
- No aceptar desde frontend saldos finales, tasas aplicables ni valores calculados como autoritativos.
- El backend calcula y devuelve desglose explicativo.
- Usar claves de idempotencia para pagos/desembolsos/reintentos cuando corresponda.
- No filtrar datos de otras personas.
- Documentar cada endpoint con propósito, rol requerido, DTO, validaciones, errores y efectos financieros.
- Los endpoints indicados son propuestas iniciales; no implementar acciones financieras cuya regla siga pendiente.
