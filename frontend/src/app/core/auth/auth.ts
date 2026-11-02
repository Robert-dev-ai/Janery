import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, firstValueFrom, map, Observable, of, shareReplay, switchMap, tap } from 'rxjs';

import { environment } from '../../../environments/environment';

export interface Yo {
  id: number;
  correo: string;
  cargos: string[];
  permisos: string[];
  tienePerfil: boolean;
  estadoCuenta: string | null;
  esSocio: boolean;
}

interface TokenResponse {
  accessToken: string;
  expiraEn: string;
}

/** El access token vive solo en memoria; el de refresco, en una cookie HttpOnly que el script no puede leer. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiBaseUrl}/auth`;
  private renovando: Observable<string | null> | null = null;

  readonly token = signal<string | null>(null);
  readonly yo = signal<Yo | null>(null);
  readonly autenticado = computed(() => this.yo() !== null);

  tienePermiso(permiso: string): boolean {
    return this.yo()?.permisos.includes(permiso) ?? false;
  }

  /** Ruta donde debe continuar la persona según su avance en el alta. */
  rutaInicial(): string {
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

  /** Comparte una sola renovación entre peticiones simultáneas: el token de refresco rota en cada uso. */
  renovar(): Observable<string | null> {
    this.renovando ??= this.http
      .post<TokenResponse>(`${this.api}/refresh`, null, { withCredentials: true })
      .pipe(
        map((r) => r.accessToken),
        tap((token) => {
          this.token.set(token);
          this.renovando = null;
        }),
        catchError(() => {
          this.limpiar();
          this.renovando = null;
          return of(null);
        }),
        shareReplay(1),
      );
    return this.renovando;
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
    return this.http.post<void>(`${this.api}/contrasena`, { actual, nueva }, { withCredentials: true });
  }

  cerrarOtrasSesiones(): Observable<void> {
    return this.http.post<void>(`${this.api}/sesiones/cerrar-otras`, null, { withCredentials: true });
  }

  private limpiar(): void {
    this.token.set(null);
    this.yo.set(null);
  }
}
