import { CurrencyPipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, EstadoContable } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { mensajeDeError } from '../../shared/utils/errores';

/** Mismo orden y códigos que el balance en Excel del propietario. La caja chica lleva sus cuentas aparte. */
@Component({
  selector: 'app-balance',
  imports: [CurrencyPipe, DecimalPipe, FormsModule, NgTemplateOutlet],
  template: `
    <h1>Balance general</h1>
    <div class="herramientas">
      <label>Mes <input type="month" name="periodo" [ngModel]="periodo()" (ngModelChange)="elegir($event)" /></label>
    </div>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }

    <ng-template #seccion let-s let-etiqueta="etiqueta">
      <div class="tabla mb-4">
        <table>
          <thead><tr><th scope="col">Código</th><th scope="col">{{ s.titulo }}</th><th scope="col" class="num">USD</th></tr></thead>
          <tbody>
            @for (l of s.lineas; track l.codigo) {
              <tr [class.fila-detalle]="l.detalle"><td>{{ l.codigo }}</td><td>{{ l.nombre }}</td><td class="num">{{ l.valor | currency: 'USD' }}</td></tr>
            }
            <tr class="fila-total"><td></td><td>{{ etiqueta }}</td><td class="num">{{ s.total | currency: 'USD' }}</td></tr>
          </tbody>
        </table>
      </div>
    </ng-template>

    @if (estado(); as e) {
      <div class="rejilla-balance">
        <ng-container [ngTemplateOutlet]="seccion" [ngTemplateOutletContext]="{ $implicit: e.ganancias, etiqueta: 'Total ganancias' }" />
        <ng-container [ngTemplateOutlet]="seccion" [ngTemplateOutletContext]="{ $implicit: e.gastos, etiqueta: 'Total costos' }" />
      </div>
      <p class="resultado-mes"><span>Resultado del mes (total ganancias − total costos)</span><strong>{{ e.resultadoMes | currency: 'USD' }}</strong></p>

      <div class="rejilla-balance">
        <div>
          <ng-container [ngTemplateOutlet]="seccion" [ngTemplateOutletContext]="{ $implicit: e.activoCorriente, etiqueta: 'Total activo corriente' }" />
          <ng-container [ngTemplateOutlet]="seccion" [ngTemplateOutletContext]="{ $implicit: e.activosFijos, etiqueta: 'Total activos fijos' }" />
        </div>
        <div>
          <ng-container [ngTemplateOutlet]="seccion" [ngTemplateOutletContext]="{ $implicit: e.pasivos, etiqueta: 'Total pasivos' }" />
          <ng-container [ngTemplateOutlet]="seccion" [ngTemplateOutletContext]="{ $implicit: e.capital, etiqueta: 'Total capital (patrimonio)' }" />
        </div>
      </div>

      <div class="importes">
        <section class="tarjeta"><h2>Total activos</h2><p class="importe">{{ e.totalActivos | currency: 'USD' }}</p></section>
        <section class="tarjeta"><h2>Total pasivos + patrimonio</h2><p class="importe">{{ e.totalPasivoCapital | currency: 'USD' }}</p></section>
        <section class="tarjeta">
          <h2>Diferencia</h2>
          <p class="importe">{{ e.diferencia | currency: 'USD' }}</p>
          @if (e.diferencia === 0) {
            <span class="estado estado-exito">El balance cuadra</span>
          } @else {
            <span class="estado estado-peligro">El balance no cuadra</span>
          }
        </section>
      </div>

      <h2>Indicadores financieros</h2>
      <div class="importes">
        @for (i of e.indicadores; track i.nombre) {
          <section class="tarjeta">
            <h2>{{ i.nombre }}</h2>
            <p class="importe">@if (i.valor !== null) { {{ i.valor | number: '1.2-2' }} % } @else { Sin datos }</p>
            <p class="suave">{{ i.explicacion }}</p>
          </section>
        }
      </div>
    } @else if (!error()) {
      <p class="cargando" role="status">Cargando…</p>
    }
  `,
})
export class Balance implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly periodo = signal('');
  protected readonly estado = signal<EstadoContable | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    alCambiar(() => this.ngOnInit());
  }

  ngOnInit(): void {
    this.api.estadoContable(this.periodo() || null).subscribe({
      next: (e) => {
        this.estado.set(e);
        this.periodo.set(e.periodo);
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected elegir(periodo: string): void {
    this.periodo.set(periodo);
    this.error.set(null);
    this.ngOnInit();
  }
}
