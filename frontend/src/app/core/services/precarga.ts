import { inject, Injectable } from '@angular/core';
import { PreloadingStrategy, Route } from '@angular/router';
import { Observable, of } from 'rxjs';

import { AuthService } from '../auth/auth';

/**
 * Tras abrir la primera pantalla, descarga las demás en segundo plano para que navegar no espere la red. Un visitante
 * solo necesita las públicas (data.publica): las del panel y la gestión se descargan cuando inicia sesión, porque el
 * router vuelve a precargar tras cada navegación.
 */
@Injectable({ providedIn: 'root' })
export class PrecargaConSesion implements PreloadingStrategy {
  private readonly auth = inject(AuthService);

  preload(ruta: Route, cargar: () => Observable<unknown>): Observable<unknown> {
    return this.auth.autenticado() || ruta.data?.['publica'] ? cargar() : of(null);
  }
}
