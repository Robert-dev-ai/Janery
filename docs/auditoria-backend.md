# Auditoría técnica del backend (noviembre 2026)

Revisión del backend en la rama `auditoria-backend`, sin cambiar reglas de negocio ni contratos de API, salvo la validación de `Idempotency-Key` (sección 3.3).

## 1. Qué se revisó

Documentación (`arquitectura.md`, `testing.md`, `despliegue.md`, `reglas-negocio.md`) y estos módulos: seguridad y autenticación, manejo de errores, cuentas, depósitos y retiros, créditos (solicitud, aprobación, desembolso, cobro), ahorros, pagos, participación de socios, caja, auditoría y tiempo real.

Ya estaba bien y no se tocó:

- Saldos con `CHECK (>= 0)` en la base, claves de idempotencia `UNIQUE`, una sola cuenta vigente por persona (índice parcial).
- Cuenta principal bloqueada primero en toda operación de una persona, y releída tras el bloqueo.
- `BigDecimal` en todo el dinero, importes validados con `@Positive @Digits(fraction = 2)`.
- Autorización en el backend: `@PreAuthorize` por permiso en la gestión; titularidad comprobada en lo personal (créditos, ahorros, comprobantes, documentos).
- Errores sin detalles internos; los 500 llevan un código de correlación en el log.
- Avisos en tiempo real enviados después del `COMMIT` y sin datos (cada pantalla vuelve a pedir lo suyo a la API).
- Listados sin consultas por fila (precarga de personas, consultas agrupadas).

## 2. Hallazgos

| # | Nivel | Hallazgo | Estado |
|---|---|---|---|
| 1 | ALTO | El bloqueo tras 3 contraseñas incorrectas se evitaba enviando intentos en paralelo | Corregido |
| 2 | ALTO | Un token de refresco usado en paralelo abría varias sesiones | Corregido |
| 3 | ALTO | Un pago a crédito con una clave ya usada en otro crédito respondía «pagado» sin pagar nada | Corregido |
| 4 | MEDIO | Igual en depósitos, retiros e interés de ahorros | Corregido |
| 5 | MEDIO | Se aceptaba `Idempotency-Key` vacía (`"   "`): la misma clave para cualquier operación | Corregido |
| 6 | MEDIO | Confirmar dos pagos de varias personas a la vez podía interbloquearse | Corregido |
| 7 | MEDIO | Un interbloqueo o espera de bloqueo respondía 500 «error interno» | Corregido: 409 |
| 8 | MEDIO | Un fallo inesperado al avisar a las pantallas, después del `COMMIT`, devolvía error por una operación ya guardada | Corregido |
| 9 | MEDIO | Reintentar la apertura de un plazo fijo respondía 409 aunque se había abierto | Corregido |
| 10 | MEDIO | `CreditoService` (1.160 líneas) mezclaba solicitud, aprobación, cupos, cobro y consultas | Cobro separado |
| 11 | BAJO | `AhorroService` no releía el ahorro tras bloquearlo, a diferencia de cuentas y créditos | Corregido |
| 12 | BAJO | `descontar` comprobaba la clave antes de los bloqueos | Corregido |
| 13 | BAJO | `cuotasVencidas(persona)` consultaba el crédito de cada cuota | Corregido |
| 14 | MEDIO | El retiro directo en ventanilla no comprueba que la caja principal tenga dinero | Requiere decisión (sección 6) |
| 15 | BAJO | Los permisos van dentro del token de acceso: quitar un cargo tarda hasta 15 minutos en aplicarse | Requiere decisión |
| 16 | BAJO | Las conexiones de tiempo real viven en memoria de una instancia | Documentado (sección 5) |

En lo revisado en detalle (cuentas, depósitos, retiros, créditos, ahorros, pagos, retiro de ganancias, compraventa y descuentos) no se encontraron movimientos que pudieran dejar saldos, cuotas o pagos inconsistentes: cada operación es una transacción, y la base rechaza saldos negativos y claves repetidas. `ContabilidadService`, `CreditoExistenteService`, el cierre anual y el simulador solo se revisaron por encima: siguen el mismo patrón, pero no se recorrieron línea por línea.

## 3. Cambios

### 3.1 Bloqueo de acceso (hallazgo 1)

- **Antes:** `iniciarSesion` leía al usuario, sumaba el fallo en memoria y lo guardaba. Ocho intentos simultáneos leían «0 fallos» y cada uno guardaba «1»: el acceso seguía `ACTIVO`.
- **Ahora:** `UPDATE usuarios SET intentos_fallidos = intentos_fallidos + 1 ... RETURNING`, en una sentencia. La base serializa los intentos.
- Prueba `intentosFallidosSimultaneosTambienBloquean`: falla con el código anterior (`ACTIVO`) y pasa con el nuevo.

### 3.2 Token de refresco (hallazgo 2)

- **Antes:** se marcaba revocado en memoria. Seis refrescos simultáneos con el mismo token obtenían seis sesiones.
- **Ahora:** `UPDATE ... WHERE id = ? AND revocado_en IS NULL`; si no actualiza ninguna fila, otra petición ya lo usó y responde 401.
- Prueba `refrescosSimultaneosConElMismoTokenAbrenUnaSolaSesion`: con el código anterior, 6 de 6 respondían 200.

### 3.3 Idempotencia (hallazgos 3, 4, 5, 9, 12)

- **Antes:** un reintento se reconocía por clave, tipo y usuario, pero no por el crédito o ahorro de destino. Reutilizar la clave de un pago del crédito A al pagar el crédito B respondía 200 con la operación de A; B no se pagaba y la pantalla lo daba por pagado.
- **Ahora:** `PagoCreditoService.mismoCredito` y `AhorroService.mismoAhorro` exigen que la operación anterior sea sobre el mismo destino; si no, 409 `CLAVE_REUTILIZADA`, como ya hacían depósitos y retiros de la cuenta.
- `Idempotency-Key` lleva `@NotBlank` y `@Size(max = 100)` en todos los endpoints (antes, vacía o de más de 100 caracteres terminaba en un error de la base). **Es el único cambio de contrato:** una clave vacía ahora responde 400. El frontend siempre envía un UUID.
- El reintento de un plazo fijo devuelve el mismo ahorro.
- Pruebas: `laClaveDeUnPagoNoSirveParaOtroCredito`, `laClaveDeUnDepositoAAhorroNoSirveParaOtroAhorro`, `sinClaveDeIdempotenciaNoSeOpera`, `reintentoDePlazoFijoDevuelveElMismoAhorroSinCobrarDosVeces`. Las cuatro fallan con el código anterior.

### 3.4 Concurrencia y errores (hallazgos 6, 7, 8, 11)

- `EjecucionPagos` bloquea las cuentas de todas las personas del pago ordenadas por id antes de depositar, como ya hacía `registrarCompraventa`.
- `ManejadorErrores` traduce `ConcurrencyFailureException` (interbloqueo, espera de bloqueo cancelada) a 409 `OPERACION_SIMULTANEA`. La transacción se deshizo entera: repetir con la misma clave es seguro.
- `EventosService.enviar` no deja escapar ninguna excepción: un aviso fallido descarta esa conexión y el navegador se reconecta solo.
- `AhorroService.propia` relee el ahorro tras bloquearlo (`em.refresh`), como `CuentaService.bloquear` y `CreditoService.bloquear`.

### 3.5 Organización de créditos (hallazgo 10)

- **Antes:** `CreditoService` tenía 1.160 líneas con solicitud, aprobación y desembolso, cupos de socio, cobro y consultas. El cobro lo usan además pagos y socios.
- **Ahora:**
  - `PagoCreditoService` (nuevo, ~350 líneas): pagar letras, abonar al cupo, precancelar, liberar encaje y ahorro letras. Es un caso de uso con sus propias reglas (orden de cobro, mora, liberaciones) y tres módulos lo llaman.
  - `CreditoService` (~860 líneas): solicitud, aprobación, desembolso, cupos y consultas.
  - Reglas que ambos usaban y estaban repetidas pasan a donde pertenecen: `Credito.esLinea()`, `Credito.devengar(hoy)`, `Credito.interesHasta(hoy)`, `CuotaCredito.deuda(hoy, tasaMora)` y `CalculadoraCredito.letrasParaPrecancelar`. El cálculo de interés de una línea estaba copiado tres veces.
- El código del cobro se movió sin cambiar su lógica. No se separaron más clases: el resto de `CreditoService` comparte repositorios y reglas, y dividirlo solo añadiría archivos.
- `AhorroService`, `CuentaService` y `PagoService` (400-460 líneas) tienen una responsabilidad cada uno; no se dividieron.

## 4. Archivos

- Nuevo: `credito/PagoCreditoService.java`.
- Modificados: `usuario/AuthService.java`, `usuario/Repositorios.java`, `usuario/Usuario.java`, `ahorro/AhorroService.java`, `credito/CreditoService.java`, `credito/Entidades.java`, `credito/CalculadoraCredito.java`, `credito/CreditoExistenteService.java`, `pago/EjecucionPagos.java`, `socio/ParticipacionService.java`, `common/ManejadorErrores.java`, `tiemporeal/EventosService.java`, y los controladores con `Idempotency-Key`.
- Pruebas: `ConfiguracionIntegrationTest`, `ProductosIntegrationTest`, `CalculadoraCreditoTest`.
- Sin migraciones nuevas: ningún cambio toca el esquema.

## 5. Tiempo real con varias instancias

Las conexiones de eventos se guardan en memoria. Con una sola instancia del backend funciona bien. Con dos o más, cada instancia solo avisa a sus propios navegadores: una pantalla conectada a otra instancia no se actualiza sola hasta que reconecte o recargue. Los datos nunca quedan mal, solo el aviso. Si algún día se ejecutan varias instancias, la salida sencilla es `LISTEN/NOTIFY` de PostgreSQL, sin infraestructura nueva. No se implementó porque hoy hay una sola instancia.

## 6. Decisiones que requieren al propietario

1. **Caja en el retiro de ventanilla.** El retiro que registra el personal en `/admin/cuentas/{id}/retiros` no comprueba que la caja principal tenga el dinero, a diferencia de la entrega de retiros solicitados y de los asientos manuales (`CAJA_INSUFICIENTE`). ¿Se aplica la misma regla? No se cambió porque es una regla financiera.
2. **Quitar permisos al instante.** Hoy un cargo retirado sigue valiendo hasta que vence el token de acceso (15 minutos). Se puede revocar al momento, a cambio de una consulta más por petición.

## 7. Pruebas

Ejecución completa (`.\mvnw.cmd test`, contra Neon, esquema `janery_test`) el 05/11/2026: **142 pruebas, 0 fallos, 0 errores, BUILD SUCCESS**.

| Clase | Pruebas |
|---|---|
| AreaSociosContabilidadIntegrationTest | 12 |
| ConfiguracionIntegrationTest | 16 (2 nuevas) |
| CreditosExistentesIntegrationTest | 3 |
| EsqueletoIntegrationTest | 19 |
| PagosIntegrationTest | 4 |
| ProductosIntegrationTest | 28 (4 nuevas) |
| ReportesIntegrationTest | 10 |
| SimulacionIntegrationTest | 7 |
| SociosIntegrationTest | 26 |
| TiempoRealIntegrationTest | 3 |
| SecurityConfigTest | 1 |
| CalculadoraCreditoTest | 9 (3 nuevas) |
| RepartoProporcionalTest | 4 |

Las 6 pruebas de integración nuevas se ejecutaron también sobre `main` sin las correcciones: **las 6 fallan allí**, lo que confirma que cada una detecta el error que corrige. Las 3 unitarias nuevas cubren las reglas movidas a la entidad y a la calculadora, con los ejemplos de `reglas-negocio.md`.

No quedó cubierto con una prueba automática el interbloqueo entre dos pagos de varias personas (hallazgo 6): reproducirlo depende del orden exacto en que llegan las peticiones. La corrección sigue el mismo patrón que la compraventa.

No se modificó ninguna prueba existente.
