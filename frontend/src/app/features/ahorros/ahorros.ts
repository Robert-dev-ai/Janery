import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';

import { Ahorro, ApiService, Credito, ProductosAhorro } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-ahorros',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, FormsModule],
  template: `
    <h1>Ahorros</h1>
    <p class="suave">Los fondos salen de su cuenta principal y vuelven a ella al retirar.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }

    <section class="total">
      <div>
        <h2>Total en ahorros</h2>
        <p class="total-importe">{{ total() | currency: 'USD' }}</p>
      </div>
      <p class="total-partes">
        <span>Normales<strong>{{ totalDe('NORMAL') | currency: 'USD' }}</strong></span>
        <span>Plazo fijo<strong>{{ totalDe('PLAZO_FIJO') | currency: 'USD' }}</strong></span>
        <span>Ahorro letras<strong>{{ totalLetras() | currency: 'USD' }}</strong></span>
      </p>
    </section>

    <section class="seccion">
      <h2 class="seccion-titulo">Mis ahorros</h2>
      <div class="rejilla-dos">
        @for (a of activos(); track a.id) {
          <article class="tarjeta">
            <h3>{{ a.nombre }}</h3>
            <p class="importe">{{ a.saldo | currency: 'USD' }}</p>
            @if (a.tipo === 'PLAZO_FIJO') {
              <p class="suave"><strong>Plazo fijo</strong> a {{ a.plazoMeses }} meses, al {{ a.tasaAnual | number: '1.0-4' }} % anual. Termina el {{ a.venceEn | date: 'mediumDate' : 'UTC' }}.</p>
              <div class="importes">
                <div>Interés a recibir<p class="importe">{{ a.interesAlVencimiento | currency: 'USD' }}</p></div>
                <div>Total al terminar<p class="importe">{{ a.saldo + (a.interesAlVencimiento ?? 0) | currency: 'USD' }}</p></div>
              </div>
              <button type="button" [disabled]="!a.retirable" (click)="liquidar(a)">
                {{ a.retirable ? 'Retirar todo' : 'Disponible al terminar el contrato' }}
              </button>
            } @else {
              <p class="suave"><strong>Ahorro normal</strong> al {{ a.tasaAnual | number: '1.0-4' }} % anual: el interés corre cada día, va aparte de su saldo y se libera cada día 1.</p>
              <div class="importes">
                <div>Interés corriendo<p class="importe">{{ a.interesAcumulado | currency: 'USD' }}</p></div>
                <div>Interés para retirar<p class="importe">{{ a.interesDisponible | currency: 'USD' }}</p></div>
              </div>
              <button type="button" class="secundario" [disabled]="a.interesDisponible <= 0" (click)="retirarInteres(a)">
                {{ a.interesDisponible > 0 ? 'Retirar interés' : 'Se libera el ' + (a.proximaLiberacion | date: 'dd/MM/yyyy' : 'UTC') }}
              </button>
              <form class="acciones" (ngSubmit)="mover(a, 'deposito')">
                <label>Importe (USD)
                  <input type="number" min="0" step="1" name="i{{ a.id }}" [(ngModel)]="importes[a.id]" />
                </label>
                <button type="submit" [disabled]="!importes[a.id]">Depositar</button>
                <button type="button" class="secundario" [disabled]="!importes[a.id]" (click)="mover(a, 'retiro')">Retirar</button>
              </form>
            }
          </article>
        } @empty {
          <p class="vacio lg:col-span-2">Aún no tiene ahorros.</p>
        }
      </div>
    </section>

    @if (ahorrosLetras().length) {
      <section class="seccion">
        <h2 class="seccion-titulo">Ahorro letras</h2>
        <p class="suave"><strong>Ahorro letras:</strong> se acumula con cada letra de su crédito y vuelve a su cuenta principal al terminarlo. También puede usarlo en la última letra.</p>
        <div class="rejilla-dos">
          @for (c of ahorrosLetras(); track c.id) {
            <article class="tarjeta">
              <h3>Crédito #{{ c.id }} {{ c.modalidad ?? '' }}</h3>
              <p class="importe">{{ c.ahorroLetras | currency: 'USD' }}</p>
            </article>
          }
        </div>
      </section>
    }

    <section class="tarjeta estrecho seccion-accion">
      <h2>Nuevo ahorro</h2>
      <form (ngSubmit)="crear()">
        <label>Tipo
          <select name="tipo" [(ngModel)]="tipo">
            <option value="NORMAL">Ahorro normal</option>
            <option value="PLAZO_FIJO">Plazo fijo</option>
          </select>
        </label>
        @if (tipo === 'NORMAL') {
          <p class="suave"><strong>Ahorro normal:</strong> retire su saldo cuando quiera. Gana {{ productos()?.tasaNormal | number: '1.0-4' }} % anual, calculado cada día.</p>
        } @else {
          <p class="suave"><strong>Plazo fijo:</strong> mayor interés, sin retiros hasta terminar el contrato.</p>
        }
        <label>Nombre <input name="nombre" maxlength="100" [(ngModel)]="nombre" /></label>
        @if (tipo === 'PLAZO_FIJO') {
          <label>Importe (USD) <input type="number" min="0" step="1" name="importe" [(ngModel)]="importe" /></label>
          <label>Plazo
            <select name="plazo" [(ngModel)]="plazo">
              @for (o of productos()?.plazos; track o.meses) {
                <option [ngValue]="o.meses">{{ o.meses === 24 ? '2 años' : o.meses + ' meses' }}: {{ o.tasaAnual | number: '1.0-4' }} % anual</option>
              }
            </select>
          </label>
        }
        <button type="submit" [disabled]="!nombre.trim() || (tipo === 'PLAZO_FIJO' && (!importe || !plazo))">Crear</button>
      </form>
    </section>
  `,
})
export class Ahorros implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly ahorros = signal<Ahorro[]>([]);
  protected readonly ahorrosLetras = signal<Credito[]>([]);
  protected readonly productos = signal<ProductosAhorro | null>(null);
  protected readonly activos = computed(() => this.ahorros().filter((a) => a.estado === 'ACTIVA'));
  protected readonly totalLetras = computed(() => this.ahorrosLetras().reduce((t, c) => t + (c.ahorroLetras ?? 0), 0));
  protected readonly total = computed(() => this.activos().reduce((t, a) => t + a.saldo, 0) + this.totalLetras());
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  protected tipo = 'NORMAL';
  protected nombre = '';
  protected importe: number | null = null;
  protected plazo: number | null = null;
  protected importes: Record<number, number | null> = {};

  // Una clave por intento: se renueva solo tras un éxito, para que reintentar no duplique.
  private clave: string = crypto.randomUUID();

  constructor() {
    alCambiar(() => {
      this.cargar();
      this.cargarLetras();
    });
  }

  ngOnInit(): void {
    this.cargar();
    this.cargarLetras();
    this.api.productosAhorro().subscribe({ next: (p) => this.productos.set(p), error: (e) => this.error.set(mensajeDeError(e)) });
  }

  /** Solo los créditos vigentes: al terminar, ese dinero ya volvió a la cuenta principal. */
  private cargarLetras(): void {
    this.api.misCreditos().subscribe({
      next: (c) => this.ahorrosLetras.set(c.filter((x) => x.familia === 'LETRAS' && x.estado === 'ACTIVO')),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected totalDe(tipo: string): number {
    return this.activos().filter((a) => a.tipo === tipo).reduce((t, a) => t + a.saldo, 0);
  }

  protected crear(): void {
    const accion = this.tipo === 'NORMAL'
      ? this.api.crearAhorroNormal(this.nombre.trim())
      : this.api.crearPlazoFijo(this.nombre.trim(), this.importe!, this.plazo!, this.clave);
    this.ejecutar(accion, 'Ahorro creado.', () => {
      this.nombre = '';
      this.importe = null;
      this.plazo = null;
    });
  }

  protected mover(a: Ahorro, tipo: 'deposito' | 'retiro'): void {
    const importe = this.importes[a.id]!;
    const accion = tipo === 'deposito'
      ? this.api.depositarAhorro(a.id, importe, this.clave)
      : this.api.retirarAhorro(a.id, importe, this.clave);
    this.ejecutar(accion, tipo === 'deposito' ? 'Depósito registrado.' : 'Retiro registrado.', () => {
      this.importes[a.id] = null;
    });
  }

  protected retirarInteres(a: Ahorro): void {
    this.ejecutar(this.api.retirarInteresAhorro(a.id, this.clave), 'Interés enviado a su cuenta principal.');
  }

  protected liquidar(a: Ahorro): void {
    this.ejecutar(this.api.retirarAhorro(a.id, null, this.clave), 'Plazo fijo liquidado en su cuenta principal.');
  }

  private ejecutar(accion: Observable<unknown>, aviso: string, alTerminar?: () => void): void {
    this.error.set(null);
    this.aviso.set(null);
    accion.subscribe({
      next: () => {
        this.clave = crypto.randomUUID();
        this.aviso.set(aviso);
        alTerminar?.();
        this.cargar();
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  private cargar(): void {
    this.api.misAhorros().subscribe({
      next: (a) => this.ahorros.set(a),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
