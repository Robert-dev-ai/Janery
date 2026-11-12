import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';

import { Ahorro, ApiService, Credito, ProductosAhorro } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { DialogoService } from '../../shared/components/dialogo';
import { llevarADepositarSiFaltaSaldo, mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-ahorros',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, FormsModule],
  template: `
    <div class="encabezado-pagina">
      <h1>Ahorros</h1>
      <button type="button" (click)="abrirNuevo('NORMAL')">+ Abrir nuevo ahorro</button>
    </div>
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
      <h2 class="seccion-titulo">Ahorros normales</h2>
      <p class="suave"><strong>Ahorro normal:</strong> deposite y retire cuando quiera. Gana {{ productos()?.tasaNormal | number: '1.0-4' }} % anual: el interés se calcula cada día, va aparte de su saldo y <strong>se libera el día 1 de cada mes</strong>. Desde ese día lo puede pasar a su cuenta principal.</p>
      <div class="rejilla-dos">
        @for (a of normales(); track a.id) {
          <article class="tarjeta">
            <div class="encabezado-tarjeta">
              @if (renombrando() === a.id) {
                <form class="acciones w-full" (ngSubmit)="renombrar(a)">
                  <label class="flex-1">Nombre <input name="n{{ a.id }}" maxlength="100" [(ngModel)]="nuevoNombre" /></label>
                  <button type="submit" [disabled]="enviando() || !nuevoNombre.trim()">Guardar</button>
                  <button type="button" class="secundario" (click)="renombrando.set(null)">Cancelar</button>
                </form>
              } @else {
                <h3 class="m-0">{{ a.nombre }} <span class="codigo">{{ a.codigo }}</span></h3>
                <button type="button" class="enlace-boton" (click)="empezarRenombrar(a)">Cambiar nombre</button>
              }
            </div>
            <p class="importe">{{ a.saldo | currency: 'USD' }}</p>
            <div class="importes">
              <div>Interés de este mes (se libera el {{ a.proximaLiberacion | date: 'dd/MM' : 'UTC' }})<p class="importe">{{ a.interesAcumulado | currency: 'USD' }}</p></div>
              <div>Interés liberado, para retirar<p class="importe">{{ a.interesDisponible | currency: 'USD' }}</p></div>
            </div>
            <button type="button" class="secundario" [disabled]="enviando() || a.interesDisponible <= 0" (click)="retirarInteres(a)">
              {{ a.interesDisponible > 0 ? 'Pasar el interés a mi cuenta' : 'El interés se libera el ' + (a.proximaLiberacion | date: 'dd/MM/yyyy' : 'UTC') }}
            </button>
            <form class="acciones" (ngSubmit)="mover(a, 'deposito')">
              <label>Importe (USD)
                <input type="number" min="0" step="any" name="i{{ a.id }}" [(ngModel)]="importes[a.id]" />
              </label>
              <button type="submit" [disabled]="enviando() || !importes[a.id]">Depositar</button>
              <button type="button" class="secundario" [disabled]="enviando() || !importes[a.id]" (click)="mover(a, 'retiro')">Retirar</button>
            </form>
            @if (a.saldo > 0 || a.interesDisponible > 0) {
              <p class="suave mb-0">Para eliminar este ahorro, primero pase todo su dinero a su cuenta principal.</p>
            } @else {
              <button type="button" class="enlace-discreto" [disabled]="enviando()" (click)="eliminar(a)">Eliminar este ahorro</button>
            }
          </article>
        } @empty {
          <div class="vacio lg:col-span-2">
            <p>No tiene ahorros normales.</p>
            <button type="button" (click)="abrirNuevo('NORMAL')">Ahorrar</button>
          </div>
        }
      </div>
    </section>

    <section class="seccion">
      <h2 class="seccion-titulo">Plazo fijo</h2>
      <p class="suave"><strong>Plazo fijo:</strong> mayor interés, sin retiros hasta terminar el contrato. El interés se paga al final.</p>
      <div class="rejilla-dos">
        @for (a of plazos(); track a.id) {
          <article class="tarjeta">
            <div class="encabezado-tarjeta">
              @if (renombrando() === a.id) {
                <form class="acciones w-full" (ngSubmit)="renombrar(a)">
                  <label class="flex-1">Nombre <input name="n{{ a.id }}" maxlength="100" [(ngModel)]="nuevoNombre" /></label>
                  <button type="submit" [disabled]="enviando() || !nuevoNombre.trim()">Guardar</button>
                  <button type="button" class="secundario" (click)="renombrando.set(null)">Cancelar</button>
                </form>
              } @else {
                <h3 class="m-0">{{ a.nombre }} <span class="codigo">{{ a.codigo }}</span></h3>
                <button type="button" class="enlace-boton" (click)="empezarRenombrar(a)">Cambiar nombre</button>
              }
            </div>
            <p class="importe">{{ a.saldo | currency: 'USD' }}</p>
            <p class="suave">A {{ a.plazoMeses }} meses, al {{ a.tasaAnual | number: '1.0-4' }} % anual. Termina el {{ a.venceEn | date: 'mediumDate' : 'UTC' }}.</p>
            <div class="importes">
              <div>Interés a recibir<p class="importe">{{ a.interesAlVencimiento | currency: 'USD' }}</p></div>
              <div>Total al terminar<p class="importe">{{ a.saldo + (a.interesAlVencimiento ?? 0) | currency: 'USD' }}</p></div>
            </div>
            <button type="button" [disabled]="enviando() || !a.retirable" (click)="liquidar(a)">
              {{ a.retirable ? 'Retirar todo a mi cuenta' : 'Disponible al terminar el contrato' }}
            </button>
          </article>
        } @empty {
          <div class="vacio lg:col-span-2">
            <p>No tiene ahorros a plazo fijo.</p>
            <button type="button" (click)="abrirNuevo('PLAZO_FIJO')">Ahorrar a plazo fijo</button>
          </div>
        }
      </div>
    </section>

    <section class="seccion">
      <h2 class="seccion-titulo">Ahorro letras</h2>
      <p class="suave"><strong>Ahorro letras:</strong> se acumula con cada letra de su crédito y vuelve a su cuenta principal al terminarlo. También puede usarlo en la última letra.</p>
      <div class="rejilla-dos">
        @for (c of ahorrosLetras(); track c.id) {
          <article class="tarjeta">
            <h3>Crédito {{ c.codigo }} {{ c.modalidad ?? '' }}</h3>
            <p class="importe">{{ c.ahorroLetras | currency: 'USD' }}</p>
          </article>
        } @empty {
          <p class="vacio lg:col-span-2">No tiene ahorro letras: se forma solo al pagar las letras de un crédito.</p>
        }
      </div>
    </section>

    <section #nuevo id="nuevo-ahorro" class="tarjeta estrecho seccion-accion">
      <h2>Abrir nuevo ahorro</h2>
      <form (ngSubmit)="crear()">
        <label>Tipo
          <select name="tipo" [ngModel]="tipo" (ngModelChange)="cambiarTipo($event)">
            <option value="NORMAL">Ahorro normal</option>
            <option value="PLAZO_FIJO">Plazo fijo</option>
          </select>
        </label>
        @if (tipo === 'NORMAL') {
          <p class="suave"><strong>Ahorro normal:</strong> retire su saldo cuando quiera. Gana {{ productos()?.tasaNormal | number: '1.0-4' }} % anual; el interés se libera el día 1 de cada mes.</p>
        } @else {
          <p class="suave"><strong>Plazo fijo:</strong> mayor interés, sin retiros hasta terminar el contrato.</p>
        }
        <label>Nombre (puede cambiarlo) <input name="nombre" maxlength="100" [(ngModel)]="nombre" /></label>
        @if (tipo === 'PLAZO_FIJO') {
          <label>Importe (USD) <input type="number" min="0" step="any" name="importe" [(ngModel)]="importe" /></label>
          <label>Plazo
            <select name="plazo" [(ngModel)]="plazo">
              @for (o of productos()?.plazos; track o.meses) {
                <option [ngValue]="o.meses">{{ o.meses === 24 ? '2 años' : o.meses + ' meses' }}: {{ o.tasaAnual | number: '1.0-4' }} % anual</option>
              }
            </select>
          </label>
        }
        <button type="submit" [disabled]="enviando() || !nombre.trim() || (tipo === 'PLAZO_FIJO' && (!importe || !plazo))">{{ enviando() ? 'Abriendo…' : 'Abrir ahorro' }}</button>
      </form>
    </section>
  `,
})
export class Ahorros implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly dialogo = inject(DialogoService);
  private readonly nuevo = viewChild.required<ElementRef<HTMLElement>>('nuevo');

  protected readonly ahorros = signal<Ahorro[]>([]);
  protected readonly ahorrosLetras = signal<Credito[]>([]);
  protected readonly productos = signal<ProductosAhorro | null>(null);
  protected readonly activos = computed(() => this.ahorros().filter((a) => a.estado === 'ACTIVA'));
  protected readonly normales = computed(() => this.activos().filter((a) => a.tipo === 'NORMAL'));
  protected readonly plazos = computed(() => this.activos().filter((a) => a.tipo === 'PLAZO_FIJO'));
  protected readonly totalLetras = computed(() => this.ahorrosLetras().reduce((t, c) => t + (c.ahorroLetras ?? 0), 0));
  protected readonly total = computed(() => this.activos().reduce((t, a) => t + a.saldo, 0) + this.totalLetras());
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  /** Mientras una operación está en curso: evita el doble clic y deja ver que se está procesando. */
  protected readonly enviando = signal(false);
  protected readonly renombrando = signal<number | null>(null);

  protected tipo = 'NORMAL';
  protected nombre = '';
  protected nuevoNombre = '';
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

  /** Lleva al formulario con el tipo elegido y un nombre propuesto. */
  protected abrirNuevo(tipo: string): void {
    this.cambiarTipo(tipo);
    this.nuevo().nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
    this.nuevo().nativeElement.querySelector<HTMLInputElement>('input[name=nombre]')?.focus({ preventScroll: true });
  }

  protected cambiarTipo(tipo: string): void {
    const propuesto = this.nombrePropuesto(this.tipo);
    this.tipo = tipo;
    if (!this.nombre.trim() || this.nombre === propuesto) {
      this.nombre = this.nombrePropuesto(tipo);
    }
  }

  private nombrePropuesto(tipo: string): string {
    const cantidad = this.activos().filter((a) => a.tipo === tipo).length + 1;
    return tipo === 'NORMAL' ? `Mi ahorro ${cantidad}` : `Mi plazo fijo ${cantidad}`;
  }

  protected crear(): void {
    const accion = this.tipo === 'NORMAL'
      ? this.api.crearAhorroNormal(this.nombre.trim(), this.clave)
      : this.api.crearPlazoFijo(this.nombre.trim(), this.importe!, this.plazo!, this.clave);
    this.ejecutar(accion, 'Ahorro abierto.', 'abrir este plazo fijo', () => {
      this.importe = null;
      this.plazo = null;
      this.nombre = '';
    });
  }

  protected empezarRenombrar(a: Ahorro): void {
    this.nuevoNombre = a.nombre;
    this.renombrando.set(a.id);
  }

  protected renombrar(a: Ahorro): void {
    this.ejecutar(this.api.renombrarAhorro(a.id, this.nuevoNombre.trim()), 'Nombre cambiado.', '', () => this.renombrando.set(null));
  }

  protected async eliminar(a: Ahorro): Promise<void> {
    const pendiente = a.interesAcumulado > 0 ? ` El interés de este mes (${a.interesAcumulado} USD) aún no se libera y se perderá.` : '';
    if (await this.dialogo.confirmar(`¿Eliminar el ahorro «${a.nombre}»?${pendiente}`, 'Eliminar ahorro', true)) {
      this.ejecutar(this.api.eliminarAhorro(a.id), 'Ahorro eliminado.', '');
    }
  }

  protected mover(a: Ahorro, tipo: 'deposito' | 'retiro'): void {
    const importe = this.importes[a.id]!;
    const accion = tipo === 'deposito'
      ? this.api.depositarAhorro(a.id, importe, this.clave)
      : this.api.retirarAhorro(a.id, importe, this.clave);
    this.ejecutar(accion, tipo === 'deposito' ? 'Depósito registrado.' : 'Retiro registrado.', 'depositar en su ahorro', () => {
      this.importes[a.id] = null;
    });
  }

  protected retirarInteres(a: Ahorro): void {
    this.ejecutar(this.api.retirarInteresAhorro(a.id, this.clave), 'Interés enviado a su cuenta principal.', '');
  }

  protected liquidar(a: Ahorro): void {
    this.ejecutar(this.api.retirarAhorro(a.id, null, this.clave), 'Plazo fijo liquidado en su cuenta principal.', '');
  }

  private ejecutar(accion: Observable<unknown>, aviso: string, para: string, alTerminar?: () => void): void {
    if (this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    this.aviso.set(null);
    accion.subscribe({
      next: () => {
        this.enviando.set(false);
        this.clave = crypto.randomUUID();
        this.aviso.set(aviso);
        alTerminar?.();
        this.cargar();
      },
      error: (e) => {
        this.enviando.set(false);
        if (!para || !llevarADepositarSiFaltaSaldo(e, this.router, para)) {
          this.error.set(mensajeDeError(e));
        }
      },
    });
  }

  private cargar(): void {
    this.api.misAhorros().subscribe({
      next: (a) => {
        this.ahorros.set(a);
        if (!this.nombre) {
          this.nombre = this.nombrePropuesto(this.tipo);
        }
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
