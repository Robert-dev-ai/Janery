# Janery — historial, auditoría y trazabilidad

## 1. Objetivo

Poder reconstruir qué ocurrió, cuándo, quién lo realizó, qué cambió y qué efecto tuvo en saldos y productos. Es un requisito transversal.

## 2. Registrar

### Usuarios/personas
Registro, activación, cambios relevantes, bloqueo/desbloqueo, eventos de seguridad, cargos y permisos.

### Cuenta principal
Apertura, cobro de USD 20, separación de USD 15 ingreso y USD 5 reembolsables, depósitos, retiros, transferencias, cierre y devolución.

### Créditos
Solicitud, evaluación, aprobación/rechazo, parámetros aplicados, desembolso, cuotas, vencimientos, pagos, aplicación de pagos, intereses, fondo desgravamen, uso de cupo, promociones, liquidaciones y anulaciones.

### Ahorros/encajes
Aperturas, aportes, retiros, intereses, vencimientos, constitución/devolución de encajes, acumulación/liberación de ahorro letras y aplicación a última letra.

### Socios/fondos
Aportes obligatorios y personales por separado, aportes de caja chica, evolución mensual de participación, ganancias/pérdidas, reserva, distribución, movimientos de caja principal y caja chica.

### Administración
Cambios de parámetros, cargos, permisos, aprobaciones, rechazos, anulaciones e informes relevantes.

## 3. Datos mínimos de auditoría

ID, fecha/hora de servidor, actor o proceso, acción, módulo, entidad/ID afectado, valores anteriores/nuevos cuando aplique, motivo, estado anterior/nuevo y referencia de operación. No guardar contraseñas, tokens ni secretos.

## 4. Libro financiero y auditoría

Distinguir:
1. Libro de movimientos financieros: hechos económicos confirmados con origen, destino, importe, fecha y referencias.
2. Auditoría: quién creó, modificó, aprobó, anuló o cambió datos, permisos y parámetros.

No basta con guardar el saldo actual. Debe poder explicarse a partir de movimientos.

## 5. Integridad

- Operaciones monetarias completas o no aplicadas.
- Evitar duplicación por reintentos.
- No borrar operaciones confirmadas como corrección ordinaria.
- Corregir mediante reversos/anulaciones auditables.
- Guardar parámetros utilizados en cada cálculo.
- Restringir modificaciones ordinarias del historial.
- Definir respaldo y retención.

## 6. Informes previstos

Estado de cuenta, historial de créditos/cuotas, intereses y desgravamen, ahorros/encajes/letras, caja principal y chica separadas, aportes por socio, evolución mensual de participación, reserva/distribución y actividad por usuario/cargo/periodo.
