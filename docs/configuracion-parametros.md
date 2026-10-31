# Janery — CMS de parámetros y configuración

## 1. Requisito

Crear un módulo administrativo tipo CMS que permita cambiar desde la interfaz de Janery los parámetros del negocio sin modificar código. Los valores oficiales se guardan y validan en backend/base de datos.

## 2. Parámetros identificados

- Importe inicial de apertura.
- Distribución de los USD 20: USD 15 ingreso Janery y USD 5 depósito reembolsable.
- Tasas por modalidad de crédito a letras y perfil socio/cliente.
- Encaje por modalidad/perfil.
- Componentes de letras y ahorro letras.
- Fondo de desgravamen.
- Límite general de emergentes.
- Frecuencia de uso de emergentes para clientes.
- Tasas mensuales y diarias.
- Límite individual y tasa de emergentes estacionales.
- Meses de apertura/cierre de promociones.
- Tasas de ahorro normal y plazo fijo.
- Plazos de plazo fijo.
- Aporte obligatorio mensual de socios.
- Aporte mensual de caja chica.
- Porcentaje del fondo de reserva.
- Otros importes, tasas, límites y periodos incorporados en el futuro.

## 3. Datos por parámetro

Código estable, nombre, descripción, valor, unidad (USD, porcentaje diario/mensual/anual, días, meses), fecha inicial/final de vigencia, estado, creador, aprobador/publicador cuando aplique y motivo.

## 4. Historial y aplicación

Cada cambio registra quién, cuándo, valor anterior/nuevo, motivo y vigencia. Los cambios no recalculan automáticamente operaciones históricas. Cada crédito/cuota/cálculo conserva una instantánea de parámetros usados.

## 5. Seguridad y validación

Solo usuarios autorizados modifican parámetros. Validar rangos, unidades, coherencia entre parámetros y vigencias. Mostrar vista previa del impacto cuando sea posible. Validar en backend aunque exista validación visual en Angular.
