import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';

import { ApiService, MesCaja, Tesoreria } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { nombreConcepto } from '../../shared/utils/etiquetas';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-area-tesoreria',
  imports: [CurrencyPipe, DatePipe, DecimalPipe],
  template: `
    <h1>Tesorería: caja principal</h1>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    } @else if (datos(); as d) {
      <section class="total">
        <div>
          <h2>Saldo de la caja principal</h2>
          <p class="total-importe">{{ d.saldo | currency: 'USD' }}</p>
        </div>
        @if (d.meses[0]; as m) {
          <p class="total-partes">
            <span>{{ mes(m.periodo) }} (en curso)
              <strong>{{ signo(m) }} {{ m.porcentaje === null ? '—' : (abs(m.porcentaje) | number: '1.2-2') + ' %' }}</strong>
            </span>
            <span>Variación del mes<strong>{{ m.variacion >= 0 ? '+' : '−' }}{{ abs(m.variacion) | currency: 'USD' }}</strong></span>
          </p>
        }
      </section>
      <p class="suave">El porcentaje del mes en curso cambia con cada movimiento de la caja. Queda cerrado cuando el mes termina.</p>

      @if (d.meses.length) {
        <section class="seccion">
          <h2 class="seccion-titulo">Cómo ha variado cada mes</h2>
          <div class="tabla">
            <table>
              <thead><tr><th scope="col">Mes</th><th scope="col" class="num">Saldo al empezar</th><th scope="col" class="num">Saldo al terminar</th><th scope="col" class="num">Variación</th><th scope="col" class="num">%</th><th scope="col">Estado</th></tr></thead>
              <tbody>
                @for (m of d.meses; track m.periodo) {
                  <tr>
                    <td class="capitalize">{{ mes(m.periodo) }}</td>
                    <td class="num">{{ m.saldoInicial | currency: 'USD' }}</td>
                    <td class="num">{{ m.saldoFinal | currency: 'USD' }}</td>
                    <td class="num" [class.positivo]="m.variacion > 0" [class.negativo]="m.variacion < 0">{{ m.variacion >= 0 ? '+' : '−' }}{{ abs(m.variacion) | currency: 'USD' }}</td>
                    <td class="num" [class.positivo]="(m.porcentaje ?? 0) > 0" [class.negativo]="(m.porcentaje ?? 0) < 0">
                      {{ m.porcentaje === null ? '—' : signo(m) + ' ' + (abs(m.porcentaje) | number: '1.2-2') + ' %' }}
                    </td>
                    <td>
                      @if (m.cerrado) { <span class="estado estado-neutro">Cerrado</span> } @else { <span class="estado estado-informacion">En curso</span> }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </section>
      }
      @if (d.movimientos.total === 0) {
        <p class="vacio">Aún no hay movimientos.</p>
      } @else {
        <div class="tabla">
          <table>
            <thead><tr><th scope="col">Fecha</th><th scope="col">Movimiento</th><th scope="col">De quién</th><th scope="col" class="num">Importe</th></tr></thead>
            <tbody>
              @for (m of d.movimientos.contenido; track m.id) {
                <tr>
                  <td>{{ m.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                  <td>{{ concepto(m.concepto) }}</td>
                  <td>{{ m.quien }}</td>
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
  protected readonly concepto = nombreConcepto;
  private readonly api = inject(ApiService);

  protected readonly datos = signal<Tesoreria | null>(null);
  protected readonly error = signal<string | null>(null);

  protected mes(periodo: string): string {
    const [anio, mes] = periodo.split('-').map(Number);
    return new Date(anio, mes - 1, 1).toLocaleDateString('es-EC', { month: 'long', year: 'numeric' });
  }

  /** Con flecha: que suba o baje no dependa solo del color. */
  protected signo(m: MesCaja): string {
    return m.variacion > 0 ? '▲' : m.variacion < 0 ? '▼' : '';
  }

  protected abs(valor: number): number {
    return Math.abs(valor);
  }

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
