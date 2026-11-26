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
- `GET /cuentas-principales/me/interes`: interés de la cuenta principal a hoy: `{tasaAnual, acumulado, disponible, proximaLiberacion}`.
- `POST /cuentas-principales/me/interes` (requiere `Idempotency-Key`): pasa al saldo el interés ya liberado. Sin interés liberado responde 409 `INTERES_NO_DISPONIBLE`.
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
- Créditos anteriores a Janery (`REGISTRAR_CREDITOS_EXISTENTES`, reglas en `reglas-negocio.md`, sección 18): `GET /admin/creditos/existentes/personas?buscar=`, `POST /admin/creditos/existentes/personas/{usuarioId}/vista-previa` (sin guardar) y `POST /admin/creditos/existentes/personas/{usuarioId}` (requiere `Idempotency-Key`). `capitalDebe` es opcional: el capital que debe hoy, si es menos que el de sus letras pendientes.
- Crear la cuenta de una persona (`ADMIN_USUARIOS`): `POST /admin/usuarios` con `correo`, `nombres`, `apellidos`, `identificacion`, `telefono` y `direccion`. Abre su cuenta principal con la apertura cobrada en efectivo y responde `usuarioId`, `cuenta` y `contrasenaTemporal` (solo esta vez).

### Pagar
- Reglas en `reglas-negocio.md`, sección 20. Requiere cuenta principal activa.
- `GET /pagos/personas?buscar=` (mínimo 3 letras; devuelve solo `usuarioId` y `nombre`). Buscar y pagar lo de otras personas es solo para socios: a un cliente responde 403 `SOLO_SOCIOS`.
- `GET /pagos/opciones/me` y `GET /pagos/opciones/{usuarioId}`: créditos activos, ahorros normales y, si es socio, meses de aporte
- `POST /pagos` (multiparte, requiere `Idempotency-Key`): parte `datos` en JSON `{destinos: [{usuarioId, tipo, referenciaId?, periodos?, importe?}], medio}` y parte `archivos` con el comprobante, obligatorio y uno solo. `tipo`: `CUENTA`, `CUOTA_CREDITO`, `AHORRO`, `APORTE_OBLIGATORIO`, `APORTE_PERSONAL` o `CAJA_CHICA`. Queda como aviso de depósito; se confirma o rechaza en `/admin/solicitudes-deposito`.
- `POST /pagos/con-saldo` (requiere `Idempotency-Key`): `{destinos, origen: CUENTA|AHORRO, ahorroId?}`. La persona paga lo suyo con el saldo de su cuenta principal o de un ahorro normal; se aplica al momento, sin comprobante. Lo de otra persona responde 409 `SOLO_PROPIO`.
- `POST /ahorros/{ahorroId}/retiros-dinero` (requiere `Idempotency-Key`): como `POST /cuentas-principales/me/retiros`, pero el dinero sale directo del ahorro normal. Queda `PENDIENTE` hasta la entrega.
- Ventanilla de tesorería (permiso `GESTIONAR_CUENTAS`; todo se aplica al momento):
  - `GET /ventanilla/personas?buscar=` (mínimo 3 caracteres: nombre, cédula o número de cuenta): `usuarioId`, `nombre`, `identificacion`, `numeroCuenta`, `socio`.
  - `GET /ventanilla/personas/{usuarioId}`: `persona`, `saldoCuenta`, `ahorros` normales con su saldo y `opciones` (como `/pagos/opciones`).
  - `POST /ventanilla/cobros` (multiparte, requiere `Idempotency-Key`): parte `datos` `{pagadorId, destinos, origen, ahorroId?}` y parte `archivos` opcional, con un solo archivo. `origen`: `EFECTIVO`, `TRANSFERENCIA`, `CUENTA` o `AHORRO` (con `ahorroId`; solo lo del mismo `pagadorId`, si no `SOLO_PROPIO`). Responde `{id, codigo, importe, detalle}`; `id` y `codigo` (DP) son nulos si se pagó con saldo.
  - `POST /ventanilla/retiros` (requiere `Idempotency-Key`): `{usuarioId, origen: CUENTA|AHORRO, ahorroId?, importe, medio: EFECTIVO|TRANSFERENCIA, banco?, tipoCuenta?, numeroCuenta?, titularCuenta?}`. Responde el retiro ya `ENTREGADA`.


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
- `GET /tesoreria/estado.xlsx` (`CONSULTAR_CAJA`, no basta con ser socio): foto de la caja en el momento de pedirla, en Excel. Hojas: Resumen, Balance (mes en curso), Por persona (lo que se le debe, créditos por pagar, aportes y fondo personal), Créditos activos, Ahorros activos y Aportes por socio. Se lee en una sola transacción `REPEATABLE READ` para que las hojas cuadren entre sí; queda en auditoría como `INFORME_EXPORTADO` con código `ESTADO_CAJA`.
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
- `GET /socios/general/cartera`: cada persona con sus créditos activos (saldo inicial y actual, próxima letra desglosada, letras pagadas y atrasadas, ahorro letras, encaje, interés o mora por pagar y total de la deuda)
- `GET /socios/general/ahorros`: cada persona con su ahorro a la vista, plazo fijo, ahorro letras, encaje, saldo en cuenta y apertura; de los socios, sus aportes inicial, mensuales (y meses), personales, compraventas, ganancias, fondo personal y caja chica

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
