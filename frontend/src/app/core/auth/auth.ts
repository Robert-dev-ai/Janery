import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, firstValueFrom, map, Observable, of, shareReplay, switchMap, tap, timer, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';

export interface Yo {
  id: number;
  correo: string;
  cargos: string[];
  permisos: string[];
  tienePerfil: boolean;
  estadoCuenta: string | null;
  esSocio: boolean;
  nombre: string | null;
  tieneFoto: boolean;
  debeCambiarContrasena: boolean;
}

interface TokenResponse {
  accessToken: string;
  expiraEn: string;
}

/** Otra pestaña pudo usar el mismo token de refresco al mismo tiempo: pasado este tiempo, la cookie ya trae el nuevo. */
const ESPERA_OTRA_PESTANA_MS = 300;

/** Había cookie pero el token ya no sirve: puede haberlo usado otra pestaña. Sin cookie (SIN_SESION) no hay qué reintentar. */
function sesionYaUsada(error: unknown): boolean {
  return error instanceof HttpErrorResponse && (error.error as { codigo?: string } | null)?.codigo === 'SESION_EXPIRADA';
}

/** Lo que ve en Iniciar sesión quien perdió la sesión mientras usaba la aplicación. */
export const AVISO_SESION_TERMINADA = 'Su sesión terminó. Inicie sesión de nuevo para continuar.';

/** El access token vive solo en memoria; el de refresco, en una cookie HttpOnly que el script no puede leer. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly api = `${environment.apiBaseUrl}/auth`;
  private renovando: Observable<string | null> | null = null;

  readonly token = signal<string | null>(null);
  readonly yo = signal<Yo | null>(null);
  readonly autenticado = computed(() => this.yo() !== null);
  /** Sube al cambiar la foto, para que se vuelva a descargar. */
  readonly versionFoto = signal(0);

  tienePermiso(permiso: string): boolean {
    return this.yo()?.permisos.includes(permiso) ?? false;
  }

  /** Ruta donde debe continuar la persona según su avance en el alta. */
  rutaInicial(): string {
    if (this.yo()?.debeCambiarContrasena) {
      return '/configuracion/contrasena';
    }
    return this.yo()?.permisos.length ? '/admin/panel' : this.rutaPortal();
  }

  rutaPortal(): string {
    return this.yo()?.estadoCuenta === 'ACTIVA' ? '/app/inicio' : '/activar-cuenta';
  }

  /** Entrada a la gestión: un cargo sin permisos solo tiene sus áreas. Null si la persona no forma parte del personal. */
  rutaPanel(): string | null {
    if (this.yo()?.permisos.length) {
      return '/admin/panel';
    }
    return this.yo()?.cargos.length ? '/areas' : null;
  }

  registrar(datos: { correo: string; contrasena: string; nombres: string; apellidos: string; identificacion: string; telefono: string }): Observable<void> {
    return this.http.post<void>(`${this.api}/registro`, datos);
  }

  /** El primer dato puede ser el correo o la cédula: el backend distingue cuál es. */
  iniciarSesion(correo: string, contrasena: string): Observable<Yo> {
    return this.http.post<TokenResponse>(`${this.api}/login`, { correo, contrasena }, { withCredentials: true }).pipe(
      tap((r) => this.token.set(r.accessToken)),
      switchMap(() => this.cargarYo()),
    );
  }

  cargarYo(): Observable<Yo> {
    return this.http.get<Yo>(`${this.api}/me`).pipe(tap((yo) => this.yo.set(yo)));
  }

  /**
   * Comparte una sola renovación entre peticiones simultáneas: el token de refresco rota en cada uso. Si falla, reintenta
   * una vez: otra pestaña pudo usarlo en el mismo instante y la cookie ya trae el nuevo.
   */
  renovar(): Observable<string | null> {
    this.renovando ??= this.pedirRenovacion().pipe(
      catchError((e: unknown) => (sesionYaUsada(e)
        ? timer(ESPERA_OTRA_PESTANA_MS).pipe(switchMap(() => this.pedirRenovacion()))
        : throwError(() => e))),
      map((r) => r.accessToken),
      tap((token) => {
        this.token.set(token);
        this.renovando = null;
      }),
      catchError(() => {
        this.renovando = null;
        this.sesionPerdida();
        return of(null);
      }),
      shareReplay(1),
    );
    return this.renovando;
  }

  private pedirRenovacion(): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${this.api}/refresh`, null, { withCredentials: true });
  }

  /** Si había una sesión abierta, lleva a Iniciar sesión: sin esto la pantalla quedaba con el menú vacío y errores. */
  private sesionPerdida(): void {
    const habia = this.yo() !== null;
    this.limpiar();
    if (habia) {
      void this.router.navigateByUrl('/iniciar-sesion', { state: { aviso: AVISO_SESION_TERMINADA } });
    }
  }

  /** Al abrir o recargar la página intenta recuperar la sesión. */
  restaurarSesion(): Promise<void> {
    return firstValueFrom(
      this.renovar().pipe(
        switchMap((token) => (token ? this.cargarYo().pipe(map(() => undefined)) : of(undefined))),
        catchError(() => of(undefined)),
      ),
    );
  }

  cerrarSesion(): Observable<void> {
    return this.http.post<void>(`${this.api}/logout`, null, { withCredentials: true }).pipe(
      catchError(() => of(undefined)),
      tap(() => this.limpiar()),
    );
  }

  /** Con la cookie, el servidor sabe qué sesión conservar al cerrar las demás. */
  cambiarContrasena(actual: string, nueva: string): Observable<void> {
    // Cambiar una contraseña temporal levanta la obligación: se renueva el token, que llevaba la marca, y se recarga el usuario.
    return this.http.post<void>(`${this.api}/contrasena`, { actual, nueva }, { withCredentials: true }).pipe(
      switchMap(() => (this.yo()?.debeCambiarContrasena ? this.renovar() : of(null))),
      switchMap(() => this.cargarYo().pipe(map(() => undefined))),
    );
  }

  /** Pide la contraseña porque con el correo se inicia sesión. */
  cambiarCorreo(correo: string, contrasena: string): Observable<void> {
    return this.http.put<void>(`${this.api}/correo`, { correo, contrasena }).pipe(
      switchMap(() => this.cargarYo().pipe(map(() => undefined))),
    );
  }

  /** Sin correo saliente: la petición le llega a la presidencia, que entrega una contraseña temporal. */
  pedirRestablecimiento(identificador: string): Observable<void> {
    return this.http.post<void>(`${this.api}/recuperar`, { identificador });
  }

  foto(): Observable<Blob> {
    return this.http.get(`${this.api}/foto`, { responseType: 'blob' });
  }

  guardarFoto(archivo: File): Observable<void> {
    const datos = new FormData();
    datos.append('archivo', archivo, archivo.name);
    return this.http.put<void>(`${this.api}/foto`, datos).pipe(
      tap(() => this.versionFoto.update((v) => v + 1)),
      switchMap(() => this.cargarYo().pipe(map(() => undefined))),
    );
  }

  quitarFoto(): Observable<void> {
    return this.http.delete<void>(`${this.api}/foto`).pipe(
      tap(() => this.versionFoto.update((v) => v + 1)),
      switchMap(() => this.cargarYo().pipe(map(() => undefined))),
    );
  }

  cerrarOtrasSesiones(): Observable<void> {
    return this.http.post<void>(`${this.api}/sesiones/cerrar-otras`, null, { withCredentials: true });
  }

  private limpiar(): void {
    this.token.set(null);
    this.yo.set(null);
  }
}
