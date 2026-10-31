# Janery — requisitos del sistema

## 1. Objetivo

Janery es una aplicación para gestionar una caja de ahorro y crédito. Las personas podrán registrarse, informarse sobre Janery, abrir una cuenta principal y utilizar créditos, ahorros e inversiones. Una persona puede ser cliente y socio simultáneamente.

Los socios tendrán su panel personal y, adicionalmente, áreas de trabajo según sus cargos: administración, presidencia, vicepresidencia, secretaría, tesorería, caja chica, comité de crédito y asuntos sociales.

El sistema debe conservar el historial de operaciones y cambios para control, informes y análisis.

## 2. Flujo de acceso

1. La persona visita la página de Janery.
2. Encuentra **Registrarse** e **Iniciar sesión**.
3. El registro inicial solicita datos básicos para crear credenciales.
4. Después de iniciar sesión puede consultar información informativa sobre Janery, sin obligación de operar.
5. Si desea utilizar los servicios, completa sus datos personales como persona operativa.
6. Abre una cuenta principal mediante un pago inicial de USD 20:
   - USD 15 se reconocen como ingreso de Janery y se registran en la caja principal.
   - USD 5 quedan asociados a la persona como depósito reembolsable.
   - Los USD 5 no forman parte del saldo disponible.
   - Se devuelven cuando se cierra la cuenta.
7. Con la cuenta principal activa, accede al panel personal.

El usuario de acceso y la persona operativa son conceptos distintos. Registrarse no convierte automáticamente a alguien en cliente o socio.

## 3. Cuenta principal

Es la cuenta central de la persona, similar conceptualmente a una cuenta bancaria:
- Recibe desembolsos de créditos.
- Recibe fondos liberados de ahorro letras y encajes al terminar el crédito.
- Permite mantener dinero disponible y transferirlo a productos de ahorro.
- Registra depósitos, retiros y otros movimientos habilitados.

Mostrar por separado saldo disponible y depósito reembolsable de USD 5. Los USD 5 no son utilizables.

Debe existir la posibilidad de cerrar la cuenta. Para la primera versión, mantener el proceso simple; no implementar un sistema complejo de liquidación.

## 4. Panel personal

Consultar y gestionar, según elegibilidad:
- Cuenta principal y movimientos.
- Créditos.
- Ahorros normales.
- Ahorros a plazo fijo.
- Ahorros letras vinculados a créditos.
- Encajes.
- Inversiones/aportes habilitados para su perfil.
- Historial y documentos relevantes.

## 5. Productos

- Créditos: a letras (normal, inversión, estudio, personalizado) y emergentes.
- Ahorros: normal, plazo fijo y ahorro letras asociado a crédito.
- Encaje: vinculado a crédito a letras, pagado al inicio.
- Participación de socios: aportes obligatorios y personales registrados por separado.
- Caja principal y caja chica: fondos independientes.

## 6. Áreas de trabajo

Contemplar áreas para administración, presidencia, vicepresidencia, secretaría, tesorería, caja chica, comité de crédito y asuntos sociales. Las funciones detalladas se definirán progresivamente.

## 7. CMS de parámetros

Los porcentajes, tasas, importes, límites, plazos y demás parámetros deben modificarse desde la aplicación, sin cambiar el código. Debe existir control de permisos, validaciones, vigencias e historial.

## 8. Historial e informes

Registrar operaciones y cambios relevantes: cuenta, créditos, cuotas, pagos, ahorros, encajes, aportes, participación, ganancias/pérdidas, fondos, cargos, permisos y parámetros. Permitir reconstruir historiales y generar informes.

## 9. Calidad

Integridad financiera, autorización en backend, trazabilidad, cálculos reproducibles, pruebas, protección de datos personales, parámetros versionados e informes conciliables.
