# Janery — diseño inicial de base de datos

**Importante:** este documento define dominios y relaciones iniciales, no un DDL final aprobado. Claude debe presentar un diagrama ER y propuesta de migraciones antes de ejecutar el reinicio de tablas.

## 1. Base y esquema

- Nombre de base de datos PostgreSQL: `janery`.
- Esquema de aplicación preferido: `janery`.
- Base de datos y esquema son distintos.
- No ejecutar `DROP DATABASE`.
- El propietario ha autorizado eliminar y recrear las tablas de prueba de Janery. La operación debe limitarse a los objetos confirmados como pertenecientes a Janery.

## 2. Dominios de datos

### Identidad
- `usuarios`: credenciales, estado, datos de autenticación.
- `personas`: datos personales completos de quien opera con Janery.
- Relación usuario-persona: una identidad de acceso vinculada a una persona operativa cuando complete el alta.
- `roles`, `permisos`, `usuario_roles` o asignaciones equivalentes.
- Historial de cargos y permisos.

### Cuenta y movimientos
- `cuentas_principales`: titular, número, estado, fechas.
- `depositos_apertura`: separar los USD 15 de ingreso y USD 5 reembolsables.
- `operaciones_financieras`: operación general/origen/destino/importe/fecha/estado/referencia.
- `movimientos_cuenta`: detalle de los movimientos de cuenta principal.
- Mantener trazabilidad de saldos disponibles y depósito reembolsable.

### Créditos
- `creditos`: familia, modalidad, titular, importe, plazo, tasas/condiciones aplicadas, estado.
- `cuotas_credito`: número, vencimiento y componentes.
- `pagos_credito`: pagos confirmados y referencias.
- `detalles_pago`: distribución del pago por concepto.
- `desembolsos_credito`.
- Registros de intereses y fondo de desgravamen cuando corresponda.
- Crédito emergente de socio debe poder registrar múltiples disposiciones/retiros contra un cupo y pagos que reduzcan el capital utilizado.
- Emergentes estacionales deben poder vincularse al cupo general y al periodo promocional.

### Ahorros y encajes
- `cuentas_ahorro`: ahorro normal y plazo fijo con nombre, tipo, titular, saldo/estado y condiciones.
- `movimientos_ahorro`.
- `encajes_credito`: vínculo con crédito, importe constituido, pagado al inicio y devolución al finalizar.
- `ahorro_letras_credito`: saldo acumulado por crédito y movimientos de redondeo de cuotas.
- No mezclar ahorro letras con ahorro normal.

### Socios y fondos
- `socios` o relación de condición de socio con persona.
- `aportes_socios`: tipo obligatorio o personal/adicional, importe y fecha.
- `participaciones` / snapshots mensuales: valor inicial, variaciones positivas/negativas y valor final, de acuerdo con la fórmula aprobada.
- `fondo_reserva`.
- `caja_principal` y sus movimientos.
- `caja_chica` y sus movimientos separados.
- Aportes mensuales de caja chica diferenciados de aportes al capital.

### CMS y auditoría
- `parametros_sistema`: código, valor, tipo/unidad, vigencia, estado.
- `historial_parametros`: valores anterior/nuevo, actor, fecha, motivo.
- `auditoria_eventos`: actor, acción, entidad, identificador, fecha, cambios relevantes.
- `historial_estados` para procesos que requieran transiciones consultables.

## 3. Reglas de modelado

- Dinero: usar `NUMERIC/DECIMAL` con precisión definida; nunca `FLOAT/REAL` para importes monetarios.
- Tasas: guardar valor y unidad (diaria/mensual/anual) explícitamente; no almacenar un número sin contexto.
- Fechas: definir fechas de negocio y timestamps de auditoría con claridad.
- Usar claves primarias y foráneas.
- Restricciones `CHECK`, `UNIQUE` y `NOT NULL` para invariantes estructurales.
- Evitar duplicar datos calculables sin una razón de auditoría/rendimiento.
- Guardar una instantánea de tasas y parámetros aplicados al crear crédito/cuota/cálculo.
- No guardar solo el saldo final: mantener movimientos para reconstruirlo.
- No eliminar registros financieros confirmados como mecanismo normal de corrección.

## 4. Revisión antes de migrar

Claude debe presentar:
1. Diagrama entidad-relación.
2. Descripción de cada tabla y campos importantes.
3. Cardinalidades y restricciones.
4. Estrategia de saldo y libro de movimientos.
5. Estrategia de migraciones Flyway.
6. Inventario de objetos que se eliminarán del esquema de prueba.
7. Respaldo y procedimiento de restauración.

No ejecutar DDL destructivo hasta aprobación explícita del propietario después de ver ese plan.
