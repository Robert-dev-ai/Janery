import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, HistorialParametro, Parametro } from '../../core/services/api';
import { mensajeDeError } from '../../shared/utils/errores';

const UNIDADES: Record<string, string> = {
  USD: 'USD',
  PCT: '%',
  PCT_DIARIO: '% diario',
  PCT_MENSUAL: '% mensual',
  PCT_ANUAL: '% anual',
  DIAS: 'días',
  MESES: 'meses',
  MES: 'mes del año',
  USOS_MES: 'veces al mes',
};

interface Edicion {
  parametro: Parametro;
  valor: number | null;
  vigenteDesde: string;
  motivo: string;
}

@Component({
  selector: 'app-admin-parametros',
  imports: [DatePipe, DecimalPipe, FormsModule],
  template: `
    <h1>Parámetros</h1>
    <p class="suave">Los cambios solo afectan a operaciones futuras.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }

    @if (edicion(); as e) {
      <form class="tarjeta estrecho" (ngSubmit)="guardar(e)">
        <h2>{{ e.parametro.nombre }}</h2>
        <p class="suave">{{ e.parametro.descripcion }}</p>
        <label>Nuevo valor ({{ unidad(e.parametro.unidad) }})
          <input type="number" min="0" step="any" name="valor" [(ngModel)]="e.valor" required />
        </label>
        <label>Vigente desde (vacío: ahora)
          <input type="datetime-local" name="desde" [(ngModel)]="e.vigenteDesde" />
        </label>
        <label>Motivo <input name="motivo" maxlength="300" [(ngModel)]="e.motivo" required /></label>
        <div class="acciones">
          <button type="submit" [disabled]="e.valor === null || !e.motivo.trim()">Guardar</button>
          <button type="button" class="secundario" (click)="edicion.set(null)">Cancelar</button>
        </div>
      </form>
    }

    @if (historial(); as h) {
      <section class="tarjeta">
        <h2>Historial</h2>
        <div class="tabla">
          <table>
            <thead><tr><th>Registrado</th><th>Vigente desde</th><th class="num">Anterior</th><th class="num">Nuevo</th><th>Motivo</th></tr></thead>
            <tbody>
              @for (x of h; track $index) {
                <tr>
                  <td>{{ x.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                  <td>{{ x.vigenteDesde | date: 'short' : 'America/Guayaquil' }}</td>
                  <td class="num">{{ x.valorAnterior ?? '—' }}</td>
                  <td class="num">{{ x.valorNuevo }}</td>
                  <td>{{ x.motivo }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <button type="button" class="secundario" (click)="historial.set(null)">Cerrar historial</button>
      </section>
    }

    <div class="tabla">
      <table>
        <thead><tr><th>Parámetro</th><th class="num">Vigente</th><th>Unidad</th><th>Desde</th><th>Programado</th><th>Acciones</th></tr></thead>
        <tbody>
          @for (p of parametros(); track p.id) {
            <tr>
              <td>{{ p.nombre }}</td>
              <td class="num">{{ p.vigente ? (p.vigente.valor | number: '1.0-6') : 'Sin valor' }}</td>
              <td>{{ unidad(p.unidad) }}</td>
              <td>{{ p.vigente?.vigenteDesde | date: 'shortDate' : 'America/Guayaquil' }}</td>
              <td>
                @if (p.programado; as f) {
                  {{ f.valor | number: '1.0-6' }} desde {{ f.vigenteDesde | date: 'short' : 'America/Guayaquil' }}
                }
              </td>
              <td class="acciones">
                <button type="button" (click)="editar(p)">Cambiar</button>
                <button type="button" class="secundario" (click)="verHistorial(p)">Historial</button>
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class AdminParametros implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly parametros = signal<Parametro[]>([]);
  protected readonly edicion = signal<Edicion | null>(null);
  protected readonly historial = signal<HistorialParametro[] | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  protected unidad(codigo: string): string {
    return UNIDADES[codigo] ?? codigo;
  }

  protected editar(parametro: Parametro): void {
    this.edicion.set({ parametro, valor: parametro.vigente?.valor ?? null, vigenteDesde: '', motivo: '' });
  }

  protected guardar(e: Edicion): void {
    this.error.set(null);
    const desde = e.vigenteDesde ? new Date(e.vigenteDesde).toISOString() : null;
    this.api.cambiarParametro(e.parametro.id, e.valor!, desde, e.motivo.trim()).subscribe({
      next: () => {
        this.edicion.set(null);
        this.cargar();
      },
      error: (err) => this.error.set(mensajeDeError(err)),
    });
  }

  protected verHistorial(p: Parametro): void {
    this.api.historialParametro(p.id).subscribe({
      next: (h) => this.historial.set(h),
      error: (err) => this.error.set(mensajeDeError(err)),
    });
  }

  private cargar(): void {
    this.api.parametros().subscribe({
      next: (p) => this.parametros.set(p),
      error: (err) => this.error.set(mensajeDeError(err)),
    });
  }
}
