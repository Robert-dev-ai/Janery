import { DestroyRef, effect, inject, Injectable, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter, firstValueFrom, Subject } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth';

const ESPERA_REINTENTO_MS = 3000;
const AGRUPAR_AVISOS_MS = 250;

/** Lo ocurrido sin conexión no se avisó: tras reconectar, todo se da por cambiado. */
export const TODO = '*';

/**
 * Módulos cuyos cambios pueden alterar los datos de la persona conectada (cargos y permisos, cuenta, socio, nombre). Solo
 * con ellos se vuelve a pedir /auth/me: con cualquier otro sería una consulta por persona conectada y por operación.
 */
const MODULOS_DEL_USUARIO = ['USUARIO', 'PERSONA', 'CUENTA', 'SOCIO', 'SIMULACION'];

/**
 * Mantiene abierta una conexión con el backend, que avisa cuando algo cambia y en qué módulo. El aviso no trae datos:
 * cada pantalla vuelve a pedir los suyos. Se usa fetch y no EventSource porque este no permite enviar el token.
 */
@Injectable({ providedIn: 'root' })
export class TiempoRealService {
  private readonly auth = inject(AuthService);
  private readonly avisos = new Subject<ReadonlySet<string>>();
  private conexion: AbortController | null = null;
  private temporizador: ReturnType<typeof setTimeout> | null = null;
  private pendientes = new Set<string>();

  /** Los módulos que cambiaron en cada tanda de avisos; TODO si no se sabe. */
  readonly cambios = this.avisos.asObservable();
  readonly conectado = signal(false);

  constructor() {
    effect(() => {
      const autenticado = this.auth.autenticado();
      untracked(() => (autenticado ? this.abrir() : this.cerrar()));
    });
  }

  private abrir(): void {
    if (!this.conexion) {
      this.conexion = new AbortController();
      void this.mantenerConexion(this.conexion.signal);
    }
  }

  private cerrar(): void {
    this.conexion?.abort();
    this.conexion = null;
    this.conectado.set(false);
  }

  private async mantenerConexion(cancelada: AbortSignal): Promise<void> {
    let primera = true;
    while (!cancelada.aborted) {
      try {
        const respuesta = await fetch(`${environment.apiBaseUrl}/eventos`, {
          headers: { Authorization: `Bearer ${this.auth.token()}` },
          signal: cancelada,
        });
        if (respuesta.status === 401) {
          if (!(await firstValueFrom(this.auth.renovar()))) {
            return;
          }
          continue;
        }
        if (respuesta.ok && respuesta.body) {
          this.conectado.set(true);
          // Lo ocurrido mientras no hubo conexión no se avisó.
          if (!primera) {
            this.avisar(TODO);
          }
          primera = false;
          await this.leer(respuesta.body);
        }
      } catch {
        // Servidor caído o conexión cortada: se reintenta abajo.
      }
      this.conectado.set(false);
      if (!cancelada.aborted) {
        await new Promise((listo) => setTimeout(listo, ESPERA_REINTENTO_MS));
      }
    }
  }

  /**
   * La red entrega el texto en trozos que no coinciden con los eventos: uno puede llegar partido en dos. Se acumula y solo
   * se procesa cada evento completo, que termina en una línea vacía.
   */
  private async leer(cuerpo: ReadableStream<Uint8Array>): Promise<void> {
    const lector = cuerpo.getReader();
    const texto = new TextDecoder();
    let resto = '';
    for (;;) {
      const { done, value } = await lector.read();
      if (done) {
        return;
      }
      resto += texto.decode(value, { stream: true }).replace(/\r\n?/g, '\n');
      let fin: number;
      while ((fin = resto.indexOf('\n\n')) >= 0) {
        const modulo = moduloDe(resto.slice(0, fin));
        resto = resto.slice(fin + 2);
        if (modulo !== null) {
          this.avisar(modulo);
        }
      }
    }
  }

  /** Una operación genera varios avisos seguidos: se agrupan para no repetir las consultas. */
  private avisar(modulo: string): void {
    this.pendientes.add(modulo);
    if (this.temporizador) {
      clearTimeout(this.temporizador);
    }
    this.temporizador = setTimeout(() => {
      const modulos = this.pendientes;
      this.pendientes = new Set<string>();
      this.temporizador = null;
      if (afecta(modulos, MODULOS_DEL_USUARIO)) {
        this.auth.cargarYo().subscribe({ error: () => undefined });
      }
      this.avisos.next(modulos);
    }, AGRUPAR_AVISOS_MS);
  }
}

/** Módulo de un evento «cambio»; null si es otro (comentarios de latido, conexión). Sin módulo, TODO. */
function moduloDe(evento: string): string | null {
  let nombre = 'message';
  let datos = '';
  for (const linea of evento.split('\n')) {
    if (linea.startsWith('event:')) {
      nombre = linea.slice(6).trim();
    } else if (linea.startsWith('data:')) {
      datos += linea.slice(5).trim();
    }
  }
  return nombre === 'cambio' ? datos || TODO : null;
}

function afecta(cambiados: ReadonlySet<string>, modulos: readonly string[]): boolean {
  return cambiados.has(TODO) || modulos.some((m) => cambiados.has(m));
}

/**
 * Ejecuta la función cada vez que algo cambia en el backend, mientras exista el componente. Llamar en el constructor.
 * Con módulos, solo cuando cambia alguno de ellos: sin módulos, con cualquier cambio.
 */
export function alCambiar(accion: () => void, modulos?: readonly string[]): void {
  inject(TiempoRealService).cambios.pipe(
    filter((cambiados) => !modulos || afecta(cambiados, modulos)),
    takeUntilDestroyed(inject(DestroyRef)),
  ).subscribe(accion);
}
