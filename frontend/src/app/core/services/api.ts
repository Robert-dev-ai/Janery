import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

export interface Pagina<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  total: number;
}

export interface EstadoSimulacion {
  fecha: string;
  ahora: string;
  diasAdelantados: number;
}

export interface DatosCredito {
  lugarTrabajo: string;
  ocupacion: string;
  antiguedadTrabajo: string;
  ingresosMensuales: number;
  fechaNacimiento: string;
  estadoCivil: string;
  referenciaNombre: string;
  referenciaTelefono: string;
  banco: string;
  tipoCuentaBanco: string;
  numeroCuentaBanco: string;
}

export interface Perfil extends Partial<{ [K in keyof DatosCredito]: DatosCredito[K] | null }> {
  nombres: string;
  apellidos: string;
  identificacion: string;
  telefono: string;
  direccion: string | null;
  datosCreditoCompletos?: boolean;
}

export type Solicitante = Omit<Perfil, 'nombres' | 'apellidos' | 'datosCreditoCompletos'> & { nombre: string };

export interface Cuenta {
  id: number;
  numero: string;
  titular: string;
  estado: string;
  saldoDisponible: number;
  depositoReembolsable: number | null;
  moneda: string;
  metodoPago: string;
  referenciaPago: string | null;
  abiertaEn: string | null;
}

export interface MovimientoCuenta {
  id: number;
  sentido: string;
  importe: number;
  concepto: string;
  detalle: string | null;
  saldoResultante: number;
  fecha: string;
}

export interface ValorParametro {
  valor: number;
  vigenteDesde: string;
  vigenteHasta: string | null;
}

export interface Parametro {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string;
  unidad: string;
  vigente: ValorParametro | null;
  programado: ValorParametro | null;
}

export interface HistorialParametro {
  valorAnterior: number | null;
  valorNuevo: number;
  vigenteDesde: string;
  actorId: number | null;
  motivo: string;
  fecha: string;
}

export interface UsuarioResumen {
  id: number;
  correo: string;
  estado: string;
  creadoEn: string;
  cargos: string[];
  socio: boolean;
}

export interface Letra {
  numero: number;
  vencimiento: string;
  capital: number;
  ahorroLetras: number;
  fondoDesgravamen: number;
  interes: number;
  total: number;
}

export interface Credito {
  id: number;
  familia: string;
  modalidad: string | null;
  perfil: string;
  estado: string;
  importe: number;
  plazoMeses: number | null;
  tasaInteres: number;
  tasaDesgravamen: number;
  unidadTasa: string;
  capitalPendiente: number;
  interes: number;
  desgravamen: number;
  totalPendiente: number;
  encaje: number | null;
  encajeDevuelto: boolean;
  ahorroLetras: number | null;
  titular: string;
  motivoRechazo: string | null;
  cuotas: (Letra & { pagado: number; diasAtraso: number; mora: number })[];
  cuotasPagadas: number[];
  letrasParaPrecancelar: number;
  precancelable: boolean;
  precanceladoEn: string | null;
  creadoEn: string;
  finalizadoEn: string | null;
  origenEncaje: string | null;
  tasaMora: number;
  moraPendiente: number;
  letrasAtrasadas: number;
}

export interface SolicitudCupo {
  id: number;
  titular: string;
  importe: number;
  promocion: string | null;
  estado: string;
  motivo: string | null;
  creadoEn: string;
}

export interface DocumentoCredito {
  id: number;
  nombre: string;
  tamano: number;
  creadoEn: string;
}

export interface ProductosAhorro {
  tasaNormal: number;
  plazos: { meses: number; tasaAnual: number }[];
}

export interface ProductosCredito {
  perfil: string;
  letras: { modalidad: string; tasaInteres: number; porcentajeEncaje: number }[];
  tasaDesgravamen: number;
  importeMinimo: number;
  importeMaximo: number;
  plazoMinimo: number;
  plazoMaximo: number;
  emergenteLimite: number;
  cupoUtilizado: number;
  cupoDisponible: number;
  emergenteInteres: number;
  emergenteDesgravamen: number;
  emergenteUnidad: string;
  promocionAbierta: string | null;
  promocionLimite: number;
  promocionTasa: number;
}

export interface Simulacion {
  importe: number;
  plazoMeses: number;
  tasaInteres: number;
  tasaDesgravamen: number;
  encaje: number;
  cuotas: Letra[];
}

export interface Ahorro {
  id: number;
  tipo: string;
  nombre: string;
  estado: string;
  saldo: number;
  tasaAnual: number;
  plazoMeses: number | null;
  venceEn: string | null;
  interesAlVencimiento: number | null;
  retirable: boolean;
  interesAcumulado: number;
  interesDisponible: number;
  proximaLiberacion: string | null;
}

export interface EventoAuditoria {
  id: number;
  actorId: number | null;
  accion: string;
  modulo: string;
  entidad: string | null;
  entidadId: string | null;
  antes: string | null;
  despues: string | null;
  motivo: string | null;
  fecha: string;
}

export interface EstadoCajaRespuesta {
  caja: {
    saldos: { codigo: string; saldo: number }[];
    meses: { periodo: string; entradas: number; salidas: number }[];
    resultados: { periodo: string; ganancias: number; perdidas: number }[];
    entradas: { concepto: string; importe: number }[];
    salidas: { concepto: string; importe: number }[];
    duenos: DuenoDinero[];
  };
  enEspera: ResultadosEnEspera;
}

export interface DuenoDinero {
  nombre: string;
  apertura: number;
  cuenta: number;
  ahorrosNormales: number;
  plazosFijos: number;
  encajes: number;
  ahorroLetras: number;
}

export interface ResultadosEnEspera {
  ganancia: number;
  perdida: number;
}

export interface Tesoreria {
  saldo: number;
  moneda: string;
  movimientos: Pagina<{ id: number; sentido: string; importe: number; concepto: string; fecha: string }>;
}

export interface ResumenSocio {
  socioId: number;
  nombre: string;
  desde: string;
  totalInicial: number;
  totalObligatorio: number;
  totalPersonal: number;
  compraventas: number;
  ganancias: number;
  gananciasDisponibles: number;
  obligacionesPendientes: number;
  retiroAutorizado: boolean;
  totalCajaChica: number;
  valorActual: number;
  porcentaje: number;
  aporteObligatorio: number;
  aporteCajaChica: number;
  periodoActual: string;
  obligatorioDelMesPagado: boolean;
  cajaChicaDelMesPagado: boolean;
  rendimientoMes: number | null;
  variacionMes: number;
}

export interface Obligacion {
  tipo: string;
  periodo: string | null;
  cuotaId: number | null;
  descripcion: string;
  importe: number;
}

export interface MenuObligaciones {
  socioId: number;
  nombre: string;
  gananciasDisponibles: number;
  retiroAutorizado: boolean;
  obligaciones: Obligacion[];
  total: number;
}

export interface Aporte {
  id: number;
  tipo: string;
  importe: number;
  periodo: string;
  fecha: string;
}

export interface PeriodoParticipacion {
  periodo: string;
  valorInicial: number;
  variaciones: { tipo: string; importe: number; fecha: string }[];
  valorFinal: number;
  rendimiento: number | null;
}

export interface Resultado {
  id: number;
  tipo: string;
  importe: number;
  reserva: number;
  concepto: string;
  fecha: string;
}

export interface CierreAnual {
  anio: number;
  resultadoNeto: number;
  porcentajeReserva: number;
  reserva: number;
  distribuido: number;
  fecha: string;
}

export interface CajaChica {
  saldo: number;
  moneda: string;
  movimientos: Pagina<{ id: number; sentido: string; importe: number; concepto: string; detalle: string | null; fecha: string }>;
}

export interface CargoVista {
  codigo: string;
  nombre: string;
  permisos: string[];
}

export interface PermisoVista {
  codigo: string;
  descripcion: string;
}

export interface DefinicionInforme {
  codigo: string;
  titulo: string;
  descripcion: string;
  usaFechas: boolean;
}

export interface TablaInforme {
  columnas: string[];
  numericas: boolean[];
  filas: (string | null)[][];
  truncado: boolean;
}

export interface DatosBanco {
  banco: string;
  tipoCuenta: string;
  numeroCuenta: string;
  titularCuenta: string;
}

export interface SolicitudRetiro {
  id: number;
  numeroCuenta: string;
  titular: string;
  importe: number;
  medio: string;
  banco: DatosBanco | null;
  estado: string;
  motivo: string | null;
  referencia: string | null;
  fecha: string;
  resueltaEn: string | null;
}

export interface SolicitudDeposito {
  id: number;
  numeroCuenta: string;
  titular: string;
  importe: number;
  medio: string;
  referencia: string | null;
  estado: string;
  motivo: string | null;
  fecha: string;
  resueltaEn: string | null;
}

export interface ResumenGeneral {
  cajaPrincipal: number;
  fondoReserva: number;
  personasConCuenta: number;
  creditosActivos: number;
  capitalPrestado: number;
  saldosEnCuentas: number;
  ahorros: number;
}

export interface PersonaFinanciera {
  id: number;
  nombre: string;
  cuenta: string;
  saldoCuenta: number;
  ahorros: number;
  creditosActivos: number;
  capitalPendiente: number;
  cuotasVencidas: number;
  socio: boolean;
}

export interface CreditoDetalle {
  id: number;
  familia: string;
  modalidad: string | null;
  estado: string;
  importe: number;
  capitalPendiente: number;
  solicitadoEn: string;
  finalizadoEn: string | null;
  motivoRechazo: string | null;
  cuotas: { numero: number; vencimiento: string; total: number; pagado: number; pagadaEn: string | null }[];
}

export interface MovimientoGeneral {
  id: number;
  fecha: string;
  persona: string;
  concepto: string;
  sentido: string;
  importe: number;
}

export interface DetalleFinanciero {
  persona: PersonaFinanciera;
  creditos: CreditoDetalle[];
  ahorros: { id: number; nombre: string; tipo: string; estado: string; saldo: number }[];
  movimientos: MovimientoGeneral[];
}

export interface PersonaContacto {
  id: number;
  nombres: string;
  apellidos: string;
  identificacion: string;
  telefono: string;
  direccion: string;
  correo: string;
  estadoCuenta: string | null;
  socio: boolean;
  registradaEn: string;
}

export interface HistorialCrediticio {
  id: number;
  nombre: string;
  total: number;
  activos: number;
  finalizados: number;
  rechazados: number;
  capitalPendiente: number;
  cuotasVencidas: number;
  cuotasPagadasConRetraso: number;
}

export interface LineaValor {
  codigo: string;
  nombre: string;
  valor: number;
  detalle: boolean;
}

export interface SeccionContable {
  titulo: string;
  lineas: LineaValor[];
  total: number;
}

export interface EstadoContable {
  periodo: string;
  ganancias: SeccionContable;
  gastos: SeccionContable;
  resultadoMes: number;
  activoCorriente: SeccionContable;
  activosFijos: SeccionContable;
  pasivos: SeccionContable;
  capital: SeccionContable;
  totalActivos: number;
  totalPasivoCapital: number;
  diferencia: number;
  indicadores: { nombre: string; valor: number | null; explicacion: string }[];
}

export interface MesResultado {
  periodo: string;
  ganancias: number;
  gastos: number;
  resultado: number;
}

export interface FilaLibro {
  fecha: string;
  operacionId: number | null;
  linea: string;
  nombre: string;
  concepto: string;
  debe: number;
  haber: number;
  saldo: number | null;
}

export interface LibroDiario {
  totalDebe: number;
  totalHaber: number;
  movimientos: Pagina<FilaLibro>;
}

export interface LibroMayor {
  linea: string;
  nombre: string;
  saldoInicial: number;
  saldoFinal: number;
  movimientos: Pagina<FilaLibro>;
}

export interface LineaManual {
  codigo: string;
  nombre: string;
  admiteDisminuir: boolean;
}

export interface AsientoManual {
  id: number;
  linea: string;
  nombre: string;
  importe: number;
  descripcion: string;
  reversoDe: number | null;
  revertido: boolean;
  fecha: string;
}

function consulta(valores: Record<string, string | number | null | undefined>): HttpParams {
  let params = new HttpParams();
  for (const [clave, valor] of Object.entries(valores)) {
    if (valor !== null && valor !== undefined && valor !== '') {
      params = params.set(clave, valor);
    }
  }
  return params;
}

function fechas(desde: string | null, hasta: string | null): HttpParams {
  let params = new HttpParams();
  if (desde) {
    params = params.set('desde', desde);
  }
  return hasta ? params.set('hasta', hasta) : params;
}

function conClave(clave: string) {
  return { headers: { 'Idempotency-Key': clave } };
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  miPerfil(): Observable<Perfil> {
    return this.http.get<Perfil>(`${this.base}/personas/me`);
  }

  completarPerfil(perfil: Pick<Perfil, 'nombres' | 'apellidos' | 'identificacion' | 'telefono'>): Observable<Perfil> {
    return this.http.post<Perfil>(`${this.base}/personas/perfil`, perfil);
  }

  actualizarContacto(contacto: { telefono: string; direccion: string }): Observable<Perfil> {
    return this.http.put<Perfil>(`${this.base}/personas/me`, contacto);
  }

  actualizarDatosCredito(datos: DatosCredito): Observable<Perfil> {
    return this.http.put<Perfil>(`${this.base}/personas/me/datos-credito`, datos);
  }

  solicitante(creditoId: number): Observable<Solicitante> {
    return this.http.get<Solicitante>(`${this.base}/admin/creditos/${creditoId}/solicitante`);
  }

  precancelarCredito(id: number, clave: string): Observable<unknown> {
    return this.http.post(`${this.base}/creditos/${id}/precancelacion`, null, conClave(clave));
  }

  miCuenta(): Observable<Cuenta> {
    return this.http.get<Cuenta>(`${this.base}/cuentas-principales/me`);
  }

  solicitarApertura(metodoPago: string, referenciaPago: string | null): Observable<Cuenta> {
    return this.http.post<Cuenta>(`${this.base}/cuentas-principales/apertura`, { metodoPago, referenciaPago });
  }

  solicitarCierre(): Observable<void> {
    return this.http.post<void>(`${this.base}/cuentas-principales/me/cierre`, null);
  }

  misMovimientos(pagina: number): Observable<Pagina<MovimientoCuenta>> {
    return this.http.get<Pagina<MovimientoCuenta>>(`${this.base}/cuentas-principales/me/movimientos`, {
      params: new HttpParams().set('pagina', pagina),
    });
  }

  cuentas(estado: string | null, pagina: number): Observable<Pagina<Cuenta>> {
    let params = new HttpParams().set('pagina', pagina);
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<Pagina<Cuenta>>(`${this.base}/admin/cuentas`, { params });
  }

  confirmarApertura(id: number): Observable<Cuenta> {
    return this.http.post<Cuenta>(`${this.base}/admin/cuentas/${id}/confirmar-apertura`, null);
  }

  rechazarApertura(id: number, motivo: string): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/cuentas/${id}/rechazar-apertura`, { motivo });
  }

  confirmarCierre(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/cuentas/${id}/confirmar-cierre`, null);
  }

  /** La clave la genera quien llama por cada intento del usuario, para que un reintento no duplique. */
  movimiento(id: number, tipo: 'depositos' | 'retiros', importe: number, metodoPago: string,
      referencia: string | null, clave: string): Observable<{ operacionId: number }> {
    return this.http.post<{ operacionId: number }>(`${this.base}/admin/cuentas/${id}/${tipo}`,
      { importe, metodoPago, referencia }, { headers: { 'Idempotency-Key': clave } });
  }

  parametros(): Observable<Parametro[]> {
    return this.http.get<Parametro[]>(`${this.base}/admin/parametros`);
  }

  cambiarParametro(id: number, valor: number, vigenteDesde: string | null, motivo: string): Observable<void> {
    return this.http.put<void>(`${this.base}/admin/parametros/${id}`, { valor, vigenteDesde, motivo });
  }

  historialParametro(id: number): Observable<HistorialParametro[]> {
    return this.http.get<HistorialParametro[]>(`${this.base}/admin/parametros/${id}/historial`);
  }

  usuarios(pagina: number): Observable<Pagina<UsuarioResumen>> {
    return this.http.get<Pagina<UsuarioResumen>>(`${this.base}/admin/usuarios`, {
      params: new HttpParams().set('pagina', pagina),
    });
  }

  asignarCargo(id: number, cargo: string): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/usuarios/${id}/cargos/${cargo}`, null);
  }

  revocarCargo(id: number, cargo: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/usuarios/${id}/cargos/${cargo}`);
  }

  auditoria(pagina: number): Observable<Pagina<EventoAuditoria>> {
    return this.http.get<Pagina<EventoAuditoria>>(`${this.base}/admin/auditoria`, {
      params: new HttpParams().set('pagina', pagina),
    });
  }

  tesoreria(): Observable<Tesoreria> {
    return this.http.get<Tesoreria>(`${this.base}/tesoreria`);
  }

  estadoCaja(): Observable<EstadoCajaRespuesta> {
    return this.http.get<EstadoCajaRespuesta>(`${this.base}/tesoreria/estado`);
  }

  resultadosEnEspera(): Observable<ResultadosEnEspera> {
    return this.http.get<ResultadosEnEspera>(`${this.base}/admin/resultados/en-espera`);
  }

  misAreas(): Observable<CargoVista[]> {
    return this.http.get<CargoVista[]>(`${this.base}/areas/me`);
  }

  cargos(): Observable<CargoVista[]> {
    return this.http.get<CargoVista[]>(`${this.base}/admin/cargos`);
  }

  permisos(): Observable<PermisoVista[]> {
    return this.http.get<PermisoVista[]>(`${this.base}/admin/permisos`);
  }

  concederPermiso(cargo: string, permiso: string): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/cargos/${cargo}/permisos/${permiso}`, null);
  }

  retirarPermiso(cargo: string, permiso: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/cargos/${cargo}/permisos/${permiso}`);
  }

  informes(): Observable<DefinicionInforme[]> {
    return this.http.get<DefinicionInforme[]>(`${this.base}/admin/reportes`);
  }

  informe(codigo: string, desde: string | null, hasta: string | null): Observable<TablaInforme> {
    return this.http.get<TablaInforme>(`${this.base}/admin/reportes/${codigo}`, { params: fechas(desde, hasta) });
  }

  informeCsv(codigo: string, desde: string | null, hasta: string | null): Observable<Blob> {
    return this.http.get(`${this.base}/admin/reportes/${codigo}/csv`, { params: fechas(desde, hasta), responseType: 'blob' });
  }

  miEstadoDeCuentaCsv(): Observable<Blob> {
    return this.http.get(`${this.base}/cuentas-principales/me/estado-de-cuenta.csv`, { responseType: 'blob' });
  }

  miResumenSocio(): Observable<ResumenSocio> {
    return this.http.get<ResumenSocio>(`${this.base}/socios/me`);
  }

  misAportes(): Observable<Aporte[]> {
    return this.http.get<Aporte[]>(`${this.base}/socios/me/aportes`);
  }

  miParticipacion(): Observable<PeriodoParticipacion[]> {
    return this.http.get<PeriodoParticipacion[]>(`${this.base}/socios/me/participacion`);
  }

  aportar(tipo: string, importe: number | null, clave: string): Observable<unknown> {
    return this.http.post(`${this.base}/socios/me/aportes`, { tipo, importe }, conClave(clave));
  }

  aportarCajaChica(clave: string): Observable<unknown> {
    return this.http.post(`${this.base}/socios/me/aportes-caja-chica`, {}, conClave(clave));
  }

  cajaChica(pagina: number): Observable<CajaChica> {
    return this.http.get<CajaChica>(`${this.base}/caja-chica`, { params: new HttpParams().set('pagina', pagina) });
  }

  gastoCajaChica(importe: number, detalle: string, clave: string): Observable<unknown> {
    return this.http.post(`${this.base}/caja-chica/gastos`, { importe, detalle }, conClave(clave));
  }

  socios(): Observable<ResumenSocio[]> {
    return this.http.get<ResumenSocio[]>(`${this.base}/admin/socios`);
  }

  obligaciones(socioId: number): Observable<MenuObligaciones> {
    return this.http.get<MenuObligaciones>(`${this.base}/admin/socios/${socioId}/obligaciones`);
  }

  descontarObligaciones(socioId: number, descuentos: { tipo: string; periodo: string | null; cuotaId: number | null; importe: number | null }[],
      clave: string): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/socios/${socioId}/descuentos`, { descuentos }, conClave(clave));
  }

  autorizarRetiro(socioId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/socios/${socioId}/autorizacion-retiro`, null);
  }

  revocarRetiro(socioId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/socios/${socioId}/autorizacion-retiro`);
  }

  retirarGanancias(importe: number, clave: string): Observable<unknown> {
    return this.http.post(`${this.base}/socios/me/retiro-ganancias`, { importe }, conClave(clave));
  }

  registrarCompraventa(vendedorId: number, compradorId: number, capital: number, precio: number, clave: string): Observable<unknown> {
    return this.http.post(`${this.base}/admin/socios/compraventas`, { vendedorId, compradorId, capital, precio }, conClave(clave));
  }

  registrarAporteInicial(socioId: number, importe: number, metodoPago: string, referencia: string | null,
      clave: string): Observable<unknown> {
    return this.http.post(`${this.base}/admin/socios/${socioId}/aporte-inicial`, { importe, metodoPago, referencia }, conClave(clave));
  }

  resultados(pagina: number): Observable<Pagina<Resultado>> {
    return this.http.get<Pagina<Resultado>>(`${this.base}/admin/resultados`, { params: new HttpParams().set('pagina', pagina) });
  }

  registrarResultado(tipo: string, importe: number, concepto: string, fecha: string | null): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/resultados`, { tipo, importe, concepto, fecha });
  }

  cierres(): Observable<CierreAnual[]> {
    return this.http.get<CierreAnual[]>(`${this.base}/admin/cierres`);
  }

  cerrarAnio(anio: number): Observable<CierreAnual> {
    return this.http.post<CierreAnual>(`${this.base}/admin/cierres`, { anio });
  }

  registrarSocio(usuarioId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/usuarios/${usuarioId}/socio`, null);
  }

  productosCredito(): Observable<ProductosCredito> {
    return this.http.get<ProductosCredito>(`${this.base}/creditos/productos`);
  }

  simularLetras(modalidad: string, importe: number, plazoMeses: number): Observable<Simulacion> {
    return this.http.post<Simulacion>(`${this.base}/creditos/letras/simulacion`, { modalidad, importe, plazoMeses });
  }

  solicitarLetras(modalidad: string, importe: number, plazoMeses: number, origenEncaje: string, ahorroEncajeId: number | null): Observable<{ id: number }> {
    return this.http.post<{ id: number }>(`${this.base}/creditos/letras/solicitudes`, { modalidad, importe, plazoMeses, origenEncaje, ahorroEncajeId });
  }

  misDocumentosCredito(creditoId: number): Observable<DocumentoCredito[]> {
    return this.http.get<DocumentoCredito[]>(`${this.base}/creditos/${creditoId}/documentos`);
  }

  subirDocumentosCredito(creditoId: number, archivos: File[]): Observable<DocumentoCredito[]> {
    const datos = new FormData();
    archivos.forEach((a) => datos.append('archivos', a, a.name));
    return this.http.post<DocumentoCredito[]>(`${this.base}/creditos/${creditoId}/documentos`, datos);
  }

  documentosCreditoAdmin(creditoId: number): Observable<DocumentoCredito[]> {
    return this.http.get<DocumentoCredito[]>(`${this.base}/admin/creditos/${creditoId}/documentos`);
  }

  descargarDocumentoCredito(creditoId: number, documentoId: number): Observable<Blob> {
    return this.http.get(`${this.base}/admin/creditos/${creditoId}/documentos/${documentoId}`, { responseType: 'blob' });
  }

  misSolicitudesCupo(): Observable<SolicitudCupo[]> {
    return this.http.get<SolicitudCupo[]>(`${this.base}/creditos/emergentes/disposiciones/me`);
  }

  solicitudesCupoAdmin(): Observable<SolicitudCupo[]> {
    return this.http.get<SolicitudCupo[]>(`${this.base}/admin/creditos/disposiciones`);
  }

  aprobarCupo(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/creditos/disposiciones/${id}/aprobar`, null);
  }

  rechazarCupo(id: number, motivo: string): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/creditos/disposiciones/${id}/rechazar`, { motivo });
  }

  solicitarEmergente(importe: number, clave: string): Observable<unknown> {
    return this.http.post(`${this.base}/creditos/emergentes/solicitudes`, { importe }, conClave(clave));
  }

  disponerCupo(importe: number, promocion: string | null, clave: string): Observable<unknown> {
    return this.http.post(`${this.base}/creditos/emergentes/disposiciones`, { importe, promocion }, conClave(clave));
  }

  misCreditos(): Observable<Credito[]> {
    return this.http.get<Credito[]>(`${this.base}/creditos/me`);
  }

  pagarCredito(id: number, cuerpo: { importe?: number | null; usarAhorroLetras?: boolean; usarEncaje?: boolean },
      clave: string): Observable<unknown> {
    return this.http.post(`${this.base}/creditos/${id}/pagos`, cuerpo, conClave(clave));
  }

  creditosAdmin(estado: string | null, pagina: number): Observable<Pagina<Credito>> {
    let params = new HttpParams().set('pagina', pagina);
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http.get<Pagina<Credito>>(`${this.base}/admin/creditos`, { params });
  }

  aprobarCredito(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/creditos/${id}/aprobar`, null);
  }

  rechazarCredito(id: number, motivo: string): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/creditos/${id}/rechazar`, { motivo });
  }

  misAhorros(): Observable<Ahorro[]> {
    return this.http.get<Ahorro[]>(`${this.base}/ahorros/me`);
  }

  productosAhorro(): Observable<ProductosAhorro> {
    return this.http.get<ProductosAhorro>(`${this.base}/ahorros/productos`);
  }

  retirarInteresAhorro(id: number, clave: string): Observable<unknown> {
    return this.http.post(`${this.base}/ahorros/${id}/interes`, null, conClave(clave));
  }

  crearAhorroNormal(nombre: string): Observable<Ahorro> {
    return this.http.post<Ahorro>(`${this.base}/ahorros/normales`, { nombre });
  }

  crearPlazoFijo(nombre: string, importe: number, plazoMeses: number, clave: string): Observable<Ahorro> {
    return this.http.post<Ahorro>(`${this.base}/ahorros/plazo-fijo`, { nombre, importe, plazoMeses }, conClave(clave));
  }

  depositarAhorro(id: number, importe: number, clave: string): Observable<unknown> {
    return this.http.post(`${this.base}/ahorros/${id}/depositos`, { importe }, conClave(clave));
  }

  retirarAhorro(id: number, importe: number | null, clave: string): Observable<unknown> {
    return this.http.post(`${this.base}/ahorros/${id}/retiros`, { importe }, conClave(clave));
  }

  misRetiros(): Observable<SolicitudRetiro[]> {
    return this.http.get<SolicitudRetiro[]>(`${this.base}/cuentas-principales/me/retiros`);
  }

  solicitarRetiro(cuerpo: { importe: number; medio: string } & Partial<DatosBanco>, clave: string): Observable<SolicitudRetiro> {
    return this.http.post<SolicitudRetiro>(`${this.base}/cuentas-principales/me/retiros`, cuerpo, conClave(clave));
  }

  cancelarRetiro(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/cuentas-principales/me/retiros/${id}/cancelar`, null);
  }

  solicitudesRetiro(estado: string | null, pagina: number): Observable<Pagina<SolicitudRetiro>> {
    return this.http.get<Pagina<SolicitudRetiro>>(`${this.base}/admin/solicitudes-retiro`, { params: consulta({ estado, pagina }) });
  }

  entregarRetiro(id: number, referencia: string | null): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/solicitudes-retiro/${id}/entregar`, { referencia });
  }

  rechazarRetiro(id: number, motivo: string): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/solicitudes-retiro/${id}/rechazar`, { motivo });
  }

  misDepositos(): Observable<SolicitudDeposito[]> {
    return this.http.get<SolicitudDeposito[]>(`${this.base}/cuentas-principales/me/depositos`);
  }

  solicitarDeposito(cuerpo: { importe: number; medio: string; referencia: string | null }, clave: string): Observable<SolicitudDeposito> {
    return this.http.post<SolicitudDeposito>(`${this.base}/cuentas-principales/me/depositos`, cuerpo, conClave(clave));
  }

  cancelarDeposito(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/cuentas-principales/me/depositos/${id}/cancelar`, null);
  }

  solicitudesDeposito(estado: string | null, pagina: number): Observable<Pagina<SolicitudDeposito>> {
    return this.http.get<Pagina<SolicitudDeposito>>(`${this.base}/admin/solicitudes-deposito`, { params: consulta({ estado, pagina }) });
  }

  confirmarDeposito(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/solicitudes-deposito/${id}/confirmar`, null);
  }

  rechazarDeposito(id: number, motivo: string): Observable<void> {
    return this.http.post<void>(`${this.base}/admin/solicitudes-deposito/${id}/rechazar`, { motivo });
  }

  resumenGeneral(): Observable<ResumenGeneral> {
    return this.http.get<ResumenGeneral>(`${this.base}/socios/general/resumen`);
  }

  personasGeneral(buscar: string, pagina: number): Observable<Pagina<PersonaFinanciera>> {
    return this.http.get<Pagina<PersonaFinanciera>>(`${this.base}/socios/general/personas`, { params: consulta({ buscar, pagina }) });
  }

  detalleGeneral(id: number): Observable<DetalleFinanciero> {
    return this.http.get<DetalleFinanciero>(`${this.base}/socios/general/personas/${id}`);
  }

  movimientosGeneral(pagina: number): Observable<Pagina<MovimientoGeneral>> {
    return this.http.get<Pagina<MovimientoGeneral>>(`${this.base}/socios/general/movimientos`, { params: consulta({ pagina }) });
  }

  personas(buscar: string, pagina: number): Observable<Pagina<PersonaContacto>> {
    return this.http.get<Pagina<PersonaContacto>>(`${this.base}/admin/personas`, { params: consulta({ buscar, pagina }) });
  }

  historiales(buscar: string, pagina: number): Observable<Pagina<HistorialCrediticio>> {
    return this.http.get<Pagina<HistorialCrediticio>>(`${this.base}/admin/historial-crediticio`, { params: consulta({ buscar, pagina }) });
  }

  historialDe(personaId: number): Observable<CreditoDetalle[]> {
    return this.http.get<CreditoDetalle[]>(`${this.base}/admin/historial-crediticio/${personaId}`);
  }

  estadoContable(periodo: string | null): Observable<EstadoContable> {
    return this.http.get<EstadoContable>(`${this.base}/admin/contabilidad/estado`, { params: consulta({ periodo }) });
  }

  evolucionContable(): Observable<MesResultado[]> {
    return this.http.get<MesResultado[]>(`${this.base}/admin/contabilidad/evolucion`);
  }

  libroDiario(periodo: string | null, pagina: number): Observable<LibroDiario> {
    return this.http.get<LibroDiario>(`${this.base}/admin/contabilidad/diario`, { params: consulta({ periodo, pagina }) });
  }

  libroMayor(linea: string, periodo: string | null, pagina: number): Observable<LibroMayor> {
    return this.http.get<LibroMayor>(`${this.base}/admin/contabilidad/mayor`, { params: consulta({ linea, periodo, pagina }) });
  }

  lineasManuales(): Observable<LineaManual[]> {
    return this.http.get<LineaManual[]>(`${this.base}/admin/contabilidad/lineas-manuales`);
  }

  asientos(pagina: number): Observable<Pagina<AsientoManual>> {
    return this.http.get<Pagina<AsientoManual>>(`${this.base}/admin/contabilidad/asientos`, { params: consulta({ pagina }) });
  }

  registrarAsiento(cuerpo: { linea: string; disminuye: boolean; importe: number; descripcion: string }, clave: string): Observable<unknown> {
    return this.http.post(`${this.base}/admin/contabilidad/asientos`, cuerpo, conClave(clave));
  }

  revertirAsiento(id: number, motivo: string): Observable<unknown> {
    return this.http.post(`${this.base}/admin/contabilidad/asientos/${id}/reverso`, { motivo });
  }

  estadoSimulacion(): Observable<EstadoSimulacion> {
    return this.http.get<EstadoSimulacion>(`${this.base}/simulacion`);
  }

  avanzarSimulacion(destino: { dias?: number; fecha?: string }): Observable<EstadoSimulacion> {
    return this.http.post<EstadoSimulacion>(`${this.base}/simulacion/avanzar`, destino);
  }
}
