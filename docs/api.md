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
- `POST /auth/contrasena` (exige la contraseña actual y cierra las demás sesiones)
- `POST /auth/sesiones/cerrar-otras`

### Información pública
- `GET /public/informacion`
- `GET /public/productos`

### Persona y cuenta principal
- `POST /personas/perfil`
- `GET /personas/me`
- `PUT /personas/me` (solo teléfono y dirección)
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
- `GET /tesoreria/estado` (`CONSULTAR_CAJA`): saldo de cada caja; entradas y salidas de la caja principal y resultados, por mes, de los últimos 12 meses; totales por concepto y centavos en espera de reparto.
- `POST /creditos/{id}/documentos` (multipart `archivos`, varios PDF), `GET /creditos/{id}/documentos`; `GET /admin/creditos/{id}/documentos` y `GET /admin/creditos/{id}/documentos/{documentoId}` (`APROBAR_CREDITOS`).
- `POST /creditos/letras/solicitudes` exige `origenEncaje` (`EFECTIVO`, `CUENTA`, `AHORROS`, `CREDITO`) y, con `AHORROS`, `ahorroEncajeId`.
- `POST /creditos/emergentes/solicitudes` y `POST /creditos/emergentes/disposiciones` ya no desembolsan: devuelven `{ id }` y esperan aprobación.
- `GET /creditos/emergentes/disposiciones/me`; `GET /admin/creditos/disposiciones`, `POST /admin/creditos/disposiciones/{id}/aprobar` y `/rechazar` (`APROBAR_CREDITOS`).
- `GET /ahorros/productos`: tasa normal y plazos fijos. `POST /ahorros/{id}/interes`: retira el interés liberado; requiere `Idempotency-Key`.
- `GET /admin/resultados/en-espera` (`GESTIONAR_RESULTADOS`): ganancia y pérdida que aún no se pueden repartir entre los socios.

### Conteos para la campana y el panel
Solo números, en una consulta: lo que el cargo no puede atender llega en cero o vacío.
- `GET /pendientes/gestion` (`GESTIONAR_CUENTAS` o `APROBAR_CREDITOS`): aperturas, cierres, retiros, depósitos, créditos y cupos por atender.
- `GET /pendientes/me`: letras atrasadas, retiros y depósitos pendientes de la persona.
- `GET /admin/panel/conteos`: créditos y cuentas por estado (un estado sin registros no aparece), retiros y depósitos pendientes.

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

## Retiros de dinero, área de socios y contabilidad

Retiros (`Idempotency-Key` obligatoria al solicitar):

- `POST /cuentas-principales/me/retiros`, `GET /cuentas-principales/me/retiros`, `POST /cuentas-principales/me/retiros/{id}/cancelar`
- `GET /admin/solicitudes-retiro`, `POST /admin/solicitudes-retiro/{id}/entregar`, `POST /admin/solicitudes-retiro/{id}/rechazar` (`GESTIONAR_CUENTAS`)

Depósitos (`Idempotency-Key` obligatoria al solicitar):

- `POST /cuentas-principales/me/depositos`, `GET /cuentas-principales/me/depositos`, `POST /cuentas-principales/me/depositos/{id}/cancelar`
- `GET /admin/solicitudes-deposito`, `POST /admin/solicitudes-deposito/{id}/confirmar`, `POST /admin/solicitudes-deposito/{id}/rechazar` (`GESTIONAR_CUENTAS`)

Área general, solo socios activos (403 `SOLO_SOCIOS` para los demás):

- `GET /socios/general/resumen`, `/personas`, `/personas/{id}`, `/movimientos`

Gestión:

- `GET /admin/personas` (`ADMIN_USUARIOS`)
- `GET /admin/historial-crediticio`, `/admin/historial-crediticio/{personaId}` (`APROBAR_CREDITOS`)

Contabilidad (`periodo` opcional, `AAAA-MM`):

- `GET /admin/contabilidad/estado`, `/evolucion`, `/diario`, `/mayor?linea=` (`CONSULTAR_CONTABILIDAD`)
- `GET /admin/contabilidad/lineas-manuales`, `/asientos`, `POST /asientos`, `POST /asientos/{id}/reverso` (`REGISTRAR_ASIENTOS`)

## Perfil, acceso y pagos adelantados

- `POST /auth/registro`: crea usuario y persona con correo, contraseña, nombres, apellidos, identificación y teléfono. `POST /auth/register` se conserva para cuentas que completan sus datos después.
- `POST /auth/login`: el campo `correo` admite también la identificación.
- `PUT /personas/me/datos-credito`: datos para evaluar un crédito.
- `POST /creditos/{id}/precancelacion`: requiere `Idempotency-Key`. `POST /creditos/{id}/pagos` admite un importe mayor que la letra, hasta lo que falta del crédito.
- `GET /admin/creditos/{id}/solicitante` (`APROBAR_CREDITOS`): datos para evaluar la solicitud.
- `GET /cuentas-principales/me/movimientos` incluye `detalle`: el crédito y las letras, o el ahorro, al que pertenece cada movimiento.
