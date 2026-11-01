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
- **Pagos parciales de letras (definido por el propietario):** se admiten, y lo primero que se cobra es el capital. Luego, a criterio del desarrollo, interés, desgravamen y ahorro letras. También aplican al emergente de cliente. Encaje y ahorro letras solo se usan para cubrir lo que falta de la última letra completa.
- **Encaje:** porcentaje del importe, descontado de la cuenta principal al desembolsar.
- **Tasas de un crédito a letras:** quedan fijadas al solicitarlo.
- **Emergente de cliente:** interés y desgravamen de un mes sobre el importe, en una sola cuota al mes siguiente. Se desembolsa al momento, sin aprobación.
- **Cupo de socio:** interés y desgravamen diarios simples sobre el capital utilizado. Cada pago cubre primero cargos y luego capital. No tiene fecha de vencimiento. Cada disposición actualiza la tasa a la vigente.
- **Promociones:** usan solo la tasa de 0,05 % diario, sin desgravamen. El máximo cuenta todo lo retirado en la promoción, aunque se haya pagado.
- **Perfil:** quien es socio usa las condiciones de socio; el resto, las de cliente.
- **Ahorro normal:** interés anual dividido en 12, abonado el día 1 sobre el saldo de ese momento. Si el proceso no corre ese día, ese mes no se abona automáticamente.
- **Plazo fijo:** interés simple al vencer, con retiro total; sin penalización porque no hay retiro anticipado.
- **Caja principal:** refleja el dinero real. Solo cambia con depósitos, retiros e ingreso de apertura; créditos y ahorros se mueven dentro de la cuenta de cada persona.
- **Aprobación de créditos a letras:** Presidencia, con el permiso `APROBAR_CREDITOS`.

## 11. Socios y fondos

**Definido por el propietario (31/10/2026):**

- **Fondo personal:** el dinero de cada socio es un solo valor, pero se lleva separado por origen: aportes iniciales, aportes mensuales, aportes personales y ganancias.
- **Ganancias:** van al fondo personal de cada socio y se quedan ahí; no se pagan a la cuenta principal.
- **Fondo de reserva:** el 10 % se separa cada vez que se reparte una ganancia, no al cerrar el año.
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
- **Registro de ganancias y pérdidas:** a mano, por quien tenga `GESTIONAR_RESULTADOS`; ningún ingreso se convierte solo en ganancia. Se reparten según el valor de cada socio en ese momento. Una pérdida no puede superar el capital total.
- **Evolución mensual:** se calcula de las variaciones; el valor inicial de un mes es el final del anterior.
- **Cierre anual:** solo años terminados y una vez por año. Totaliza y bloquea nuevos resultados en ese año; no mueve dinero.
- **Aportes iniciales:** los registra Presidencia como dinero recibido fuera del sistema; entran a la caja principal y al fondo personal.
- **Caja chica:** el aporte de USD 2,50 sale de la cuenta del socio y pasa de la caja principal a caja chica. No aumenta la participación.
- **Fondo de reserva:** caja propia; recibe el traspaso desde la caja principal.
- **Aportes mensuales y personales:** los paga el socio desde su cuenta principal, y puede ponerse al día con meses pasados.
- **Cierre de cuenta:** un socio activo no puede cerrar su cuenta principal.

**Fuera de esta versión, por decisión del propietario:** mora y refinanciación. Las letras pueden pagarse antes de su vencimiento, una a una.
