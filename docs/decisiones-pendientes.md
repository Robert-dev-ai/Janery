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

- Pantalla para cambiar y recuperar la contraseña.
- Límite de intentos de inicio de sesión y verificación de correo.
- Contraseña fuerte de PostgreSQL y usuario propio de la aplicación.
- Actualizar Spring Boot: revisar si 3.5 sigue recibiendo soporte.
- Control de versiones (Git) y respaldos automáticos.

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

## 8. Nota de alcance
El cierre de cuenta, admisión/salida de socios y aplicación de ahorro letras/encaje a última cuota deben mantenerse simples en primera versión. No desarrollar flujos complejos anticipadamente.
