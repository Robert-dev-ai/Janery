import { inject, Injectable } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

import { AuthService } from '../auth/auth';
import { ApiService } from './api';

/**
 * Anota qué pantalla abre cada persona, para saber qué áreas mejorar primero. Solo la ruta, sin parámetros: los números
 * de registro se reemplazan por ":id". Si falla, no interrumpe nada.
 */
@Injectable({ providedIn: 'root' })
export class UsoService {
  private readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private ultima = '';

  constructor() {
    inject(Router).events.pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd)).subscribe((e) => this.anotar(e.urlAfterRedirects));
  }

  private anotar(url: string): void {
    if (!this.auth.autenticado()) {
      return;
    }
    const ruta = rutaDe(url);
    if (ruta === this.ultima) {
      return;
    }
    this.ultima = ruta;
    this.api.registrarVisita(ruta).subscribe({ error: () => undefined });
  }
}

/** "/admin/reportes?informe=ACTIVIDAD" → "/admin/reportes/actividad"; "/x/12" → "/x/:id". */
export function rutaDe(url: string): string {
  const [camino, consulta] = url.split('#')[0].split('?');
  const informe = new URLSearchParams(consulta ?? '').get('informe');
  const limpio = camino.split('/').map((s) => (/^\d+$/.test(s) ? ':id' : s.toLowerCase())).join('/') || '/';
  const conInforme = informe ? `${limpio}/${informe.toLowerCase()}` : limpio;
  return conInforme.replace(/[^a-z0-9/:_-]/g, '').slice(0, 120) || '/';
}
