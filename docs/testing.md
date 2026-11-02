# Janery — estrategia de pruebas

## 1. Principios

Los cálculos financieros y movimientos requieren pruebas antes de habilitarse. No basta con que la pantalla funcione.

## 2. Backend

- Unit tests de servicios y reglas de negocio.
- Pruebas de validación de DTO.
- Pruebas de autorización por rol/permisos.
- Pruebas de repositorios e integración con PostgreSQL en entorno de prueba aislado.
- Pruebas transaccionales: operación completa o rollback.
- Pruebas de idempotencia contra pagos/desembolsos duplicados.
- Pruebas de auditoría.

## 3. Cálculos financieros

Cada cálculo debe tener ejemplos aprobados por el propietario y pruebas para:
- Límites exactos y valores fuera de límite.
- Redondeos monetarios.
- Cambio de parámetros y vigencias.
- Saldo utilizado, pagos y cupo disponible.
- Interés diario/mensual conforme a la fórmula que se apruebe.
- Cuotas y redondeo de ahorro letras.
- Encaje pagado al inicio y devolución al terminar.
- Separación de fondos.

No inventar resultados esperados cuando la fórmula siga pendiente; marcar el test como bloqueado por definición de negocio.

## 4. Frontend

- Pruebas de componentes y servicios críticos.
- Estados de carga, error, vacío y éxito.
- Guards y navegación por permisos.
- Formularios y validaciones.
- No asumir que guard de frontend reemplaza backend.

## 5. Seguridad

- Acceso no autorizado a datos ajenos.
- Intentos de acciones sin permiso.
- No exposición de secretos.
- Validación de entradas y errores seguros.

## 6. Simulador

Una segunda instancia de Janery, con su propia base (`janery_simulacion`), para probar créditos, ahorros o aportes y ver pasar el tiempo sin tocar los datos reales. Corre junto a la instancia normal:

1. Backend: `.\simulacion.ps1` desde la raíz (puerto 8081, perfil `simulacion`). Crea la base si falta (requiere `psql`; sin él, usa la existente).
2. Frontend: `cd frontend; npm run simulador` (http://localhost:4201). La instancia normal lo ofrece a quien tenga `USAR_SIMULADOR` en Administración → Simulador, con `JANERY_SIMULADOR_URL`.
3. Entrar con un usuario del personal. La barra amarilla permite:
   - **Iniciar simulación**: copia toda la base real tal como está en ese momento (personas, cuentas, créditos, ahorros, socios, cajas, contabilidad y auditoría) sobre la base del simulador. Las sesiones abiertas en el simulador se conservan. La base real se lee con una conexión de solo lectura (`readOnlyMode=always`): PostgreSQL rechaza cualquier escritura por ella. Ambas bases deben estar en la misma versión de migraciones.
   - **Avanzar la fecha** (+1, +15, +30 días o hasta una fecha), o `POST /api/v1/simulacion/avanzar` con `{"dias": 15}` o `{"fecha": "2027-01-01"}`.
   - **Crear una persona de prueba**: cuenta activa con 1000 USD; con «socio» al final del nombre, también socia. Muestra su correo y su contraseña (`Prueba-1234`).
   - **Volver a copiar los datos**: descarta lo probado y copia de nuevo la base real de ese momento.
   - **Finalizar simulación**: descarta todo lo probado y vuelve a la fecha real. Quedan solo el personal (usuarios con cargo), los permisos y los parámetros, para poder entrar e iniciar otra.

Pueden usarlo `ADMIN_PARAMETROS` y `USAR_SIMULADOR` (presidencia y administración).

Publicado, el simulador es otra instancia del mismo `.jar` con `SPRING_PROFILES_ACTIVE=simulacion`, en su propio subdominio, con el frontend construido con `npm run build:simulador`. Ver [despliegue.md](despliegue.md).

Reglas:
- La fecha solo avanza. Para repetir una prueba, «Volver a copiar los datos» o «Finalizar simulación».
- El proxy del simulador publicado no debe cortar `/api/v1/simulacion/iniciar` antes de 5 minutos: copiar tarda segundos junto a la base, pero hasta un minuto desde una computadora remota.
- Interés de ahorros, atraso y mora se calculan con la fecha simulada al consultarlos: se ven al avanzar, sin procesos programados.
- `.\simulacion.ps1 -SegundoPlano` arranca sin ventana, con la salida en `simulacion.log`, y `.\simulacion.ps1 -Detener` lo detiene. Úsese si el backend se cierra solo: arrancado en una ventana, muere si esa consola se cierra o recibe Ctrl+C, y lo único que queda en el log es `^C`.
- La fecha simulada se conserva al reiniciar el backend.
- Las columnas con `DEFAULT now()` que la aplicación no llena muestran la fecha real.
- Un salto de más de 7 días vence la sesión de refresco: habrá que volver a iniciar sesión.
- El perfil no arranca sobre una base o esquema sin "simulacion" en su URL. Nunca activarlo en producción.

## 7. Criterio de aceptación

Cada módulo debe tener pruebas relevantes aprobadas, migraciones verificadas en base de prueba y revisión manual de los flujos financieros antes de habilitarse.
