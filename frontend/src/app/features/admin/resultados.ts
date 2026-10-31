import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, Observable } from 'rxjs';

import { ApiService, CierreAnual, Pagina, Resultado, ResumenSocio } from '../../core/services/api';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-admin-resultados',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, FormsModule],
  template: `
    <h1>Resultados y socios</h1>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }

    <h2>Socios</h2>
    @if (socios().length === 0) {
      <p>Aún no hay socios. Regístrelos en Usuarios.</p>
    } @else {
      <div class="tabla">
        <table>
          <thead><tr><th>Socio</th><th class="num">Obligatorios</th><th class="num">Personales</th><th class="num">Caja chica</th><th class="num">Valor</th><th class="num">Participación</th></tr></thead>
          <tbody>
            @for (s of socios(); track s.socioId) {
              <tr>
                <td>{{ s.nombre }}</td>
                <td class="num">{{ s.totalObligatorio | currency: 'USD' }}</td>
                <td class="num">{{ s.totalPersonal | currency: 'USD' }}</td>
                <td class="num">{{ s.totalCajaChica | currency: 'USD' }}</td>
                <td class="num">{{ s.valorActual | currency: 'USD' }}</td>
                <td class="num">{{ s.porcentaje | number: '1.2-4' }} %</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }

    <form class="tarjeta estrecho" style="margin-top:1rem" (ngSubmit)="registrar()">
      <h2>Registrar ganancia o pérdida</h2>
      <p class="suave">Se reparte al momento entre los socios, según el valor de cada uno.</p>
      <label>Tipo
        <select name="tipo" [(ngModel)]="tipo">
          <option value="GANANCIA">Ganancia</option>
          <option value="PERDIDA">Pérdida</option>
        </select>
      </label>
      <label>Importe (USD) <input type="number" min="0.01" step="0.01" name="importe" [(ngModel)]="importe" /></label>
      <label>Concepto <input name="concepto" maxlength="200" [(ngModel)]="concepto" /></label>
      <label>Fecha (vacío: hoy) <input type="date" name="fecha" [(ngModel)]="fecha" /></label>
      <button type="submit" [disabled]="!importe || importe <= 0 || !concepto.trim()">Registrar</button>
    </form>

    @if (resultados(); as r) {
      <h2>Resultados registrados</h2>
      @if (r.total === 0) {
        <p>Aún no hay resultados.</p>
      } @else {
        <div class="tabla">
          <table>
            <thead><tr><th>Fecha</th><th>Concepto</th><th class="num">Importe</th></tr></thead>
            <tbody>
              @for (x of r.contenido; track x.id) {
                <tr>
                  <td>{{ x.fecha | date: 'mediumDate' : 'UTC' }}</td>
                  <td>{{ x.concepto }}</td>
                  <td class="num">{{ x.tipo === 'PERDIDA' ? '−' : '+' }}{{ x.importe | currency: 'USD' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <div class="paginador">
          <button type="button" class="secundario" [disabled]="r.pagina === 0" (click)="cargarResultados(r.pagina - 1)">Anterior</button>
          <span>Página {{ r.pagina + 1 }}</span>
          <button type="button" class="secundario" [disabled]="(r.pagina + 1) * r.tamano >= r.total" (click)="cargarResultados(r.pagina + 1)">Siguiente</button>
        </div>
      }
    }

    <section class="tarjeta" style="margin-top:1rem">
      <h2>Cierre anual</h2>
      <p class="suave">Si el año tuvo ganancia, separa el fondo de reserva y paga el resto a la cuenta principal de cada socio. No se puede deshacer.</p>
      <form class="acciones" (ngSubmit)="cerrar()">
        <label>Año <input type="number" step="1" name="anio" [(ngModel)]="anio" /></label>
        <button type="submit" class="peligro" [disabled]="!anio">Cerrar año</button>
      </form>
      @if (cierres().length) {
        <div class="tabla">
          <table>
            <thead><tr><th>Año</th><th class="num">Resultado neto</th><th class="num">Reserva</th><th class="num">Distribuido</th></tr></thead>
            <tbody>
              @for (c of cierres(); track c.anio) {
                <tr>
                  <td>{{ c.anio }}</td>
                  <td class="num">{{ c.resultadoNeto | currency: 'USD' }}</td>
                  <td class="num">{{ c.reserva | currency: 'USD' }} ({{ c.porcentajeReserva | number: '1.0-2' }} %)</td>
                  <td class="num">{{ c.distribuido | currency: 'USD' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
})
export class AdminResultados implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly socios = signal<ResumenSocio[]>([]);
  protected readonly resultados = signal<Pagina<Resultado> | null>(null);
  protected readonly cierres = signal<CierreAnual[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  protected tipo = 'GANANCIA';
  protected importe: number | null = null;
  protected concepto = '';
  protected fecha = '';
  protected anio: number | null = new Date().getFullYear() - 1;

  ngOnInit(): void {
    this.cargar();
  }

  protected registrar(): void {
    this.ejecutar(this.api.registrarResultado(this.tipo, this.importe!, this.concepto.trim(), this.fecha || null),
      'Resultado registrado y repartido.', () => {
        this.importe = null;
        this.concepto = '';
        this.fecha = '';
      });
  }

  protected cerrar(): void {
    if (confirm(`¿Cerrar el año ${this.anio}? No se puede deshacer.`)) {
      this.ejecutar(this.api.cerrarAnio(this.anio!), `Año ${this.anio} cerrado.`);
    }
  }

  protected cargarResultados(pagina: number): void {
    this.api.resultados(pagina).subscribe({
      next: (r) => this.resultados.set(r),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  private ejecutar(accion: Observable<unknown>, aviso: string, alTerminar?: () => void): void {
    this.error.set(null);
    this.aviso.set(null);
    accion.subscribe({
      next: () => {
        this.aviso.set(aviso);
        alTerminar?.();
        this.cargar();
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  private cargar(): void {
    forkJoin([this.api.socios(), this.api.cierres()]).subscribe({
      next: ([socios, cierres]) => {
        this.socios.set(socios);
        this.cierres.set(cierres);
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
    this.cargarResultados(0);
  }
}
