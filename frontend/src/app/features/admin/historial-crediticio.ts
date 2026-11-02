import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, CreditoDetalle, HistorialCrediticio, Pagina } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { Estado } from '../../shared/components/estado';
import { Paginador } from '../../shared/components/paginador';
import { nombreFamilia } from '../../shared/utils/etiquetas';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-historial-crediticio',
  imports: [CurrencyPipe, DatePipe, Estado, FormsModule, Paginador],
  template: `
    <h1>Historial crediticio</h1>
    <div class="herramientas">
      <label>Buscar por nombre
        <input type="search" [ngModel]="buscar()" (ngModelChange)="filtrar($event)" />
      </label>
    </div>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (datos(); as d) {
      @if (d.total === 0) {
        <p class="vacio">Aún no hay créditos registrados.</p>
      } @else {
        <div class="tabla">
          <table>
            <thead><tr><th>Persona</th><th>Créditos</th><th>Activos</th><th>Finalizados</th><th>Rechazados</th><th class="num">Capital pendiente</th><th>Letras vencidas</th><th>Pagadas con retraso</th><th></th></tr></thead>
            <tbody>
              @for (h of d.contenido; track h.id) {
                <tr>
                  <td>{{ h.nombre }}</td>
                  <td>{{ h.total }}</td>
                  <td>{{ h.activos }}</td>
                  <td>{{ h.finalizados }}</td>
                  <td>{{ h.rechazados }}</td>
                  <td class="num">{{ h.capitalPendiente | currency: 'USD' }}</td>
                  <td>@if (h.cuotasVencidas > 0) { <span class="estado estado-peligro">{{ h.cuotasVencidas }}</span> } @else { 0 }</td>
                  <td>{{ h.cuotasPagadasConRetraso }}</td>
                  <td><button type="button" class="secundario" (click)="abrir(h)">Ver créditos</button></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-paginador [pagina]="d.pagina" [tamano]="d.tamano" [total]="d.total" (cambiar)="cargar($event)" />
      }
    } @else if (!error()) {
      <p class="cargando" role="status">Cargando…</p>
    }

    @if (abierto(); as a) {
      <section class="tarjeta ficha-detalle">
        <div class="encabezado-tarjeta">
          <h2>{{ a.nombre }}</h2>
          <button type="button" class="secundario" (click)="abierto.set(null)">Cerrar</button>
        </div>
        @let creditos = a.creditos;
        <div class="tabla">
          <table>
            <thead><tr><th>Crédito</th><th>Estado</th><th class="num">Importe</th><th class="num">Capital pendiente</th><th>Letras pagadas</th><th>Próximo vencimiento</th></tr></thead>
            <tbody>
              @for (c of creditos; track c.id) {
                <tr>
                  <td>{{ familia(c.familia) }} {{ c.modalidad ?? '' }}</td>
                  <td><app-estado [valor]="c.estado" /></td>
                  <td class="num">{{ c.importe | currency: 'USD' }}</td>
                  <td class="num">{{ c.capitalPendiente | currency: 'USD' }}</td>
                  <td>{{ pagadas(c) }} de {{ c.cuotas.length }}</td>
                  <td>{{ proxima(c) ? (proxima(c)!.vencimiento | date: 'dd/MM/yyyy' : 'UTC') : '—' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="suave">Sin créditos.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    }
  `,
})
export class HistorialCrediticioPagina implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly familia = nombreFamilia;
  protected readonly buscar = signal('');
  protected readonly datos = signal<Pagina<HistorialCrediticio> | null>(null);
  protected readonly abierto = signal<{ nombre: string; creditos: CreditoDetalle[] } | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    alCambiar(() => this.cargar(this.datos()?.pagina ?? 0));
  }

  ngOnInit(): void {
    this.cargar(0);
  }

  protected filtrar(texto: string): void {
    this.buscar.set(texto);
    this.cargar(0);
  }

  protected cargar(pagina: number): void {
    this.api.historiales(this.buscar(), pagina).subscribe({ next: (d) => this.datos.set(d), error: (e) => this.error.set(mensajeDeError(e)) });
  }

  protected abrir(h: HistorialCrediticio): void {
    this.api.historialDe(h.id).subscribe({
      next: (creditos) => this.abierto.set({ nombre: h.nombre, creditos }),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected pagadas(c: CreditoDetalle): number {
    return c.cuotas.filter((q) => q.pagadaEn).length;
  }

  protected proxima(c: CreditoDetalle) {
    return c.estado === 'ACTIVO' ? c.cuotas.find((q) => !q.pagadaEn) : undefined;
  }
}
