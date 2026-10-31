# Janery — frontend Angular

## 1. Tecnología

- Angular 20.x y TypeScript.
- Componentes standalone.
- Angular Router.
- Formularios reactivos.
- Servicios HTTP para comunicación con API REST.
- Guards para experiencia de navegación; no sustituyen autorización del backend.

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

## 5. Accesibilidad y calidad

Diseño adaptable a escritorio y móvil, etiquetas accesibles, estados de carga/error/vacío, validación de formularios y mensajes claros. Los errores financieros deben explicar qué regla impidió la operación sin exponer datos sensibles.
