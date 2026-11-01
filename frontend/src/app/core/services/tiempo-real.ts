import { DestroyRef, effect, inject, Injectable, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom, Subject } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth';

const ESPERA_REINTENTO_MS = 3000;
const AGRUPAR_AVISOS_MS = 250;

/**
 * Mantiene abierta una conexión con el backend, que avisa cuando algo cambia. El aviso no trae datos: cada pantalla
 * vuelve a pedir los suyos. Se usa fetch y no EventSource porque este no permite enviar el token.
 */
@Injectable({ providedIn: 'root' })
export class TiempoRealService {
  private readonly auth = inject(AuthService);
  private readonly avisos = new Subject<void>();
  private conexion: AbortController | null = null;
  private temporizador: ReturnType<typeof setTimeout> | null = null;

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
            this.avisar();
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

  private async leer(cuerpo: ReadableStream<Uint8Array>): Promise<void> {
    const lector = cuerpo.getReader();
    const texto = new TextDecoder();
    for (;;) {
      const { done, value } = await lector.read();
      if (done) {
        return;
      }
      if (texto.decode(value, { stream: true }).includes('event:cambio')) {
        this.avisar();
      }
    }
  }

  /** Una operación genera varios avisos seguidos: se agrupan para no repetir las consultas. */
  private avisar(): void {
    if (this.temporizador) {
      clearTimeout(this.temporizador);
    }
    this.temporizador = setTimeout(() => {
      this.temporizador = null;
      this.auth.cargarYo().subscribe({ error: () => undefined });
      this.avisos.next();
    }, AGRUPAR_AVISOS_MS);
  }
}

/** Ejecuta la función cada vez que algo cambia en el backend, mientras exista el componente. Llamar en el constructor. */
export function alCambiar(accion: () => void): void {
  inject(TiempoRealService).cambios.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe(accion);
}
