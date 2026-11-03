import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, LibroMayor } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { Paginador } from '../../shared/components/paginador';
import { nombreConcepto } from '../../shared/utils/etiquetas';
import { mensajeDeError } from '../../shared/utils/errores';

const LINEAS = [
  ['1.1', 'Caja'], ['1.2', 'Bancos'], ['1.3', 'Inversiones'], ['1.4', 'Créditos por cobrar'], ['1.5', 'Otras cuentas por cobrar'], ['1.6', 'Local'],
  ['1.7', 'Muebles y equipos'], ['1.8', 'Otros activos'], ['1.99', 'Provisión de cartera'], ['2.1', 'Ahorros captados'], ['2.2', 'Plazo fijo'],
  ['2.3', 'Cuentas por pagar'], ['2.4', 'Créditos externos por pagar'], ['2.5', 'Fondo de desgravamen'], ['2.6', 'Fondo solidario'], ['3.1', 'Aportes iniciales'],
  ['3.2', 'Aportes mensuales y adicionales'], ['3.3', 'Capitalizaciones'], ['3.4', 'Donaciones'], ['3.5', 'Reservas'], ['3.7', 'Retiros y reservas del resultado'],
  ['4.1', 'Intereses ganados en créditos'], ['4.2', 'Interés de mora'], ['4.3', 'Multas'], ['4.4', 'Intereses bancarios'], ['4.5', 'Ingresos por actividades'],
  ['4.6', 'Venta de formularios'], ['4.7', 'Otros ingresos'], ['4.8', 'Suministros de créditos'], ['5.1', 'Intereses de ahorros'], ['5.2', 'Intereses de préstamos externos'], ['5.3', 'Gastos bancarios'],
  ['5.4', 'Gastos de personal'], ['5.5', 'Gastos de suministros'], ['5.6', 'Gastos de transportes'], ['5.7', 'Gastos de alimentación'], ['5.8', 'Otros gastos operativos'],
  ['5.9', 'Gastos de provisión'],
];

/** Movimientos de un solo rubro en un mes: qué subió, qué bajó, de quién y el saldo que fue quedando. */
@Component({
  selector: 'app-libro-mayor',
  imports: [CurrencyPipe, DatePipe, FormsModule, Paginador],
  template: `
    <h1>Libro mayor</h1>
    <div class="herramientas">
      <label>Línea
        <select name="linea" [ngModel]="linea()" (ngModelChange)="elegirLinea($event)">
          @for (l of lineas; track l[0]) { <option [value]="l[0]">{{ l[0] }} · {{ l[1] }}</option> }
        </select>
      </label>
      <label>Mes <input type="month" name="periodo" [ngModel]="periodo()" (ngModelChange)="elegirPeriodo($event)" /></label>
    </div>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (datos(); as d) {
      <div class="importes">
        <section class="tarjeta"><h2>Saldo inicial</h2><p class="importe">{{ d.saldoInicial | currency: 'USD' }}</p></section>
        <section class="tarjeta"><h2>Saldo final</h2><p class="importe">{{ d.saldoFinal | currency: 'USD' }}</p></section>
      </div>
      @if (d.movimientos.total === 0) {
        <p class="vacio">Esta línea no tiene movimientos en este mes.</p>
      } @else {
        <div class="tabla">
          <table>
            <thead><tr><th>Fecha</th><th>Movimiento</th><th>De quién</th><th class="num">Sube</th><th class="num">Baja</th><th class="num">Saldo</th></tr></thead>
            <tbody>
              @for (f of d.movimientos.contenido; track $index) {
                <tr>
                  <td>{{ f.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                  <td>{{ concepto(f.concepto) }}</td>
                  <td>{{ f.quien }}</td>
                  <td class="num positivo">{{ f.sube ? '+' + (f.sube | currency: 'USD') : '' }}</td>
                  <td class="num negativo">{{ f.baja ? '−' + (f.baja | currency: 'USD') : '' }}</td>
                  <td class="num">{{ f.saldo | currency: 'USD' }}</td>
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
export class LibroMayorPagina implements OnInit {
  protected readonly concepto = nombreConcepto;
  private readonly api = inject(ApiService);

  protected readonly lineas = LINEAS;
  protected readonly linea = signal('1.1');
  protected readonly periodo = signal('');
  protected readonly datos = signal<LibroMayor | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    alCambiar(() => this.cargar(this.datos()?.movimientos.pagina ?? 0));
  }

  ngOnInit(): void {
    this.cargar(0);
  }

  protected elegirLinea(linea: string): void {
    this.linea.set(linea);
    this.cargar(0);
  }

  protected elegirPeriodo(periodo: string): void {
    this.periodo.set(periodo);
    this.error.set(null);
    this.cargar(0);
  }

  protected cargar(pagina: number): void {
    this.api.libroMayor(this.linea(), this.periodo() || null, pagina).subscribe({ next: (d) => this.datos.set(d), error: (e) => this.error.set(mensajeDeError(e)) });
  }
}
