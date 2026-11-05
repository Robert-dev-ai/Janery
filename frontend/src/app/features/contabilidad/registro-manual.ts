import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, AsientoManual, LineaManual, Pagina } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { DialogoService } from '../../shared/components/dialogo';
import { Paginador } from '../../shared/components/paginador';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-registro-manual',
  imports: [CurrencyPipe, DatePipe, FormsModule, Paginador],
  template: `
    <h1>Registro manual</h1>
    <p class="suave">Para lo que el sistema no registra solo: multas, gastos, bancos o bienes. Mueve la caja principal, salvo la provisión. Nada se edita ni se borra: un error se corrige con un reverso.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }

    <form class="tarjeta estrecho" (ngSubmit)="registrar()">
      <label>Línea
        <select name="linea" [ngModel]="linea()" (ngModelChange)="elegir($event)">
          @for (l of lineas(); track l.codigo) { <option [value]="l.codigo">{{ l.codigo }} · {{ l.nombre }}</option> }
        </select>
      </label>
      @if (actual()?.admiteDisminuir) {
        <label>Efecto
          <select name="efecto" [(ngModel)]="disminuye">
            <option [ngValue]="false">Aumenta</option>
            <option [ngValue]="true">Disminuye</option>
          </select>
        </label>
      }
      <label>Importe (USD) <input type="number" min="0" step="1" name="importe" [(ngModel)]="importe" /></label>
      <label>Descripción <input name="descripcion" maxlength="150" [(ngModel)]="descripcion" /></label>
      <button type="submit" [disabled]="enviando() || !linea() || !importe || importe <= 0 || !descripcion.trim()">Registrar</button>
    </form>

    @if (datos(); as d) {
      @if (d.total === 0) {
        <p class="vacio">Aún no hay registros manuales.</p>
      } @else {
        <div class="tabla">
          <table>
            <thead><tr><th scope="col">Fecha</th><th scope="col">Línea</th><th scope="col">Descripción</th><th scope="col" class="num">Importe</th><th scope="col">Acción</th></tr></thead>
            <tbody>
              @for (a of d.contenido; track a.id) {
                <tr>
                  <td>{{ a.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                  <td>{{ a.linea }} · {{ a.nombre }}</td>
                  <td>{{ a.descripcion }}</td>
                  <td class="num">{{ a.importe | currency: 'USD' }}</td>
                  <td>
                    @if (a.reversoDe) {
                      <span class="estado estado-neutro">Reverso</span>
                    } @else if (a.revertido) {
                      <span class="estado estado-neutro">Revertido</span>
                    } @else {
                      <button type="button" class="secundario" (click)="revertir(a)">Revertir</button>
                    }
                  </td>
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
  `,
})
export class RegistroManual implements OnInit {
  private readonly api = inject(ApiService);
  private readonly dialogo = inject(DialogoService);

  protected readonly lineas = signal<LineaManual[]>([]);
  protected readonly linea = signal('');
  protected readonly actual = computed(() => this.lineas().find((l) => l.codigo === this.linea()));
  protected readonly datos = signal<Pagina<AsientoManual> | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  /** Mientras una operación está en curso: evita el doble clic y deja ver que se está procesando. */
  protected readonly enviando = signal(false);

  protected disminuye = false;
  protected importe: number | null = null;
  protected descripcion = '';

  // Una clave por intento: se renueva solo tras un éxito, para que reintentar no duplique.
  private clave: string = crypto.randomUUID();

  constructor() {
    alCambiar(() => this.cargar(this.datos()?.pagina ?? 0));
  }

  ngOnInit(): void {
    this.api.lineasManuales().subscribe({
      next: (l) => {
        this.lineas.set(l);
        this.linea.set(l[0]?.codigo ?? '');
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
    this.cargar(0);
  }

  protected elegir(codigo: string): void {
    this.linea.set(codigo);
    this.disminuye = false;
  }

  protected cargar(pagina: number): void {
    this.api.asientos(pagina).subscribe({ next: (d) => this.datos.set(d), error: (e) => this.error.set(mensajeDeError(e)) });
  }

  protected registrar(): void {
    if (this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    this.aviso.set(null);
    this.api.registrarAsiento({ linea: this.linea(), disminuye: this.disminuye, importe: this.importe!, descripcion: this.descripcion.trim() }, this.clave).subscribe({
      next: () => {
        this.enviando.set(false);
        this.clave = crypto.randomUUID();
        this.importe = null;
        this.descripcion = '';
        this.aviso.set('Registro guardado.');
        this.cargar(0);
      },
      error: (e) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  protected async revertir(a: AsientoManual): Promise<void> {
    const motivo = await this.dialogo.pedirTexto(`Motivo para revertir «${a.descripcion}»:`, 'Revertir');
    if (motivo) {
      this.error.set(null);
      this.api.revertirAsiento(a.id, motivo).subscribe({ next: () => this.cargar(this.datos()?.pagina ?? 0), error: (e) => this.error.set(mensajeDeError(e)) });
    }
  }
}
