import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, CajaChica } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { Paginador } from '../../shared/components/paginador';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-area-caja-chica',
  imports: [CurrencyPipe, DatePipe, FormsModule, Paginador],
  template: `
    <h1>Caja chica</h1>
    <p class="suave">Fondo independiente de la caja principal.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (datos(); as d) {
      <section class="tarjeta">
        <h2>Saldo</h2>
        <p class="importe">{{ d.saldo | currency: 'USD' }}</p>
      </section>
      <form class="tarjeta estrecho" (ngSubmit)="registrar()">
        <h2>Registrar gasto</h2>
        <label>Importe (USD) <input type="number" min="0" step="1" name="importe" [(ngModel)]="importe" /></label>
        <label>Detalle <input name="detalle" maxlength="200" [(ngModel)]="detalle" /></label>
        <button type="submit" [disabled]="!importe || importe <= 0 || !detalle.trim()">Registrar</button>
      </form>
      @if (d.movimientos.total === 0) {
        <p class="vacio">Aún no hay movimientos.</p>
      } @else {
        <div class="tabla">
          <table>
            <thead><tr><th>Fecha</th><th>Concepto</th><th>Detalle</th><th class="num">Importe</th></tr></thead>
            <tbody>
              @for (m of d.movimientos.contenido; track m.id) {
                <tr>
                  <td>{{ m.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                  <td>{{ m.concepto }}</td>
                  <td>{{ m.detalle }}</td>
                  <td class="num">{{ m.sentido === 'SALIDA' ? '−' : '+' }}{{ m.importe | currency: 'USD' }}</td>
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
export class AreaCajaChica implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly datos = signal<CajaChica | null>(null);
  protected readonly error = signal<string | null>(null);

  protected importe: number | null = null;
  protected detalle = '';

  // Una clave por intento: se renueva solo tras un éxito, para que reintentar no duplique.
  private clave: string = crypto.randomUUID();

  constructor() {
    alCambiar(() => this.cargar(this.datos()?.movimientos?.pagina ?? 0));
  }

  ngOnInit(): void {
    this.cargar(0);
  }

  protected cargar(pagina: number): void {
    this.api.cajaChica(pagina).subscribe({
      next: (d) => this.datos.set(d),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected registrar(): void {
    this.error.set(null);
    this.api.gastoCajaChica(this.importe!, this.detalle.trim(), this.clave).subscribe({
      next: () => {
        this.clave = crypto.randomUUID();
        this.importe = null;
        this.detalle = '';
        this.cargar(0);
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
