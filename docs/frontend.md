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

### Usuario autenticado sin cuenta operativa
- `/activar-cuenta` completar datos personales y proceso de apertura.
- Puede seguir consultando información, pero no realizar operaciones financieras hasta cumplir requisitos.

### Panel personal
- `/app/inicio`
- `/app/cuenta`
- `/app/movimientos`
- `/app/creditos`
- `/app/ahorros`
- `/app/inversiones` (solo cuando el alcance esté definido)
- `/app/perfil`

### Áreas de trabajo
- `/areas/administracion`
- `/areas/presidencia`
- `/areas/vicepresidencia`
- `/areas/secretaria`
- `/areas/tesoreria`
- `/areas/caja-chica`
- `/areas/comite-credito`
- `/areas/asuntos-sociales`

Las rutas de áreas son conceptuales; mostrar solo las áreas autorizadas.

### Administración
- `/admin/parametros` CMS de tasas, importes, límites y vigencias.
- `/admin/usuarios`
- `/admin/roles-permisos`
- `/admin/auditoria`
- `/admin/reportes`

## 3. Estructura sugerida

```text
src/app/
├── core/
│   ├── auth/
│   ├── guards/
│   ├── interceptors/
│   └── services/
├── shared/
│   ├── components/
│   ├── pipes/
│   └── utils/
├── layout/
│   ├── public-layout/
│   ├── app-layout/
│   └── admin-layout/
└── features/
    ├── auth/
    ├── public/
    ├── cuenta/
    ├── creditos/
    ├── ahorros/
    ├── socios/
    ├── areas/
    ├── parametros/
    ├── auditoria/
    └── reportes/
```

Adaptar al crecimiento real. No crear capas vacías sin necesidad.

## 4. Reglas de presentación

- Mostrar saldo disponible y depósito reembolsable de USD 5 por separado.
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
- Cada acción muestra su resultado en la misma pantalla: cargando, éxito o error.
- Los importes oficiales siempre vienen del backend; la pantalla no los calcula por su cuenta.

### Estilos con Tailwind
- Los estilos se escriben con clases de Tailwind en la plantilla.
- No se usan atributos `style` ni archivos CSS por componente.
- Lo que se repite en muchas pantallas (tarjeta, botón, tabla) se define una sola vez en `src/styles.css`, en `@layer components`, o como componente en `shared`.
- Colores (decisión del propietario): todo el sitio va del blanco al verde, también errores y acciones peligrosas, que se distinguen con símbolo, borde y verde oscuro. Se definen en el bloque `@theme` de `src/styles.css`, no como valores sueltos.
- Interacciones: enlaces, botones, tarjetas y filas reaccionan al pasar el cursor, con transiciones cortas. El menú cambia de color y crece. Quien tenga activada la opción de reducir movimiento en su sistema no las ve.
- Diseño adaptable: primero móvil, con los prefijos `sm:`, `md:` y `lg:`.

## 6. Accesibilidad y calidad

Diseño adaptable a escritorio y móvil, etiquetas accesibles, estados de carga/error/vacío, validación de formularios y mensajes claros. Los errores financieros deben explicar qué regla impidió la operación sin exponer datos sensibles.
