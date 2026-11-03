# Janery — reglas de negocio

**Regla de prioridad:** este documento conserva las reglas expresadas por el propietario. No modificarlas por conveniencia técnica. Los puntos abiertos están en `decisiones-pendientes.md`.

## 1. Personas y perfiles

1. Una persona puede ser cliente, socio o ambas cosas.
2. El registro de credenciales permite acceder y consultar información.
3. Para operar financieramente, completa sus datos y abre una cuenta principal.
4. Los socios actuales se consideran fijos en esta primera etapa. El proceso para admitir o retirar socios se desarrollará después.
5. Ser socio requiere participación en el capital y aprobación de los demás socios; el procedimiento detallado queda para más adelante.
6. Cada socio aporta USD 2,50 mensuales obligatorios al capital.
7. Cada socio puede realizar aportes personales/adicionales.
8. Los aportes obligatorios y personales aumentan la participación, pero se registran por separado porque los aportes obligatorios podrán ser relevantes para futuras reglas de elegibilidad.

## 2. Cuenta principal

1. La apertura cuesta USD 20.
2. USD 15 se registran como ingreso de Janery en la caja principal.
3. USD 5 quedan como depósito reembolsable de la persona.
4. Los USD 5 no se muestran como saldo disponible y no pueden gastarse ni retirarse mediante operaciones ordinarias.
5. Los USD 5 se devuelven al cerrar la cuenta.
6. El cierre debe existir, pero en la primera versión se mantiene simple.
7. La cuenta principal es destino de desembolsos y fondos liberados, y origen de fondos para crear ahorros.

## 3. Créditos

### 3.1 Crédito a letras

Modalidades: normal, inversión, estudio y personalizado.

1. Cada modalidad tiene sus propios porcentajes y requisitos de encaje.
2. Los porcentajes pueden ser distintos para socios y clientes.
3. El encaje se paga únicamente al inicio del crédito.
4. Al terminar el crédito, el encaje se devuelve a la cuenta principal.
5. Cada letra se compone de capital, ahorro letras, fondo de desgravamen e interés.
6. La persona puede elegir el plazo entre las opciones habilitadas.
7. El ahorro letras es exclusivo de créditos a letras.
8. El ahorro letras sirve para completar el importe de la letra hasta una cantidad entera en dólares, sin centavos. Ejemplo: si los componentes suman USD 29,50, se agrega USD 0,50 para que el total de la letra sea USD 30,00.
9. El ahorro letras se acumula en un registro/cuenta asociado a ese crédito.
10. Al terminar el crédito, el ahorro letras acumulado pasa a la cuenta principal.
11. El ahorro letras puede utilizarse para cubrir la última letra si la persona lo desea. Mantenerlo simple: aplicar el saldo como parte del pago y liberar el remanente a la cuenta principal cuando corresponda.

No inventar tasas, fórmulas, plazos, periodicidad ni reglas de mora.

### 3.2 Crédito emergente para clientes

1. Límite general indicado: USD 300, configurable.
2. El cliente puede obtenerlo una vez al mes.
3. Debe pagarlo al mes siguiente.
4. Tasas indicadas para clientes:
   - Interés: 3,5 % mensual.
   - Fondo de desgravamen: 0,25 % mensual.
5. No incluye ahorro letras.
6. Tasas y límites configurables.
7. Fórmula exacta y casos de pagos parciales/mora pendientes.

### 3.3 Crédito emergente para socios

1. Funciona como cupo similar a tarjeta de crédito.
2. Cupo general indicado: USD 300, configurable.
3. El socio puede realizar varios retiros durante el mes mientras tenga cupo.
4. El interés diario se calcula sobre el saldo de capital efectivamente utilizado y aún no pagado.
5. Ejemplo: utiliza USD 100; el interés diario se calcula sobre USD 100. Luego utiliza USD 50 adicionales; el saldo utilizado pasa a USD 150 y el interés diario se calcula sobre USD 150. Cuando paga, disminuye el saldo pendiente.
6. No puede exceder el cupo disponible.
7. Tasas indicadas para socios:
   - Interés: 0,12 % diario.
   - Fondo de desgravamen: 0,08 % diario.
8. No incluye ahorro letras.
9. Tasas y cupo configurables.

### 3.4 Emergentes estacionales para socios

1. Emergente estudiantil disponible durante agosto.
2. Emergente navideño disponible durante diciembre.
3. Cada promoción tiene un máximo de USD 50, configurable.
4. Tasa indicada: 0,05 % diario.
5. Solo se puede abrir/solicitar durante su mes correspondiente; al terminar el mes se cierra la promoción.
6. Los importes estacionales forman parte del cupo general de USD 300, no se suman por encima de él.
7. Ejemplo: si el socio ya ocupa USD 300, no puede usar la promoción; si ocupa USD 250, podría utilizar hasta USD 50, sujeto al límite configurado.
8. No incluye ahorro letras.

## 4. Ahorros

### 4.1 Normal
- Puede crear varios ahorros normales.
- Cada ahorro puede tener nombre personalizado.
- Puede retirar cuando quiera.
- Tasa y condiciones configurables.

### 4.2 Plazo fijo
- Puede crear varios depósitos a plazo fijo.
- Cada uno puede tener nombre y plazo determinado.
- Ofrece mayor interés que el ahorro normal.
- No se puede retirar durante el plazo acordado.
- Tasas y plazos configurables.

### 4.3 Ahorro letras
- Exclusivo de un crédito a letras.
- Acumula las diferencias necesarias para redondear cada letra a dólares enteros.
- Se guarda separado por crédito.
- Al finalizar el crédito, pasa a la cuenta principal.
- Puede aplicarse a la última letra si la persona lo decide.

## 5. Encajes

1. Cada encaje corresponde a un crédito a letras.
2. Se paga al inicio.
3. El importe/porcentaje depende de modalidad y perfil, mediante parámetros configurables.
4. Al terminar el crédito, se devuelve a la cuenta principal.
5. Puede utilizarse para cubrir la última letra si la persona lo desea, manteniendo el proceso simple.

## 6. Capital, participación, ganancias y pérdidas

1. El capital de Janery pertenece a los socios.
2. No existe un precio nominal fijo por acción; la participación representa la proporción correspondiente según aportes/capital.
3. Los aportes obligatorios y personales aumentan la participación y deben registrarse en categorías separadas.
4. Aporte obligatorio indicado: USD 2,50 mensual por socio.
5. También existen aportes personales/adicionales.
6. El propietario quiere que la participación económica refleje ganancias y pérdidas a medida que se producen.
7. La aplicación debe mostrar la evolución mensual de cada socio: valor al inicio del mes, variaciones positivas o negativas durante el mes (ej. `+$25` o `-$10`) y resultado/acumulado al cierre.
8. Al iniciar el siguiente mes debe mostrarse el nuevo periodo y conservarse los periodos anteriores.
9. Al final del año, las ganancias distribuibles se reparten proporcionalmente a la participación/capital de cada socio.
10. Antes de distribuir ganancias, el 10 % se destina al fondo de reserva.
11. Aportes de capital y ganancias/pérdidas son conceptos diferenciados. No asumir que cada ingreso bruto es automáticamente ganancia distribuible.
12. La fórmula contable exacta de valoración, reconocimiento y retiro queda pendiente.

## 7. Caja principal y caja chica

### Caja principal
Administra el dinero operativo principal de Janery. Recibe los USD 15 de apertura y movimientos de productos financieros.

### Caja chica
1. Es un fondo independiente de la caja principal.
2. Cada socio aporta USD 2,50 mensuales a caja chica.
3. Este aporte es adicional e independiente del aporte obligatorio de USD 2,50 al capital.
4. No se mezcla ni se utiliza como parte del dinero de la caja principal.
5. Tiene saldo, movimientos, aportes, gastos e informes propios.
6. El cargo de caja chica administra este fondo.

## 8. Roles

Cargos/áreas existentes:
- Administración.
- Presidencia.
- Vicepresidencia.
- Secretaría.
- Tesorería.
- Caja chica.
- Comité de crédito.
- Asuntos sociales.

Cada cargo tendrá área de trabajo. Funciones y permisos detallados se definirán posteriormente.

## 9. Parámetros e historial

Todos los importes, porcentajes, tasas, límites, plazos y condiciones numéricas deben modificarse desde el CMS sin tocar código. Todo cambio y toda operación relevante debe quedar registrado.

## 10. Fórmulas y valores provisionales

El propietario autorizó el 30/10/2026 usar valores provisionales de tasas, encajes, plazos y ahorros, ajustables desde el CMS. Sus valores están en las migraciones `V4__creditos.sql` y `V5__ahorros.sql`. Las fórmulas siguientes las propuso el desarrollo y están pendientes de confirmación:

- **Letras (definido por el propietario):** mensuales. El interés es fijo sobre el capital sacado, y la letra es la misma hasta el final del crédito. El ahorro letras completa hasta el dólar entero. Si el capital no se divide exacto, la última letra lleva los centavos que faltan y esos mismos centavos menos de ahorro letras. Ejemplo: USD 100 a 3 meses lleva capital de 33,33, 33,33 y 33,34.
- **Desgravamen de letras:** fijo sobre el capital sacado, igual que el interés. Pendiente de confirmación.
- **Pagos parciales de letras (definido por el propietario):** se admiten y se cobran en este orden: capital, fondo de desgravamen, interés y ahorro letras. La mora, a criterio del desarrollo, se cobra al final. También aplican al emergente de cliente. Encaje y ahorro letras solo se usan para cubrir lo que falta de la última letra completa.
- **Encaje:** porcentaje del importe, cobrado al aprobar según lo elegido al solicitar (sección 15).
- **Tasas de un crédito a letras:** quedan fijadas al solicitarlo.
- **Emergente de cliente:** interés y desgravamen de un mes sobre el importe, en una sola cuota que vence un mes después de aprobarse. Se aprueba antes de desembolsar (sección 15).
- **Cupo de socio:** interés y desgravamen diarios simples sobre el capital utilizado. Cada pago cubre primero cargos y luego capital. No tiene fecha de vencimiento. Cada disposición actualiza la tasa a la vigente.
- **Promociones:** usan solo la tasa de 0,05 % diario, sin desgravamen. El máximo cuenta todo lo retirado en la promoción, aunque se haya pagado.
- **Perfil:** quien es socio usa las condiciones de socio; el resto, las de cliente.
- **Ahorro normal y plazo fijo:** ver sección 15.
- **Caja principal:** refleja el dinero real. Solo cambia con depósitos, retiros, el pago de apertura completo y el encaje en efectivo; créditos y ahorros se mueven dentro de la cuenta de cada persona.
- **Aprobación de créditos a letras:** Presidencia, con el permiso `APROBAR_CREDITOS`.

## 11. Socios y fondos

**Definido por el propietario (31/10/2026):**

- **Fondo personal:** el dinero de cada socio es un solo valor, pero se lleva separado por origen: aportes iniciales, aportes mensuales, aportes personales y ganancias.
- **Ganancias:** van al fondo personal de cada socio y se quedan ahí; no se pagan a la cuenta principal.
- **Fondo de reserva:** el 10 % se separa cada vez que se reparte una ganancia, no al cerrar el año. Es la línea 3.5 Reservas del balance.
- **Cierre:** es anual.
- **Pérdidas:** se restan de las ganancias. La reserva no se devuelve.
- **Aportes:** al aportar, el dinero pasa a ser parte de Janery y no se retira. Solo puede venderse a otro socio, en una compraventa interna.
- **Retiro de ganancias:** se pueden retirar desde el 1 de enero siguiente, si el socio está al día con sus obligaciones.
- **Decisión del 1 de enero:** el presidente decide si cada socio puede retirar o si se le descuenta, y a qué se destina, eligiendo en un menú que muestra lo que debe. Nada se descuenta automáticamente.

**Criterios del desarrollo, pendientes de confirmación:**

- **Obligaciones:** aporte mensual y caja chica de meses ya terminados desde que entró el socio, y cuotas de crédito vencidas. El cupo emergente no cuenta porque no tiene vencimiento.
- **Menú del presidente:** aportes y caja chica se descuentan por mes completo; las letras, completas o en parte, empezando por la más antigua de cada crédito. Si lo elegido supera las ganancias liberadas no se aplica nada. La autorización vale para el año en curso, exige que el socio esté al día y puede quitarse.
- **Destino del retiro:** la cuenta principal del socio.
- **Una pérdida de este año** reduce lo disponible aunque la ganancia sea de años anteriores.
- **Compraventa:** la registra Presidencia cuando ambos socios la acordaron. Se vende solo capital, no ganancias, al precio que acuerden. El comprador paga al vendedor entre sus cuentas principales; la caja no cambia.

- **Participación:** fondo personal del socio dividido entre la suma de los fondos de todos los socios activos.
- **Registro de ganancias y pérdidas a mano:** por quien tenga `GESTIONAR_RESULTADOS`, solo para lo extraordinario; lo demás se reparte solo (sección 14). Una pérdida no puede superar el capital total.
- **Evolución mensual:** se calcula de las variaciones; el valor inicial de un mes es el final del anterior.
- **Cierre anual:** solo años terminados y una vez por año. Totaliza y bloquea nuevos resultados en ese año; no mueve dinero.
- **Aportes iniciales:** los registra Presidencia como dinero recibido fuera del sistema; entran a la caja principal y al fondo personal.
- **Caja chica:** el aporte de USD 2,50 sale de la cuenta del socio y pasa de la caja principal a caja chica. No aumenta la participación.
- **Fondo de reserva:** caja propia; recibe el traspaso desde la caja principal.
- **Aportes mensuales y personales:** los paga el socio desde su cuenta principal, y puede ponerse al día con meses pasados.
- **Cierre de cuenta:** un socio activo no puede cerrar su cuenta principal.

**Fuera de esta versión, por decisión del propietario:** mora y refinanciación. Las letras pueden pagarse antes de su vencimiento; ver sección 13.

## 12. Retiros, área de socios y contabilidad

**Definido por el propietario (01/11/2026):**

- **El dinero está en la caja:** el saldo de una persona es lo que Janery le debe, no dinero guardado en la aplicación. Puede sacarlo en efectivo o a su cuenta de banco.
- **Retiro:** la persona lo solicita desde su cuenta principal, indicando efectivo o los datos de su banco. El importe se aparta de su saldo y la caja no cambia. Quien tenga `GESTIONAR_CUENTAS` confirma cuando ya entregó el dinero, y solo entonces sale de la caja principal; o lo rechaza con un motivo. La persona puede cancelar mientras esté pendiente. Rechazar o cancelar devuelve lo apartado.
- **Depósito:** la persona avisa cuánto deposita, en efectivo o por transferencia con su número. Quien tenga `GESTIONAR_CUENTAS` confirma cuando ya recibió el dinero, y solo entonces entra a la cuenta y a la caja principal; o lo rechaza con un motivo. Criterio del desarrollo, pendiente de confirmación.
- **Ahorros:** para sacar un ahorro, primero pasa a la cuenta principal. El plazo fijo sigue sin poder retirarse antes de vencer.
- **Área general de socios:** solo los socios activos ven las personas con cuenta, con nombres e importes de créditos, cuotas, ahorros y movimientos, y los totales de la caja. No incluye identificación, teléfono, dirección, correo ni datos bancarios. Tener un cargo o un permiso no da acceso si no se es socio.
- **Balance:** sigue el formato en Excel del propietario, con los mismos códigos.

| Línea | Qué contiene |
|---|---|
| 1.1 Caja | Caja principal y fondo de reserva, más los depósitos de apertura aún no devueltos |
| 1.4 Créditos por cobrar | Capital desembolsado menos capital cobrado. 1.42: crédito con alguna letra vencida; 1.41: el resto |
| 2.1 Ahorros captados | Ahorros normales, saldos de cuenta principal y retiros apartados sin entregar |
| 2.2 Plazo fijo | Plazos fijos |
| 2.3 Cuentas por pagar | Encajes, ahorro letras y depósitos de USD 5 |
| 2.5 Fondo de desgravamen | Desgravamen cobrado: se debe, no es ganancia |
| 3.1 y 3.2 | Aportes iniciales; aportes mensuales y personales |
| 3.5 Reservas | Fondo de reserva |
| 3.6 Resultado del mes | Ganancias menos gastos del mes |
| 3.7 Resultado acumulado | Resultado de meses anteriores, menos reservas separadas y ganancias retiradas o descontadas |
| 4.1 / 4.7 / 4.8 / 5.1 | Intereses cobrados / USD 15 de apertura / suministros de créditos / intereses de ahorros |

- **Caja chica:** queda fuera del balance; lleva sus cuentas aparte.
- **Registro manual:** las demás líneas se llenan con un registro auditado, con quien tenga `REGISTRAR_ASIENTOS`. Nada se edita ni se borra: se corrige con un reverso.

**Criterios del desarrollo, pendientes de confirmación:**

- **Depósitos de USD 5 en 1.1:** no pasan por la caja principal, pero son efectivo recibido que se debe. Sin contarlos, el balance no cuadraría.
- **Registro manual y caja:** un activo o gasto que aumenta se paga de la caja principal; un ingreso, pasivo o patrimonio que aumenta entra a ella. La provisión (5.9) no mueve caja y reduce la cartera en 1.99.
- **Registrar una ganancia o pérdida en Resultados** reparte entre socios, pero no es un ingreso ni un gasto del balance.
- **Indicadores:** morosidad (1.42 / 1.4), liquidez (1.1 / pasivos), solvencia (patrimonio / activos) y rentabilidad del mes (resultado / activos).
- **Permisos nuevos:** `CONSULTAR_CONTABILIDAD` y `REGISTRAR_ASIENTOS`, concedidos solo a Presidencia. Personas usa `ADMIN_USUARIOS` e Historial crediticio, `APROBAR_CREDITOS`.
- **Saldos iniciales:** el balance empieza con lo registrado en Janery; las cifras actuales del Excel no se importaron.

## 13. Pagos adelantados, precancelación y perfil

**Definido por el propietario (01/11/2026):**

- **Pago adelantado:** se puede pagar más que una letra; lo que excede pasa a las siguientes, en orden. Cada letra cobra en el orden de la sección 10. No se puede pagar más de lo que falta del crédito.
- **Precancelación de créditos a letras:** con la mitad de las letras redondeada hacia abajo, más una, ya pagadas por completo (12 letras exigen 7; 5 letras exigen 3), se termina el crédito pagando solo el capital pendiente. No se cobra interés, desgravamen ni ahorro letras de lo que falte. El encaje y el ahorro letras acumulado vuelven a la cuenta principal.
- **Registro:** pide correo, nombres, apellidos, identificación, teléfono y contraseña. Se puede entrar con el correo o con la identificación.
- **Datos para crédito:** dirección, lugar de trabajo, ocupación y antigüedad, ingresos mensuales, fecha de nacimiento, estado civil, contacto de referencia y cuenta bancaria. Se llenan en el perfil o al solicitar, y son obligatorios para solicitar un crédito a letras, también para socios; no bloquean los emergentes.
- **Rol visible:** el cargo de la persona (`ADMINISTRACION` se muestra como Administrador); sin cargo, Socio; si no es socio, Cliente. El panel personal se llama Panel del socio o Panel del cliente.

**Criterios del desarrollo, pendientes de confirmación:**

- Quien tenga `APROBAR_CREDITOS` ve estos datos al revisar un crédito. No aparecen en el área general de socios.
- Con un pago parcial, solo cuentan para precancelar las letras pagadas por completo.
- Las notificaciones del panel personal son letras vencidas, retiros en espera y depósitos en espera.

## 14. Reparto automático de ganancias

**Definido por el propietario (01/11/2026):**

- **Cada ingreso se reparte al momento:** intereses cobrados, aperturas de cuenta, suministros de créditos e ingresos del registro manual. El 10 % va a reservas (3.5) y el resto a los socios. El fondo de desgravamen no es ganancia.
- **Proporción:** según el fondo personal completo de cada socio.
- **Cierre mensual:** las ganancias se ven al momento, pero solo cuentan como base del reparto desde el mes siguiente. Siguen separadas de los aportes, porque al final del año se decide si se pueden retirar.
- **Retiro:** no se pueden retirar hasta el 1 de enero siguiente y con la autorización del presidente, si el socio está al día.
- **Rendimiento:** cada socio ve cuánto subió o bajó su fondo cada mes, en porcentaje.
- **Desde cuándo:** se aplica a lo cobrado desde este cambio; lo anterior no se reparte.
- **Resultados y Secretaría:** el registro a mano sigue por ahora para lo extraordinario. Más adelante, el área de Secretaría registrará gastos y pérdidas por tipo.
- **Centavos en espera:** un importe que no se puede dividir entre los socios no se reparte: queda a la espera hasta que sea repartible. Ejemplo: 0,01 entre dos socios espera; al llegar otro centavo, cada uno recibe 0,01.

**Cómo se aplica la regla de los centavos (criterio del desarrollo, pendiente de confirmación):**

- **Repartible:** cuando todos los socios de la base recibirían al menos 0,01 según su proporción. Si uno no llega, nadie recibe nada todavía.
- **Lo que sobra:** cada parte se redondea hacia abajo y los centavos sobrantes también esperan, en lugar de asignarse al socio mayor como se hacía antes. Ejemplo: 0,03 entre dos socios iguales da 0,01 a cada uno y 0,01 espera.
- **Ganancias y pérdidas esperan por separado:** no se compensan entre sí. Cada reparto suma lo que espera de su mismo tipo.
- **Reserva:** el 10 % se separa al registrar la ganancia; solo espera la parte de los socios.
- **Sin guardar otra copia:** lo que espera se calcula como lo registrado en `resultados`, sin la reserva, menos lo que llegó a `variaciones_participacion`. Se ve en Resultados y en Estado de caja.
- **Balance y cierre anual:** esperar no cambia la caja ni el balance, porque repartir es interno al patrimonio. El cierre anual totaliza lo registrado; lo que siga esperando pasa al año siguiente.
- **Socio con muy poca participación:** puede retrasar el reparto, porque también debe alcanzarle un centavo. Con fondos de 1 y 1000, el reparto espera hasta juntar unos USD 10.

**Criterios del desarrollo, pendientes de confirmación:**

- **Gastos:** cada gasto (líneas 5.x, incluidos los intereses de ahorros) baja los fondos de los socios al momento, sin tocar la reserva.
- **Comprar un bien:** registrado en 1.6 a 1.8, saca dinero de la caja pero no baja los fondos, porque el bien sigue siendo de Janery. Registrado como gasto, sí los baja.
- **Rendimiento del mes:** ganancias menos pérdidas del mes, sobre el fondo con que empezó más sus aportes del mes.
- **Sin capital de socios:** el importe queda en el balance sin repartir, sin bloquear la operación que lo originó.
- **Reversos del registro manual:** deshacen el reparto, incluida la reserva que se separó.

## 15. Aprobaciones, encaje, mora y ahorros

**Definido por el propietario (01/11/2026):**

- **Dinero en la caja:** los USD 5 de apertura, los ahorros normales y los encajes son del cliente, pero están en la caja y se pueden prestar. Estado de caja detalla a quién pertenece.
- **Aprobación:** ningún crédito sale sin aprobarse, tampoco los emergentes. El dinero llega a la cuenta principal al aprobar.
- **Solicitud de crédito a letras:** obliga a llenar los datos para crédito y a elegir de dónde sale el encaje: efectivo, cuenta principal, ahorros o descontado del crédito. Tiene un panel para subir varios documentos PDF, que se usará en el futuro.
- **Letras atrasadas:** se marcan como atrasadas con sus días, y el cliente recibe la notificación y un aviso.
- **Mora:** solo para clientes, USD 0,01 por día, modificable en el parámetro `MORA_DIARIA`.
- **Ahorro normal:** interés del 5 % anual calculado cada día. Se muestra aparte del saldo y se habilita para retirar el día 1 de cada mes.
- **Plazo fijo:** a 3, 6, 9 o 12 meses o 2 años, nada más, cada uno con su tasa modificable por el presidente. El interés se muestra aparte y se retira todo al terminar el contrato.

**Criterios del desarrollo, pendientes de confirmación:**

- **Mora por letra:** cada letra atrasada suma su propio centavo diario. Corre desde el vencimiento hasta que se paga lo demás de la letra, y se cobra al final. Es ganancia (línea 4.2) y se reparte como las demás.
- **Tasa de mora:** queda fijada en el crédito al aprobarlo, como las otras tasas. Cambiar `MORA_DIARIA` afecta a los créditos nuevos. Los activos de clientes al crear esta regla quedaron con USD 0,01, contando su atraso desde el vencimiento.
- **Precancelación:** además del capital, cobra la mora ya generada.
- **Interés del ahorro normal:** saldo × tasa ÷ 365 por día, sin capitalizar. Lo liberado el día 1 sigue disponible después de ese día, para que quien no entre ese día no lo pierda. Solo el interés tiene esta restricción: el saldo se retira cuando se quiera.
- **Contabilidad del interés de ahorros:** es gasto (5.1) y baja los fondos de los socios cuando se retira, no mientras corre. Lo que corre aún no aparece como deuda en el balance.
- **Cerrar la cuenta:** exige retirar antes el saldo y el interés de los ahorros, por lo que puede haber que esperar al siguiente día 1.
- **Tasas iniciales de plazo fijo:** 5,5 %, 6 %, 6,5 %, 7 % y 8 % anual. Son provisionales, a ajustar por el presidente. Los plazos fijos abiertos conservan su tasa.
- **Encaje:** "cuenta principal" exige tener el dinero antes del desembolso; "descontado del crédito" lo paga con el desembolso; "ahorros" sale de un ahorro normal, aunque no sea día 1; "efectivo" entra a la caja al aprobar, y quien aprueba confirma haberlo recibido.
- **Cupo de socio:** cada retiro espera aprobación y aparta cupo mientras tanto. La promoción abierta y los límites se validan al pedirlo y otra vez al aprobarlo.
- **Documentos:** solo PDF comprobado por su contenido, hasta 10 por crédito y 10 MB cada uno, en la base de datos. Desde la sección 16, la solicitud firmada es obligatoria para clientes.

## 16. Acceso, comprobantes, aprobación conjunta y socios

**Definido por el propietario (02/11/2026):**

- **Acceso:** se entra con el correo o la cédula. Tres contraseñas incorrectas seguidas bloquean el acceso; la presidencia lo desbloquea dando una contraseña temporal. Quien olvida su contraseña la pide desde «¿Olvidó su contraseña?». La presidencia puede corregir un correo mal escrito.
- **Datos:** la cédula tiene 10 números. El teléfono lleva el prefijo del país, +593 por defecto: con 0 inicial caben 10 números y sin él 9, y el 0 no se guarda. La fecha de nacimiento exige mayoría de edad.
- **Comprobantes:** todo dinero que llega de fuera (apertura y depósitos) se respalda con su comprobante.
- **Retiros a banco:** Banco Pichincha y Banco Guayaquil sin costo. Otro banco o cooperativa cuesta USD 0,41 (`COSTO_TRANSFERENCIA_OTROS`), lo que cobra el banco.
- **Aprobación de créditos:** la deciden tres áreas: presidencia o vicepresidencia, tesorería y comité de crédito.
- **Solicitud firmada:** los clientes suben en PDF la solicitud firmada del crédito a letras y del emergente.
- **Ahorro letras:** cada letra ahorra al menos USD 0,50, aunque la suma dé un dólar exacto.
- **Ahorros:** se les pone nombre (propuesto y editable) y se pueden eliminar tras pasar todo su dinero a la cuenta principal.
- **Aportes:** el aporte mensual y la caja chica se pueden pagar por varios meses, hasta todo el año.
- **Caja chica:** quien la administra registra los aportes que recibe en persona, eligiendo quién pagó.
- **Hacerse socio:** con el aporte inicial (`APORTE_INICIAL_MINIMO`, USD 1000), que no se retira: solo se vende a otro socio.
- **Finanzas para socios:** todos los socios ven balance, libros, tesorería, estado de caja, caja chica y resultados, sin registrar nada. En caja chica solo pagan lo suyo.
- **Códigos:** cada registro tiene un código legible derivado de su número: `CL-` crédito a letras, `CE-` emergente de cliente, `CS-` cupo de socio, `CP-` promoción, `SC-` retiro de cupo, `AH-` ahorro normal, `PF-` plazo fijo, `DP-` depósito, `RT-` retiro, `SO-` socio. Las cuentas siguen con `JAN-`.

**Criterios del desarrollo, pendientes de confirmación:**

- **Recuperar contraseña sin correo:** Janery no envía correos. El pedido le llega a la presidencia, que genera una contraseña temporal de 10 caracteres y se la entrega en persona o por mensaje. Al entrar con ella se obliga a elegir otra.
- **Cédula exacta:** se exigen exactamente 10 números (no «hasta 10»), como toda cédula ecuatoriana.
- **Teléfono en Ecuador:** celular de 9 números sin el 0 (empieza con 9); fijo de 8. En otros países, de 6 a 12.
- **Comprobante obligatorio solo en transferencias:** sin él no se puede confirmar. En efectivo es opcional, porque se entrega en mano. Se aceptan PDF, JPG, PNG y WEBP de hasta 10 MB, comprobados por su contenido.
- **Costo de transferencia:** se aparta de la cuenta junto con el retiro y sale de la caja al entregarlo (el banco lo descuenta a Janery). Se cobra a cualquier banco cuyo nombre no contenga «Pichincha» ni «Guayaquil».
- **Votos:** cada persona vota una vez y cuenta por una sola de sus áreas. Con los tres votos se decide: se aprueba solo si los tres aprueban; un rechazo con motivo basta para rechazarlo al completarse. El retiro de cupo de socio sigue la misma regla. Vicepresidencia, tesorería y comité de crédito recibieron el permiso `APROBAR_CREDITOS`.
- **Ahorro letras mínimo:** la letra se redondea al dólar siguiente contando 0,50 de ahorro; así cada letra ahorra entre 0,50 y 1,49.
- **Eliminar un ahorro:** el interés que aún no se liberó el día 1 se pierde, y se avisa antes de eliminar.
- **Adelantar aportes:** se admiten los meses del año en curso, no los del siguiente.
- **Aporte de caja chica en persona:** entra directo a caja chica, sin pasar por la cuenta del socio ni por la caja principal.
- **Socios y finanzas:** la vista de resultados no muestra la lista de socios con sus fondos a quien no tiene `GESTIONAR_RESULTADOS`.

## 17. Caja, avisos, uso, simulador y trámite de créditos

**Definido por el propietario (02/11/2026):**

- **Caja principal:** muestra cuánto subió o bajó cada mes, en dinero y en porcentaje. El mes en curso cambia con cada movimiento; queda «cerrado» al terminar el mes.
- **Aviso de la cabecera:** la presidencia publica un texto que corre por la cabecera de todos los paneles.
- **Uso de la aplicación:** el administrador ve qué pantallas se usan más, para decidir qué mejorar.
- **Simulador:** el administrador prueba en un ambiente aparte que no toca la base real (ver testing.md, sección 6).
- **Contacto:** +593 996619908, San Agustín de Cajas, González Suárez. Botón de WhatsApp y versión del programa en el pie.
- **Tesorería tramita los créditos.**
- **Modo claro** por defecto.
- **Créditos para clientes:** solo el crédito a letras normal y el emergente. Inversión, estudio y personalizado son exclusivos de socios.
- **Aviso:** corre de derecha a izquierda; la cabecera ya no muestra «Panel del…», para dejarle espacio en el celular.

**Criterios del desarrollo, pendientes de confirmación:**

- **Porcentaje de la caja:** variación del mes ÷ saldo al empezar el mes. Si el mes empezó sin saldo, no hay porcentaje.
- **Aviso:** pueden publicarlo quienes tengan `PUBLICAR_ANUNCIOS` (presidencia). Un aviso nuevo reemplaza al anterior; se guardan todos. Se detiene al pasar el ratón; quien prefiera menos movimiento lo ve quieto.
- **Uso:** se anota solo la ruta de cada pantalla abierta (sin datos de la pantalla) y quién la abrió, para contar personas distintas. Se agrupa en clientes, socios y personal con cargo. Lo ven quienes tengan `VER_USO_APP` (presidencia y administración).
- **Simulador:** decisión del propietario (02/11/2026): «Iniciar simulación» copia toda la base real de ese momento, también clientes y sus datos; «Finalizar simulación» la descarta. La base del simulador está en el mismo servidor y con las mismas protecciones que la real. Copiar tarda unos segundos en un servidor junto a la base (un minuto desde una computadora remota): el proxy no debe cortar esa petición antes de 5 minutos.
- **Trámite en ventanilla:** tesorería busca a la persona, confirma con ella sus datos para crédito, registra la solicitud (letras o emergente; a un socio, retiro de su cupo) y sube la solicitud firmada. Sigue necesitando los tres votos. El encaje en ventanilla puede ser en efectivo, de la cuenta o descontado del crédito.
- **Versión:** sale del `pom.xml` (0.2.0) y se muestra en el pie.

## 18. Créditos anteriores a Janery

**Definido por el propietario (02/11/2026):** los créditos a letras dados antes de usar Janery tienen tasas y plazos distintos entre sí. Se registran uno por uno, con `REGISTRAR_CREDITOS_EXISTENTES` (presidencia), en la pantalla de créditos.

- **Datos:** persona (con cuenta principal activa), modalidad, importe, plazo, interés y desgravamen en % mensual sobre el importe, fecha en que recibió el dinero, letras ya pagadas completas, encaje retenido en dólares y si llevaba ahorro letras. Con ahorro letras, las letras se calculan como en la sección 10; sin él, la letra es capital, interés y desgravamen, con centavos.
- **Lo ya ocurrido no se repite:** no entra dinero a la cuenta, no se cobra el encaje y lo cobrado antes no se reparte como ganancia. Las letras pagadas quedan pagadas en su fecha de vencimiento.
- **Balance:** entran solo los saldos del día del registro: el capital pendiente en 1.4 y el encaje retenido y el ahorro letras acumulado en 2.3. Hasta registrar también el dinero y los aportes que ya existían, el balance mostrará esa diferencia.
- **Desde el registro** se cobra como cualquier crédito: pagos, mora, precancelación y devolución de encaje y ahorro letras al terminar. Las letras ya vencidas que no estén entre las pagadas cuentan como atrasadas; a un cliente le corre la mora desde cada vencimiento, con la tasa vigente al registrarlo.
- No se registra un crédito con todas sus letras pagadas: ya terminó. Los emergentes y cupos anteriores no se registran por esta vía. Tampoco se les cobra suministros (sección 19).

## 19. Suministros de los créditos a letras

**Definido por el propietario (02/11/2026):**

- **Qué es:** al inicio de cada crédito a letras, de socios y de clientes, se cobra un porcentaje del importe para suministros: papelería y gastos de operación. Es 1,5 % en las modalidades normal y personalizado, y 0,75 % en inversión y estudio. Los porcentajes se cambian desde el CMS (`LETRAS_SUMINISTROS_<MODALIDAD>`) y quedan fijados en el crédito al solicitarlo, como el encaje.
- **Cómo se paga:** al aprobarse, del mismo origen que el encaje: en efectivo, de la cuenta principal, de un ahorro o descontado del crédito.
- **No se devuelve:** a diferencia del encaje, es ganancia. Va a la línea 4.8 «Suministros de créditos» y se reparte al momento como los intereses: 10 % a reservas y el resto a los socios.
- No se cobra en los emergentes, en los cupos ni en los créditos anteriores a Janery (sección 18), que ya lo pagaron en su momento.

## 20. Pagar

**Definido por el propietario (02/11/2026):** desde «Pagar», al final del menú, quien tiene cuenta activa paga de una vez lo que quiera: letras de créditos, depósitos a ahorros normales, aporte mensual, aporte personal, caja chica o un depósito a la cuenta principal. Un **cliente** solo paga lo suyo. Un **socio** puede pagar también lo de otras personas en el mismo pago (por ejemplo, 30 a la letra de una persona, 50 a la de otra, un depósito al ahorro de la primera y el aporte de una tercera); a un cliente no se le muestra esa opción y el servidor la rechaza (`SOLO_SOCIOS`).

- **Comprobante obligatorio:** sin al menos un comprobante (foto o PDF, hasta 5) no se registra el pago, sea en efectivo o por transferencia.
- **Otras personas:** se buscan por nombre o cédula entre quienes tienen cuenta activa. Quien paga ve solo su nombre y lo que se le puede pagar (créditos con la letra que falta, ahorros normales y, si es socio, sus meses de aporte); no ve su cédula ni su número de cuenta.
- **Nada cambia hasta confirmar:** el pago queda como un aviso de depósito (código DP) en «Depósitos y pagos por confirmar». Al confirmarlo, el dinero entra a la caja principal y a la cuenta principal de cada beneficiario, y de ahí se paga cada cosa como si la persona la hubiera pagado.
- **Todo o nada:** si una cosa de la lista ya no se puede pagar al confirmar (por ejemplo, el mes ya se pagó), no se confirma nada y el mensaje dice cuál falló; el personal puede rechazar el pago con un motivo. Al registrarlo también se rechaza entero si algo no es válido.
- Un pago a un crédito que supera la letra adelanta las siguientes, como en la sección de pagos de créditos. Quien paga puede cancelar el pago mientras esté pendiente.
- «Mi cuenta» salió del menú: el saldo y los botones de depositar y retirar están en el resumen, y el cierre de cuenta en Configuración → Cuenta.
