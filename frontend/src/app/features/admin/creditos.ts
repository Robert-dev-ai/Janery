import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';

import { ApiService, Credito, Pagina } from '../../core/services/api';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-admin-creditos',
  imports: [CurrencyPipe, DecimalPipe, FormsModule],
  template: `
    <h1>Créditos</h1>
    <label class="estrecho">Estado
      <select [ngModel]="estado()" (ngModelChange)="filtrar($event)">
        <option value="SOLICITADO">Por aprobar</option>
        <option value="ACTIVO">Activos</option>
        <option value="FINALIZADO">Finalizados</option>
        <option value="RECHAZADO">Rechazados</option>
      </select>
    </label>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }

    @if (datos(); as d) {
      @if (d.total === 0) {
        <p>No hay créditos en este estado.</p>
      } @else {
        <div class="tabla">
          <table>
            <thead><tr><th>Titular</th><th>Tipo</th><th>Perfil</th><th class="num">Importe</th><th>Plazo</th><th>Tasa</th><th class="num">Pendiente</th><th>Acciones</th></tr></thead>
            <tbody>
              @for (c of d.contenido; track c.id) {
                <tr>
                  <td>{{ c.titular }}</td>
                  <td>{{ c.familia }} {{ c.modalidad ?? '' }}</td>
                  <td>{{ c.perfil }}</td>
                  <td class="num">{{ c.importe | currency: 'USD' }}</td>
                  <td>{{ c.plazoMeses ? c.plazoMeses + ' meses' : '—' }}</td>
                  <td>{{ c.tasaInteres | number: '1.0-4' }} {{ c.unidadTasa === 'PCT_DIARIO' ? '% diario' : '% mensual' }}</td>
                  <td class="num">{{ c.totalPendiente | currency: 'USD' }}</td>
                  <td class="acciones">
                    @if (c.estado === 'SOLICITADO') {
                      <button type="button" (click)="aprobar(c)">Aprobar y desembolsar</button>
                      <button type="button" class="peligro" (click)="rechazar(c)">Rechazar</button>
                    } @else if (c.motivoRechazo) {
                      {{ c.motivoRechazo }}
                    }
                  </td>
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
    }
  `,
})
export class AdminCreditos implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly estado = signal('SOLICITADO');
  protected readonly datos = signal<Pagina<Credito> | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.cargar(0);
  }

  protected filtrar(estado: string): void {
    this.estado.set(estado);
    this.cargar(0);
  }

  protected cargar(pagina: number): void {
    this.api.creditosAdmin(this.estado(), pagina).subscribe({
      next: (d) => this.datos.set(d),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected aprobar(c: Credito): void {
    if (confirm(`¿Aprobar y desembolsar ${c.importe} USD a ${c.titular}?`)) {
      this.ejecutar(this.api.aprobarCredito(c.id));
    }
  }

  protected rechazar(c: Credito): void {
    const motivo = prompt(`Motivo del rechazo a ${c.titular}:`)?.trim();
    if (motivo) {
      this.ejecutar(this.api.rechazarCredito(c.id, motivo));
    }
  }

  private ejecutar(accion: Observable<void>): void {
    this.error.set(null);
    accion.subscribe({
      next: () => this.cargar(this.datos()?.pagina ?? 0),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
