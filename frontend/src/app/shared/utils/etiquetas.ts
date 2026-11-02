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
};

export function nombreConcepto(codigo: string): string {
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
