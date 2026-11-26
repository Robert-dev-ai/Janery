# Janery — decisiones pendientes

Claude no debe inventar respuestas a estos puntos. Puede preparar estructuras extensibles, pero no implementar fórmulas o permisos no definidos.

## Resueltas por el propietario (30/10/2026)

- Pago de apertura: efectivo o transferencia.
- Registro y perfil: datos básicos (correo y contraseña; nombres, apellidos, identificación, teléfono y dirección).
- Presidente: el propietario, con todos los permisos. Los demás cargos recibirán permisos por versiones.
- Zona horaria de negocio: Ecuador (`America/Guayaquil`).
- Tasas, encajes, plazos y ahorros: valores provisionales editables desde el CMS. Fórmulas en `reglas-negocio.md`, sección 10.
- Mora y pagos parciales: se dejan para la siguiente versión.

## Necesario antes de publicar con datos reales

- Recuperar la contraseña por correo: hoy la atiende la presidencia (ver reglas, sección 16).
- Verificación de correo. El límite de intentos ya existe: 3 contraseñas incorrectas bloquean el acceso.
- Contraseña fuerte de PostgreSQL y usuario propio de la aplicación.
- Actualizar Spring Boot: revisar si 3.5 sigue recibiendo soporte.
- Respaldos automáticos fuera de la computadora. El control de versiones (Git y GitHub, con CI) ya existe, y `respaldo.ps1` hace el respaldo manual.
- Separar los datos reales de la rama de desarrollo de Neon (hoy la rama `development` tiene los datos reales y `production` está vacía).
- Decidir si el simulador copia los datos personales tal cual o anonimizados.

## Criterios de implementación aplicados, a confirmar por el propietario

- Informes en CSV, con punto y coma y coma decimal para abrirlos en Excel. Sin PDF en esta versión.
- Las áreas de trabajo muestran las funciones que concede cada permiso; los cargos sin permisos aparecen vacíos hasta que se les asignen.

- Quien tenga `GESTIONAR_CUENTAS` confirma que recibió el pago; hasta entonces la cuenta queda `PENDIENTE_PAGO` y no se contabiliza nada.
- Depósitos y retiros de la cuenta principal los registra ese mismo permiso, y entran o salen de la caja principal.
- Número de cuenta: `JAN-000001`, secuencial.
- Cierre: exige saldo cero y que se confirme la devolución del depósito. Los USD 5 no pasan por la caja principal.
- Sin verificación de correo ni de identidad en esta versión.

## 1. Registro y cuenta
- Campos exactos de registro básico y perfil personal completo.
- Verificación de identidad, correo o teléfono.
- Método de cobro/confirmación de los USD 20.
- Formato del número de cuenta.
- Cierre simple en primera versión; detalles adicionales pendientes.

## 2. Créditos a letras
- Tasas exactas por modalidad y perfil.
- Fórmula de interés y amortización.
- Importe/porcentaje de encaje por modalidad.
- Plazos y periodicidad.
- Porcentaje/base del fondo de desgravamen.
- Detalles del redondeo de ahorro letras.
- Mora, pagos anticipados y refinanciación.

## 3. Emergentes
- Clientes: 3,5 % mensual de interés y 0,25 % mensual de fondo de desgravamen; definir base exacta y momento del cálculo.
- Socios: 0,12 % diario de interés y 0,08 % diario de fondo de desgravamen; definir convención de días, base exacta y liquidación.
- Definir vencimiento exacto del emergente de socios y forma de pago del capital.
- Definir pagos parciales, mora y suspensión.
- Promociones: confirmar fechas/horas exactas de apertura y cierre y tratamiento de solicitudes iniciadas al final del mes.

## 4. Ahorros
- Tasas de ahorro normal y plazo fijo.
- Periodicidad/capitalización de intereses.
- Plazos disponibles.
- Excepciones o penalización de retiro anticipado de plazo fijo.

## 5. Socios, participación y resultados
- Fórmula contable exacta para convertir aportes en participación sin valor nominal fijo.
- Tratamiento de retiros de capital.
- Cómo reconocer y asignar ganancias/pérdidas en tiempo real.
- Base exacta de ganancias distribuibles.
- El 10 % se destina al fondo de reserva antes del reparto; definir periodicidad, base y límites.
- Fórmula de acumulado y variación mensual.
- Reglas futuras de admisión/salida de socios.

## 6. Roles
- Funciones y permisos específicos de cada cargo.
- Quién aprueba créditos y cambios de parámetros.
- Separación de funciones, suplencias y conflictos de interés.

## 7. Informes y control
- Informes obligatorios y periodicidad.
- Acceso a datos personales/financieros.
- Retención, respaldos y formatos de exportación.

## 9. Fondo de reserva institucional (propuesta del 26/11/2026, a decidir en la reunión)

Nada de esto está implementado. Hoy el 10 % de cada ingreso va a la reserva al momento, la reserva no paga gastos ni se
reparte, y su dinero está en la caja principal y se presta (ver reglas, secciones 11 y 14).

Ya decidido por el propietario:

- Se sigue separando el 10 % en cada ganancia, no una vez al mes: así el socio no ve un descuento grande al cerrar.
- Los ahorros, depósitos, encajes, desgravamen y aportes no cuentan como ingreso.

Propuesto, pendiente de aprobar:

- **Ganancias de la reserva:** que reciba su parte de todas las ganancias según su saldo, como un socio más (ejemplo:
  reserva $1.000 y socios $9.000; de $100 de intereses recibe $10 + $9). Si no se aprueba, queda como hoy. Idea: un
  interruptor en el CMS, auditado y sin efecto retroactivo, para no tener que elegir ya.
- **Dónde está la reserva:** mostrar cuánto está prestado en créditos y cuánto en su propia cuenta. Pasa poco a poco a esa
  cuenta, que ya no se presta y está pensada para invertir en bolsa y tener liquidez en un día. Lo que gane es todo suyo.
- **Aviso al presidente:** al cerrar cada mes, con lo que falta pasar a la cuenta propia; él decide cuánto se mueve.
- **Cierre mensual:** el último día a las 23:59:59 (Ecuador). El mes queda bloqueado y lo que se pague después, aunque sea
  de un mes anterior, se registra en el mes en que se paga.
- **Informe mensual para los socios:** ingresos, gastos y resultado del mes; reserva (lo que recibió, lo que ganó, saldo y
  dónde está); evolución del fondo de cada socio.
- **Pérdidas:** al cierre anual, el presidente decide si usa la reserva para cubrirlas y cuánto, como máximo la pérdida.
  Cambia la regla actual de que la reserva no se devuelve.
- **Pantalla de la reserva:** saldo actualizado e historial de movimientos.
- **Normativa:** confirmar con la SEPS o un contador lo que aplica a cajas de ahorro. En cooperativas la reserva legal se
  forma de los excedentes, no de los ingresos brutos.

Orden propuesto: cuenta propia e historial; interruptor de ganancias; cierre mensual con bloqueo y aviso; informe mensual;
uso de la reserva en el cierre anual.

## 8. Nota de alcance
El cierre de cuenta, admisión/salida de socios y aplicación de ahorro letras/encaje a última cuota deben mantenerse simples en primera versión. No desarrollar flujos complejos anticipadamente.
