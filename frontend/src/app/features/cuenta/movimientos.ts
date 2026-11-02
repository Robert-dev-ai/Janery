import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';

import { ApiService, MovimientoCuenta, Pagina } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { descargar } from '../../shared/utils/descarga';
import { Paginador } from '../../shared/components/paginador';
import { nombreConcepto } from '../../shared/utils/etiquetas';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-movimientos',
  imports: [CurrencyPipe, DatePipe, Paginador],
  template: `
    <h1>Movimientos</h1>
    <p><button type="button" class="secundario" (click)="exportar()">Descargar estado de cuenta (CSV)</button></p>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    } @else if (datos(); as d) {
      @if (d.total === 0) {
        <p class="vacio">Aún no tiene movimientos.</p>
      } @else {
        <div class="tabla">
          <table>
            <thead><tr><th>Fecha</th><th>Concepto</th><th class="num">Importe</th><th class="num">Saldo</th></tr></thead>
            <tbody>
              @for (m of d.contenido; track m.id) {
                <tr>
                  <td>{{ m.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                  <td>{{ concepto(m.concepto) }}@if (m.detalle) { <br /><span class="suave">{{ m.detalle }}</span> }</td>
                  <td class="num">{{ m.sentido === 'SALIDA' ? '−' : '+' }}{{ m.importe | currency: 'USD' }}</td>
                  <td class="num">{{ m.saldoResultante | currency: 'USD' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-paginador [pagina]="d.pagina" [tamano]="d.tamano" [total]="d.total" (cambiar)="cargar($event)" />
      }
    } @else {
      <p class="cargando" role="status">Cargando…</p>
    }
  `,
})
export class Movimientos implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly concepto = nombreConcepto;
  protected readonly datos = signal<Pagina<MovimientoCuenta> | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    alCambiar(() => this.cargar(this.datos()?.pagina ?? 0));
  }

  ngOnInit(): void {
    this.cargar(0);
  }

  protected exportar(): void {
    this.api.miEstadoDeCuentaCsv().subscribe({
      next: (archivo) => descargar(archivo, 'estado_de_cuenta.csv'),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected cargar(pagina: number): void {
    this.api.misMovimientos(pagina).subscribe({
      next: (d) => this.datos.set(d),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
