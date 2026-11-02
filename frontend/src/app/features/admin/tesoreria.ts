import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';

import { ApiService, Tesoreria } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-area-tesoreria',
  imports: [CurrencyPipe, DatePipe],
  template: `
    <h1>Tesorería: caja principal</h1>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    } @else if (datos(); as d) {
      <section class="tarjeta">
        <h2>Saldo</h2>
        <p class="importe">{{ d.saldo | currency: 'USD' }}</p>
      </section>
      @if (d.movimientos.total === 0) {
        <p class="vacio">Aún no hay movimientos.</p>
      } @else {
        <div class="tabla">
          <table>
            <thead><tr><th>Fecha</th><th>Concepto</th><th class="num">Importe</th></tr></thead>
            <tbody>
              @for (m of d.movimientos.contenido; track m.id) {
                <tr>
                  <td>{{ m.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                  <td>{{ m.concepto }}</td>
                  <td class="num">{{ m.sentido === 'SALIDA' ? '−' : '+' }}{{ m.importe | currency: 'USD' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    } @else {
      <p class="cargando" role="status">Cargando…</p>
    }
  `,
})
export class AreaTesoreria implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly datos = signal<Tesoreria | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    alCambiar(() => this.ngOnInit());
  }

  ngOnInit(): void {
    this.api.tesoreria().subscribe({
      next: (d) => this.datos.set(d),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
