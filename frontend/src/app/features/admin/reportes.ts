import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, DefinicionInforme, TablaInforme } from '../../core/services/api';
import { descargar } from '../../shared/utils/descarga';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-reportes',
  imports: [FormsModule],
  template: `
    <h1>Informes</h1>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }

    <form class="tarjeta estrecho" (ngSubmit)="consultar()">
      <label>Informe
        <select name="informe" [ngModel]="codigo()" (ngModelChange)="elegir($event)">
          @for (d of catalogo(); track d.codigo) { <option [value]="d.codigo">{{ d.titulo }}</option> }
        </select>
      </label>
      <p class="suave">{{ actual()?.descripcion }}</p>
      @if (actual()?.usaFechas) {
        <label>Desde <input type="date" name="desde" [(ngModel)]="desde" /></label>
        <label>Hasta <input type="date" name="hasta" [(ngModel)]="hasta" /></label>
      }
      <div class="acciones">
        <button type="submit" [disabled]="!codigo()">Ver informe</button>
        <button type="button" class="secundario" [disabled]="!codigo()" (click)="exportar()">Descargar CSV</button>
      </div>
    </form>

    @if (tabla(); as t) {
      @if (t.filas.length === 0) {
        <p>Sin datos para este informe.</p>
      } @else {
        @if (t.truncado) {
          <p class="suave">Se muestran las primeras {{ t.filas.length }} filas. El CSV incluye más.</p>
        }
        <div class="tabla">
          <table>
            <thead>
              <tr>
                @for (c of t.columnas; track $index) { <th [class.num]="t.numericas[$index]">{{ c }}</th> }
              </tr>
            </thead>
            <tbody>
              @for (fila of t.filas; track $index) {
                <tr>
                  @for (v of fila; track $index) { <td [class.num]="t.numericas[$index]">{{ v }}</td> }
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    }
  `,
})
export class AdminReportes implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly catalogo = signal<DefinicionInforme[]>([]);
  protected readonly codigo = signal('');
  protected readonly actual = computed(() => this.catalogo().find((d) => d.codigo === this.codigo()));
  protected readonly tabla = signal<TablaInforme | null>(null);
  protected readonly error = signal<string | null>(null);

  protected desde = '';
  protected hasta = '';

  ngOnInit(): void {
    this.api.informes().subscribe({
      next: (c) => {
        this.catalogo.set(c);
        this.codigo.set(c[0]?.codigo ?? '');
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected elegir(codigo: string): void {
    this.codigo.set(codigo);
    this.tabla.set(null);
  }

  protected consultar(): void {
    this.error.set(null);
    this.api.informe(this.codigo(), this.desde || null, this.hasta || null).subscribe({
      next: (t) => this.tabla.set(t),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected exportar(): void {
    this.error.set(null);
    this.api.informeCsv(this.codigo(), this.desde || null, this.hasta || null).subscribe({
      next: (archivo) => descargar(archivo, `${this.codigo().toLowerCase()}.csv`),
      error: () => this.error.set('No se pudo descargar el informe. Revise las fechas.'),
    });
  }
}
