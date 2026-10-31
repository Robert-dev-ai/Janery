import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';

import { ApiService, EventoAuditoria, Pagina } from '../../core/services/api';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-admin-auditoria',
  imports: [DatePipe],
  template: `
    <h1>Auditoría</h1>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (datos(); as d) {
      <div class="tabla">
        <table>
          <thead><tr><th>Fecha</th><th>Usuario</th><th>Acción</th><th>Entidad</th><th>Antes</th><th>Después</th><th>Motivo</th></tr></thead>
          <tbody>
            @for (e of d.contenido; track e.id) {
              <tr>
                <td>{{ e.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                <td>{{ e.actorId ?? '—' }}</td>
                <td>{{ e.accion }}</td>
                <td>{{ e.entidad }} {{ e.entidadId }}</td>
                <td>{{ e.antes }}</td>
                <td>{{ e.despues }}</td>
                <td>{{ e.motivo }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <div class="paginador">
        <button type="button" class="secundario" [disabled]="d.pagina === 0" (click)="cargar(d.pagina - 1)">Anterior</button>
        <span>Página {{ d.pagina + 1 }}</span>
        <button type="button" class="secundario" [disabled]="(d.pagina + 1) * d.tamano >= d.total" (click)="cargar(d.pagina + 1)">Siguiente</button>
      </div>
    }
  `,
})
export class AdminAuditoria implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly datos = signal<Pagina<EventoAuditoria> | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.cargar(0);
  }

  protected cargar(pagina: number): void {
    this.api.auditoria(pagina).subscribe({
      next: (d) => this.datos.set(d),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
