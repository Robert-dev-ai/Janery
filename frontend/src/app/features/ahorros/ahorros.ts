import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';

import { Ahorro, ApiService } from '../../core/services/api';
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

    @for (a of ahorros(); track a.id) {
      @if (a.estado === 'ACTIVA') {
        <section class="tarjeta">
          <h2>{{ a.nombre }}</h2>
          <p class="importe">{{ a.saldo | currency: 'USD' }}</p>
          @if (a.tipo === 'PLAZO_FIJO') {
            <p class="suave">Plazo fijo al {{ a.tasaAnual | number: '1.0-4' }} % anual. Vence el {{ a.venceEn | date: 'mediumDate' : 'UTC' }};
              interés al vencer: {{ a.interesAlVencimiento | currency: 'USD' }}.</p>
            <button type="button" [disabled]="!a.retirable" (click)="liquidar(a)">
              {{ a.retirable ? 'Retirar todo' : 'Aún no vence' }}
            </button>
          } @else {
            <p class="suave">Ahorro normal al {{ a.tasaAnual | number: '1.0-4' }} % anual, abonado cada mes.</p>
            <form class="acciones" (ngSubmit)="mover(a, 'deposito')">
              <label>Importe (USD)
                <input type="number" min="0.01" step="0.01" name="i{{ a.id }}" [(ngModel)]="importes[a.id]" />
              </label>
              <button type="submit" [disabled]="!importes[a.id]">Depositar</button>
              <button type="button" class="secundario" [disabled]="!importes[a.id]" (click)="mover(a, 'retiro')">Retirar</button>
            </form>
          }
        </section>
      }
    } @empty {
      <p>Aún no tiene ahorros.</p>
    }

    <section class="tarjeta estrecho">
      <h2>Nuevo ahorro</h2>
      <form (ngSubmit)="crear()">
        <label>Tipo
          <select name="tipo" [(ngModel)]="tipo">
            <option value="NORMAL">Normal: retire cuando quiera</option>
            <option value="PLAZO_FIJO">Plazo fijo: mayor interés, sin retiros antes de vencer</option>
          </select>
        </label>
        <label>Nombre <input name="nombre" maxlength="100" [(ngModel)]="nombre" /></label>
        @if (tipo === 'PLAZO_FIJO') {
          <label>Importe (USD) <input type="number" min="0.01" step="0.01" name="importe" [(ngModel)]="importe" /></label>
          <label>Plazo (meses) <input type="number" step="1" name="plazo" [(ngModel)]="plazo" /></label>
        }
        <button type="submit" [disabled]="!nombre.trim() || (tipo === 'PLAZO_FIJO' && (!importe || !plazo))">Crear</button>
      </form>
    </section>
  `,
})
export class Ahorros implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly ahorros = signal<Ahorro[]>([]);
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
    alCambiar(() => this.cargar());
  }

  ngOnInit(): void {
    this.cargar();
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
