import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, LibroDiario } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { Paginador } from '../../shared/components/paginador';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-libro-diario',
  imports: [CurrencyPipe, DatePipe, FormsModule, Paginador],
  template: `
    <h1>Libro diario</h1>
    <div class="herramientas">
      <label>Mes <input type="month" name="periodo" [ngModel]="periodo()" (ngModelChange)="elegir($event)" /></label>
    </div>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (datos(); as d) {
      <div class="importes">
        <section class="tarjeta"><h2>Total debe</h2><p class="importe">{{ d.totalDebe | currency: 'USD' }}</p></section>
        <section class="tarjeta"><h2>Total haber</h2><p class="importe">{{ d.totalHaber | currency: 'USD' }}</p></section>
      </div>
      @if (d.movimientos.total === 0) {
        <p class="vacio">No hay movimientos en este mes.</p>
      } @else {
        <div class="tabla">
          <table>
            <thead><tr><th>Fecha</th><th>Operación</th><th>Código</th><th>Línea</th><th>Concepto</th><th class="num">Debe</th><th class="num">Haber</th></tr></thead>
            <tbody>
              @for (f of d.movimientos.contenido; track $index) {
                <tr>
                  <td>{{ f.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                  <td>{{ f.operacionId ?? '—' }}</td>
                  <td>{{ f.linea }}</td>
                  <td>{{ f.nombre }}</td>
                  <td>{{ f.concepto }}</td>
                  <td class="num">{{ f.debe ? (f.debe | currency: 'USD') : '' }}</td>
                  <td class="num">{{ f.haber ? (f.haber | currency: 'USD') : '' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-paginador [pagina]="d.movimientos.pagina" [tamano]="d.movimientos.tamano" [total]="d.movimientos.total" (cambiar)="cargar($event)" />
      }
    } @else if (!error()) {
      <p class="cargando" role="status">Cargando…</p>
    }
  `,
})
export class LibroDiarioPagina implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly periodo = signal('');
  protected readonly datos = signal<LibroDiario | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    alCambiar(() => this.cargar(this.datos()?.movimientos.pagina ?? 0));
  }

  ngOnInit(): void {
    this.cargar(0);
  }

  protected elegir(periodo: string): void {
    this.periodo.set(periodo);
    this.error.set(null);
    this.cargar(0);
  }

  protected cargar(pagina: number): void {
    this.api.libroDiario(this.periodo() || null, pagina).subscribe({ next: (d) => this.datos.set(d), error: (e) => this.error.set(mensajeDeError(e)) });
  }
}
