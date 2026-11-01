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

export interface Perfil {
  nombres: string;
  apellidos: string;
  identificacion: string;
  telefono: string;
  direccion: string;
}

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
  cuotas: (Letra & { pagado: number })[];
  cuotasPagadas: number[];
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

  completarPerfil(perfil: Perfil): Observable<Perfil> {
    return this.http.post<Perfil>(`${this.base}/personas/perfil`, perfil);
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

  solicitarLetras(modalidad: string, importe: number, plazoMeses: number): Observable<{ id: number }> {
    return this.http.post<{ id: number }>(`${this.base}/creditos/letras/solicitudes`, { modalidad, importe, plazoMeses });
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

  estadoSimulacion(): Observable<EstadoSimulacion> {
    return this.http.get<EstadoSimulacion>(`${this.base}/simulacion`);
  }

  avanzarSimulacion(destino: { dias?: number; fecha?: string }): Observable<EstadoSimulacion> {
    return this.http.post<EstadoSimulacion>(`${this.base}/simulacion/avanzar`, destino);
  }
}
