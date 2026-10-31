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

## 6. Criterio de aceptación

Cada módulo debe tener pruebas relevantes aprobadas, migraciones verificadas en base de prueba y revisión manual de los flujos financieros antes de habilitarse.
