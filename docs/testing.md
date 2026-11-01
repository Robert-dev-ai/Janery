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

## 6. Simular el paso del tiempo

Para ver a mano intereses, vencimientos o el retiro de ganancias del 1 de enero sin esperar:

1. Detener el backend normal, porque usa el mismo puerto.
2. Ejecutar `.\simulacion.ps1` desde la raíz. Crea la base `janery_simulacion` si falta y arranca con el perfil `simulacion`.
3. Iniciar sesión con un usuario con permiso `ADMIN_PARAMETROS` y avanzar la fecha con la barra amarilla, o con `POST /api/v1/simulacion/avanzar` y `{"dias": 15}` o `{"fecha": "2027-01-01"}`.

Reglas:
- La fecha solo avanza. Para repetir una prueba, `.\simulacion.ps1 -Reiniciar` borra los datos simulados y vuelve a la fecha real.
- Al cruzar cada día 1 se ejecuta el abono mensual de intereses de ahorros.
- La fecha simulada se conserva al reiniciar el backend.
- Las columnas con `DEFAULT now()` que la aplicación no llena muestran la fecha real.
- Un salto de más de 7 días vence la sesión de refresco: habrá que volver a iniciar sesión.
- El perfil no arranca sobre una base o esquema sin "simulacion" en su URL. Nunca activarlo en producción.

## 7. Criterio de aceptación

Cada módulo debe tener pruebas relevantes aprobadas, migraciones verificadas en base de prueba y revisión manual de los flujos financieros antes de habilitarse.
