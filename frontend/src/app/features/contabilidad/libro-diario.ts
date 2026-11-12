import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, LibroDiario } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { Paginador } from '../../shared/components/paginador';
import { nombreConcepto } from '../../shared/utils/etiquetas';
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
      <section class="total">
        <div>
          <h2>Dinero en la caja hoy</h2>
          <p class="total-importe">{{ d.saldoCaja | currency: 'USD' }}</p>
        </div>
        <p class="total-partes">
          <span>Entró este mes<strong>+{{ d.entradasCaja | currency: 'USD' }}</strong></span>
          <span>Salió este mes<strong>−{{ d.salidasCaja | currency: 'USD' }}</strong></span>
        </p>
      </section>
      <p class="suave">Cada operación mueve dos o más rubros: «Sube» y «Baja» dicen qué pasó con cada uno. En los movimientos de la caja, «Queda en caja» muestra cuánto dinero había justo después.</p>
      @if (d.movimientos.total === 0) {
        <p class="vacio">No hay movimientos en este mes.</p>
      } @else {
        <div class="tabla">
          <table class="tabla-diario">
            <thead><tr><th scope="col" class="solo-escritorio">Fecha</th><th scope="col">Movimiento</th><th scope="col">De quién</th><th scope="col" class="solo-escritorio">Rubro</th><th scope="col" class="num">Sube</th><th scope="col" class="num">Baja</th><th scope="col" class="num solo-escritorio">Queda en caja</th></tr></thead>
            <tbody>
              @for (f of d.movimientos.contenido; track $index) {
                <tr>
                  <td class="solo-escritorio">{{ f.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                  <td>{{ concepto(f.concepto) }}</td>
                  <td>{{ f.quien }}</td>
                  <td class="solo-escritorio">{{ f.nombre }} <small class="codigo">{{ f.linea }}</small></td>
                  <td class="num positivo">{{ f.sube ? '+' + (f.sube | currency: 'USD') : '' }}</td>
                  <td class="num negativo">{{ f.baja ? '−' + (f.baja | currency: 'USD') : '' }}</td>
                  <td class="num solo-escritorio">@if (f.cajaDespues !== null) { <strong>{{ f.cajaDespues | currency: 'USD' }}</strong> }</td>
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
  protected readonly concepto = nombreConcepto;
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
