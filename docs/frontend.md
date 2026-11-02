# Janery — frontend Angular

## 1. Tecnología

- Angular 20.x y TypeScript.
- Componentes standalone.
- Angular Router.
- Formularios reactivos.
- Servicios HTTP para comunicación con API REST.
- Guards para experiencia de navegación; no sustituyen autorización del backend.
- Tailwind CSS 4, integrado mediante PostCSS.

## 2. Experiencia y rutas conceptuales

### Público
- `/` página informativa de Janery.
- `/registro` registro de credenciales.
- `/iniciar-sesion` login.
- `/informacion` información institucional y productos.

Usa `LayoutPublico`: barra superior, sin menú lateral.

### Usuario autenticado sin cuenta operativa
- `/activar-cuenta` completar datos personales y proceso de apertura.
- Puede seguir consultando información, pero no realizar operaciones financieras hasta cumplir requisitos.

### Panel personal (Panel del socio o Panel del cliente)
Usa `LayoutPanel` con lateral claro. El menú está en `layout/navegacion.ts`.
- `/app/inicio` resumen de cuenta, créditos y cuotas, ahorros, fondo y movimientos.
- `/app/cuenta`
- `/app/movimientos`
- `/app/creditos`
- `/app/ahorros`
- `/app/inversiones` (solo socios)
- `/app/depositos` avisar un depósito en efectivo o por transferencia
- `/app/retiros` pedir dinero en efectivo o al banco
- `/app/socios/general` estado general, exclusivo de socios
- `/mis-cargos` solo para quien tiene cargo

### Configuración (menú de perfil del header)
- `/configuracion/perfil`
- `/configuracion/contrasena`
- `/configuracion/sesiones`

### Áreas de trabajo
- `/areas/administracion`
- `/areas/presidencia`
- `/areas/vicepresidencia`
- `/areas/secretaria`
- `/areas/tesoreria`
- `/areas/estado-caja`: estado y resultado de la caja, con gráficos.
- `/areas/caja-chica`
- `/areas/comite-credito`
- `/areas/asuntos-sociales`

Las rutas de áreas son conceptuales; mostrar solo las áreas autorizadas.

### Panel administrativo
Usa `LayoutPanel` con lateral oscuro. El menú agrupa por módulos, muestra solo lo que autoriza cada permiso y solo enlaza pantallas que existen. Quien tiene algún permiso entra aquí al iniciar sesión.
- `/admin/panel` indicadores, resumen financiero, gráficos y actividad reciente, según permisos.
- `/admin/personas`, `/admin/solicitudes-deposito`, `/admin/solicitudes-retiro`, `/admin/historial-crediticio`
- `/admin/contabilidad/balance`, `/diario`, `/mayor`, `/registro`
- `/admin/parametros` CMS de tasas, importes, límites y vigencias.
- `/admin/usuarios`
- `/admin/roles-permisos`
- `/admin/auditoria`
- `/admin/reportes`

## 3. Estructura

```text
src/app/
├── core/        autenticación, guards y servicios HTTP
├── layout/      layout-publico, layout-panel y menús (navegacion.ts)
├── shared/      components (estado, indicador, gráfico, paginador, diálogo) y utils
└── features/    admin, ahorros, arcade, areas, auth, configuracion, creditos, cuenta, public, socios
```

Cada pantalla se carga bajo demanda. Adaptar al crecimiento real; no crear capas vacías sin necesidad. El sistema visual y la cobertura del menú están en `diseno.md`.

## 4. Reglas de presentación

- Mostrar el saldo disponible sin incluir el depósito reembolsable de USD 5. Ese depósito solo se muestra al solicitar el cierre de la cuenta (decisión del propietario, 01/11/2026).
- Mostrar desglose de cuotas: capital, ahorro letras, fondo de desgravamen e interés.
- Mostrar ahorro letras y encaje asociados a su crédito.
- Mostrar cupo emergente utilizado y disponible para socios.
- Mostrar aportes obligatorios y personales en apartados separados.
- Mostrar evolución mensual de participación con saldo inicial, variaciones +/− y saldo final.
- Separar visual y funcionalmente caja principal y caja chica.
- Mostrar en CMS unidad y vigencia de cada parámetro.
- No permitir que el usuario introduzca o modifique directamente el saldo oficial.

## 5. Base de construcción: dinámica y con Tailwind

Toda pantalla nueva o modificada sigue estas reglas.

### Aplicación dinámica
- Navegar, enviar un formulario o ejecutar una acción no recarga la página.
- Tiempo real (decisión del propietario): lo que hace una persona aparece solo en las pantallas de las demás, sin recargar. Toda pantalla que muestra datos llama a `alCambiar` con su función de carga.
- Tras una operación, la pantalla pide de nuevo los datos al backend y muestra saldos y estados actualizados.
- El aviso en tiempo real (`GET /api/v1/eventos`) solo indica el módulo que cambió, nunca datos: cada pantalla los pide a la API, que valida permisos. Toda acción que cambie datos debe pasar por `AuditoriaService.registrar`, que es quien avisa.
- El estado de cada pantalla se guarda en signals de Angular.
- Cada acción muestra su resultado en la misma pantalla: cargando, éxito o error. Las confirmaciones usan `DialogoService`, no `confirm()` ni `prompt()`.
- Los importes oficiales siempre vienen del backend; la pantalla no los calcula por su cuenta.

### Estilos con Tailwind
- Los estilos se escriben con clases de Tailwind en la plantilla.
- No se usan atributos `style` ni archivos CSS por componente. Los gráficos son SVG con atributos.
- Lo que se repite en muchas pantallas (tarjeta, botón, tabla) se define una sola vez en `src/styles.css`, en `@layer components`, o como componente en `shared`.
- Colores (decisión del propietario, 01/11/2026; reemplaza a «todo del blanco al verde»): el verde es la identidad y se usa en navegación, acciones principales y estados positivos. Las superficies son blancas y grises, y los colores de apoyo (azul, naranja, rojo) comunican información, avisos y errores, siempre con texto o símbolo. Se definen en el bloque `@theme` de `src/styles.css`, no como valores sueltos.
- Interacciones: enlaces, botones, indicadores y filas reaccionan al pasar el cursor, con transiciones cortas. Quien tenga activada la opción de reducir movimiento en su sistema no las ve.
- Diseño adaptable: primero móvil, con los prefijos `sm:`, `md:` y `lg:`.

### Janery Arcade
Diez minijuegos en el hueco libre de la cabecera de `LayoutPanel`, con la altura del aviso (`h-9`). Si hay un aviso publicado, el aviso tiene prioridad y el arcade solo se ve en pantallas `xl`.
- `features/arcade/arcade.ts`: la barra y sus estados (invitación, partida, resultado). Al cargar la página elige un juego al azar distinto del anterior (guardado en `sessionStorage`) y no lo cambia durante la partida.
- `features/arcade/catalogo.ts`: lista de juegos, elección al azar y tope de puntos creíble de cada uno.
- `features/arcade/motor.ts`: clase base `Juego` y utilidades de dibujo. Cada juego (`juegos/*.ts`) es una clase sin Angular que dibuja en un `canvas` y se descarga solo al jugarlo.
- `features/arcade/marcas.ts`: récord, fecha y últimas 10 partidas de cada persona en `localStorage`, con la clave `janery-arcade-<id de usuario>`. Decisión del propietario (02/11/2026): el arcade no toca la base de datos. Por eso los récords valen solo en ese navegador, y una marca que supera el tope de su juego se descarta.
- El bucle y los eventos corren fuera de la detección de cambios de Angular y se limpian al salir, al terminar o al cambiar de juego. Las teclas solo se atienden con el juego enfocado; si pierde el foco, se pausa. En pantalla táctil, `touch-action: none` evita que la página se desplace mientras se juega.
- Los colores de la barra están en `@theme` (`--color-arcade-*`); los de cada juego forman parte de su dibujo.

## 6. Accesibilidad y calidad

Diseño adaptable a escritorio y móvil, etiquetas accesibles, estados de carga/error/vacío, validación de formularios y mensajes claros. Los errores financieros deben explicar qué regla impidió la operación sin exponer datos sensibles.
