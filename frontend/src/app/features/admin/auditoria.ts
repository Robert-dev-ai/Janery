import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';

import { ApiService, EventoAuditoria, Pagina } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { Paginador } from '../../shared/components/paginador';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-admin-auditoria',
  imports: [DatePipe, Paginador],
  template: `
    <h1>Auditoría</h1>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (datos(); as d) {
      <div class="tabla">
        <table>
          <thead><tr><th>Fecha</th><th>Quién</th><th>Qué hizo</th><th>A quién</th><th>Código</th><th>Detalle</th><th>Motivo</th></tr></thead>
          <tbody>
            @for (e of d.contenido; track e.id) {
              <tr>
                <td>{{ e.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                <td>{{ e.quien ?? 'Sistema' }}@if (e.cargos) { <br /><small class="suave">{{ e.cargos }}</small> }</td>
                <td>{{ e.descripcion }}</td>
                <td>{{ e.afectado || '—' }}</td>
                <td class="codigo">{{ e.codigo ?? '' }}</td>
                <td>{{ detalle(e) }}</td>
                <td>{{ e.motivo }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-paginador [pagina]="d.pagina" [tamano]="d.tamano" [total]="d.total" (cambiar)="cargar($event)" />
    }
  `,
})
export class AdminAuditoria implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly datos = signal<Pagina<EventoAuditoria> | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    alCambiar(() => this.cargar(this.datos()?.pagina ?? 0));
  }

  ngOnInit(): void {
    this.cargar(0);
  }

  /** Lo que cambió, de antes a después. */
  protected detalle(e: EventoAuditoria): string {
    return [e.antes, e.despues].filter((x) => x).join(' → ');
  }

  protected cargar(pagina: number): void {
    this.api.auditoria(pagina).subscribe({
      next: (d) => this.datos.set(d),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
