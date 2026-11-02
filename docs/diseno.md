# Janery 2.0 — diseño y navegación

Complementa a `frontend.md`, que conserva las rutas y las reglas de construcción. Aquí están el sistema visual, qué cubre el menú y qué falta en el backend para completar el diseño.

## 1. Tres experiencias

| Experiencia | Layout | Para quién |
|---|---|---|
| Sitio público | `layout/layout-publico.ts`, barra superior | Personas que aún no usan Janery |
| Panel del socio o del cliente | `layout/layout-panel.ts`, lateral claro | Consulta de los productos propios |
| Panel administrativo | `layout/layout-panel.ts`, lateral verde oscuro | Personal con cargos o permisos |

Portal y panel comparten el mismo marco, por eso son un solo componente; la ruta indica la experiencia con `data.experiencia`. Quien tiene ambos accesos cambia entre ellos desde la cabecera. Los menús están en `layout/navegacion.ts`.

## 2. Sistema visual

Los valores están en `src/styles.css`: los colores en `@theme` y las piezas repetidas en `@layer components`.

- **Verde:** marca, navegación, acciones principales y estados positivos.
- **Neutros:** fondo gris claro, tarjetas blancas, divisores y texto oscuro.
- **Apoyo:** azul para información, naranja para avisos y rojo para errores y acciones peligrosas. Siempre con texto o símbolo: el color nunca informa solo.
- **Tipografía:** Inter si está instalada, si no la del sistema. No se descarga ninguna fuente.
- **Iconos:** solo `lucide-angular`.

Componentes en `shared/components`: `Estado`, `Indicador`, `GraficoBarras`, `GraficoCircular`, `GraficoColumnas`, `Paginador` y `Dialogo`.

### Áreas visibles

Una pantalla no debe verse como texto corrido: cada tema va en su propia área.

- **`.seccion` y `.seccion-titulo`:** bloque con borde y fondo gris que agrupa tarjetas de un mismo tema.
- **`.tarjeta`:** su primer `h2` o `h3` se convierte solo en una cabecera verde tenue. Los importes y formularios internos llevan su propio recuadro.
- **`.seccion-accion`:** fondo verde tenue para lo que crea o solicita algo, y así distinguirlo de lo que solo informa.
- **`.total`:** franja verde oscura con el total de la pantalla y su desglose. La usan Ahorros y Créditos.

### Logo y fondo

- **Archivo:** `public/logo-icono.png` se usa en el menú lateral, la barra pública, la pestaña del navegador y el fondo. Es cuadrado, con la moneda centrada y fondo transparente.
- **Recorte:** el contorno exterior no debe tener píxeles claros, porque sobre fondo oscuro se ven como un halo. Sus píxeles semitransparentes llevan el color de la línea (negro) y solo cambia su opacidad.
- **Sin fondo detrás:** el logo no lleva círculo ni fondo blanco en CSS.
- **Fondo:** el logo va en `body`, centrado y sin repetir, sobre el verde `intenso`. El contenido va sobre una hoja clara al 90 % (`.contenido`, `.publico-contenido`) para que el texto conserve el contraste. Se cambia con `background-image` en `styles.css`.
- **Tamaño:** el archivo mide unos 250 px y como fondo se amplía. Para que se vea nítido a gran tamaño hace falta el logo original en mayor resolución.

### Tipos de producto

Cuando a un tipo de crédito o ahorro le sigue su explicación, el tipo va en `<strong>`. Dentro de `.suave` se ve oscuro y seminegrita, para distinguirlo de la explicación gris. Un `<option>` no admite negrita: la explicación va debajo del selector.

### Atraso

`.alerta-atraso` avisa de las letras atrasadas en Resumen y Créditos. El conteo lo da el backend (`letrasAtrasadas`, `moraPendiente`), nunca la fecha del navegador: en la simulación no coinciden.

### Campos numéricos

Todo campo de importe usa `type="number" min="0" step="1"`: las flechas suben y bajan de un dólar en un dólar, y al hacer clic se pueden escribir centavos. `min` debe ser 0, porque el navegador cuenta los pasos desde `min`: con 0.01 las flechas darían 1.01, 2.01. Los formularios de Angular llevan `novalidate`, así que 10.50 no se rechaza por no ser múltiplo del paso; el backend valida el importe.

### Gráficos

| Tipo | Componente | Cuándo |
|---|---|---|
| Circular | `GraficoCircular` | Partes de un total, con pocas categorías: desde la quinta se agrupan en "Otros" |
| Columnas (histograma por periodo) | `GraficoColumnas` | Evolución en el tiempo, con una o más series sobre un solo eje |
| Barras | `GraficoBarras` | Comparar valores de una sola serie |

- **Colores:** `--color-serie-1` a `--color-serie-5` en ese orden, sin ciclar. El orden se validó para daltonismo sobre fondo blanco. Tres de ellos tienen poco contraste, por eso cada valor también se puede leer como texto.
- **Identidad y valores:** la leyenda escribe nombre, importe y porcentaje; el texto nunca lleva el color de la serie.
- **Columnas:** detalle al pasar el puntero o enfocar con el teclado, y una vista "Ver como tabla".
- **Un solo eje:** nunca dos escalas en el mismo gráfico. Medidas distintas van en gráficos distintos.

`Dialogo` reemplaza a `confirm()` y `prompt()`: se usa con `DialogoService` y se monta una sola vez en `app.ts`.

Cargando y sin datos son clases de CSS (`.cargando`, `.vacio`), porque no necesitan lógica.

No se crearon `page-header`, `breadcrumb`, `data-table`, `filter-bar` ni `toast`. Un `h1`, la clase `.tabla`, la clase `.herramientas` y los mensajes `.exito`/`.error` ya cubren esos casos, y la navegación solo tiene dos niveles. Se crearán si aparece una necesidad real.

## 3. Menú administrativo frente a lo existente

| Propuesta | Cómo se atiende |
|---|---|
| Dashboard, actividad reciente | `/admin/panel` |
| Personas | `/admin/personas`, solo consulta |
| Usuarios y permisos | `/admin/usuarios`, `/admin/roles-permisos` |
| Socios | `/admin/retiros`, `/admin/resultados`; el alta se hace desde Usuarios |
| Cuentas, créditos | `/admin/cuentas`, `/admin/creditos` |
| Depósitos y retiros de dinero | `/admin/solicitudes-deposito`, `/admin/solicitudes-retiro` |
| Ahorros, cuotas, encajes, aportes, fondo de reserva, historial de operaciones | Abren su informe con `/admin/reportes?informe=CODIGO` |
| Balance general e indicadores | `/admin/contabilidad/balance` |
| Libro diario, libro mayor | `/admin/contabilidad/diario`, `/admin/contabilidad/mayor` |
| Registro manual | `/admin/contabilidad/registro` |
| Caja y tesorería, resultados | `/areas/tesoreria`, `/areas/estado-caja` (gráficos), `/areas/caja-chica`, `/admin/resultados` |
| Informes y exportaciones | `/admin/reportes`, con CSV |
| Historial crediticio | `/admin/historial-crediticio` |
| Auditoría, parámetros | `/admin/auditoria`, `/admin/parametros` |
| Áreas y cargos | `/areas`, `/admin/roles-permisos` |
| Configuración | Menú de perfil de la cabecera |

En el portal, `/app/depositos` y `/app/retiros` permiten mover dinero. En el menú aparecen pequeños debajo de Mi cuenta (`sub: true` en `navegacion.ts`), para ocupar menos espacio. `/app/socios/general` es el área general, visible solo para socios.

Ahorros y Créditos abren con su total: lo que tiene y lo que debe. El Resumen muestra los dos como indicadores; su total de ahorros no incluye el ahorro letras, que sí se suma y se desglosa en Ahorros. Créditos termina con el historial de todos los créditos de la persona, en cualquier estado.

## 4. Contabilidad

La vista `libro_contable` (migración V12) traduce cada movimiento a las líneas del balance del propietario. Balance, libros, indicadores y panel se calculan de ella, sin un cálculo aparte para cada uno y sin guardar otra copia de los saldos. Si algo no cuadra, el balance muestra la diferencia. Las reglas están en `reglas-negocio.md`, sección 12.

## 5. Limitaciones conocidas

- La búsqueda de las tablas filtra solo la página visible, porque la API no acepta un texto de búsqueda.
- Fechas e importes usan el formato predeterminado de Angular (`en-US`).
- Auditoría y actividad reciente muestran los códigos de acción tal como los guarda el backend.
