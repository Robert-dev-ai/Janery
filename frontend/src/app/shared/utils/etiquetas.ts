const FAMILIAS: Record<string, string> = {
  LETRAS: 'Crédito a letras',
  EMERGENTE_CLIENTE: 'Emergente',
  EMERGENTE_SOCIO: 'Cupo emergente',
  EMERGENTE_ESTACIONAL: 'Emergente estacional',
};

const CONCEPTOS: Record<string, string> = {
  DEPOSITO: 'Depósito',
  RETIRO: 'Retiro',
  RETIRO_SOLICITADO: 'Retiro solicitado',
  RETIRO_DEVUELTO: 'Retiro devuelto',
  DESEMBOLSO_CREDITO: 'Desembolso de crédito',
  ENCAJE_CREDITO: 'Encaje de crédito',
  PAGO_CREDITO: 'Pago de crédito',
  PRECANCELACION_CREDITO: 'Precancelación de crédito',
  LIBERACION_AHORRO_LETRAS: 'Ahorro letras devuelto',
  DEVOLUCION_ENCAJE: 'Encaje devuelto',
  APERTURA_PLAZO_FIJO: 'Apertura de plazo fijo',
  DEPOSITO_AHORRO: 'Depósito a ahorro',
  RETIRO_AHORRO: 'Retiro de ahorro',
  LIQUIDACION_PLAZO_FIJO: 'Liquidación de plazo fijo',
  INTERES_AHORRO: 'Interés de ahorro',
  INGRESO_APERTURA: 'Ingreso de apertura',
  DEPOSITO_REEMBOLSABLE: 'Depósito de apertura (del cliente)',
  DEVOLUCION_DEPOSITO: 'Devolución del depósito de apertura',
  ENCAJE_EFECTIVO: 'Encaje en efectivo',
  APORTE_OBLIGATORIO: 'Aporte mensual',
  APORTE_PERSONAL: 'Aporte personal',
  APORTE_CAJA_CHICA: 'Aporte a caja chica',
  RETIRO_GANANCIA: 'Retiro de ganancias',
  DESCUENTO_DE_GANANCIAS: 'Descuento de ganancias',
  COMPRA_DE_CAPITAL: 'Compra de capital',
  VENTA_DE_CAPITAL: 'Venta de capital',
  // Libro diario, libro mayor y cajas
  APERTURA: 'Apertura de cuenta',
  ASIENTO_MANUAL: 'Registro manual',
  DEPOSITO_CUENTA: 'Depósito a la cuenta',
  RETIRO_CUENTA: 'Retiro entregado',
  RETIRO_APARTADO: 'Retiro pedido (apartado)',
  RETIRO_RESUELTO: 'Retiro resuelto',
  DESEMBOLSO: 'Desembolso de crédito',
  ENCAJE: 'Encaje de crédito',
  PAGO_CAPITAL: 'Pago de letra: capital',
  PAGO_INTERES: 'Pago de letra: interés',
  PAGO_FONDO_DESGRAVAMEN: 'Pago de letra: desgravamen',
  PAGO_AHORRO_LETRAS: 'Pago de letra: ahorro letras',
  PAGO_MORA: 'Pago de mora',
  RESERVA: 'Fondo de reserva',
  RESERVA_DE_GANANCIA: 'Reserva de la ganancia',
  TRASPASO_A_RESERVA: 'Traspaso al fondo de reserva',
  TRASPASO_A_CAJA_CHICA: 'Traspaso a caja chica',
  INTERES: 'Interés de ahorro',
  LIQUIDACION: 'Liquidación de plazo fijo',
  RETIRO_INTERES: 'Retiro de interés de ahorro',
  APORTE_INICIAL: 'Aporte inicial',
  APORTE_COMPRA: 'Compra de capital',
  APORTE_VENTA: 'Venta de capital',
  DESCUENTO_OBLIGACION: 'Descuento por obligaciones',
  APORTE_SOCIO: 'Aporte a caja chica',
  GASTO: 'Gasto de caja chica',
};

export function nombreConcepto(codigo: string): string {
  if (codigo?.startsWith('MANUAL: ')) {
    return `Registro manual: ${codigo.slice(8)}`;
  }
  return CONCEPTOS[codigo] ?? codigo;
}

const CARGOS: Record<string, string> = {
  ADMINISTRACION: 'Administrador',
  PRESIDENCIA: 'Presidencia',
  VICEPRESIDENCIA: 'Vicepresidencia',
  SECRETARIA: 'Secretaría',
  TESORERIA: 'Tesorería',
  CAJA_CHICA: 'Caja chica',
  COMITE_CREDITO: 'Comité de crédito',
  ASUNTOS_SOCIALES: 'Asuntos sociales',
};

/** Su cargo; sin cargo, socio; si no es socio, cliente. */
export function rolDe(yo: { cargos: string[]; esSocio: boolean } | null): string {
  if (!yo) {
    return '';
  }
  if (yo.cargos?.length) {
    return yo.cargos.map((c) => CARGOS[c] ?? c).join(', ');
  }
  return yo.esSocio ? 'Socio' : 'Cliente';
}

export function nombreFamilia(codigo: string): string {
  return FAMILIAS[codigo] ?? codigo;
}

export function nombreCargo(codigo: string): string {
  return CARGOS[codigo] ?? codigo;
}

const AREAS: Record<string, string> = {
  DIRECCION: 'Presidencia o vicepresidencia',
  TESORERIA: 'Tesorería',
  COMITE_CREDITO: 'Comité de crédito',
};

/** Área que vota la aprobación de un crédito. */
export function nombreArea(codigo: string): string {
  return AREAS[codigo] ?? codigo;
}

export const AREAS_APROBACION = Object.keys(AREAS);
