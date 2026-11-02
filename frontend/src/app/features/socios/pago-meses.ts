import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import { Router } from '@angular/router';

import { ApiService, MesAporte } from '../../core/services/api';
import { llevarADepositarSiFaltaSaldo, mensajeDeError } from '../../shared/utils/errores';

const MESES = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];

/** Elegir y pagar uno o varios meses del año, del aporte mensual o de caja chica. Sale de la cuenta principal. */
@Component({
  selector: 'app-pago-meses',
  imports: [CurrencyPipe],
  template: `
    @if (meses().length) {
      <div class="meses" role="group" [attr.aria-label]="'Meses de ' + nombre()">
        @for (m of meses(); track m.periodo) {
          <label [class.pagado]="pagado(m)">
            <input type="checkbox" [disabled]="pagado(m) || enviando()" [checked]="pagado(m) || elegidos().has(m.periodo)" (change)="alternar(m.periodo)" />
            {{ etiqueta(m.periodo) }}@if (pagado(m)) { ✓ }
          </label>
        }
      </div>
      @if (pendientes().length) {
        <div class="acciones mb-2">
          <button type="button" class="enlace-boton" (click)="elegirHastaHoy()">Elegir los atrasados y este mes</button>
          <button type="button" class="enlace-boton" (click)="elegirTodos()">Elegir todo el año</button>
        </div>
        <button type="button" [disabled]="!elegidos().size || enviando()" (click)="pagar()">
          Pagar {{ elegidos().size }} {{ elegidos().size === 1 ? 'mes' : 'meses' }}: {{ elegidos().size * importe() | currency: 'USD' }}
        </button>
      } @else {
        <p class="suave">Todo el año está pagado.</p>
      }
      @if (error()) { <p class="error mt-2" role="alert">{{ error() }}</p> }
    }
  `,
})
export class PagoMeses implements OnInit {
  readonly tipo = input.required<'OBLIGATORIO' | 'CAJA_CHICA'>();
  readonly importe = input.required<number>();
  readonly pagadoAhora = output<string>();

  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly meses = signal<MesAporte[]>([]);
  protected readonly elegidos = signal(new Set<string>());
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly pendientes = computed(() => this.meses().filter((m) => !this.pagado(m)));
  protected readonly nombre = computed(() => (this.tipo() === 'OBLIGATORIO' ? 'aporte mensual' : 'caja chica'));
  private clave: string = crypto.randomUUID();

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.api.miCalendario().subscribe({ next: (m) => this.meses.set(m), error: (e) => this.error.set(mensajeDeError(e)) });
  }

  protected pagado(m: MesAporte): boolean {
    return this.tipo() === 'OBLIGATORIO' ? m.obligatorioPagado : m.cajaChicaPagado;
  }

  protected etiqueta(periodo: string): string {
    return MESES[Number(periodo.slice(5, 7)) - 1];
  }

  protected alternar(periodo: string): void {
    const nuevos = new Set(this.elegidos());
    if (nuevos.has(periodo)) {
      nuevos.delete(periodo);
    } else {
      nuevos.add(periodo);
    }
    this.elegidos.set(nuevos);
  }

  protected elegirTodos(): void {
    this.elegidos.set(new Set(this.pendientes().map((m) => m.periodo)));
  }

  protected elegirHastaHoy(): void {
    const hoy = new Date().toISOString().slice(0, 7);
    this.elegidos.set(new Set(this.pendientes().filter((m) => m.periodo <= hoy).map((m) => m.periodo)));
  }

  protected pagar(): void {
    const periodos = [...this.elegidos()].sort();
    this.enviando.set(true);
    this.error.set(null);
    this.api.pagarPeriodos(this.tipo(), periodos, this.clave).subscribe({
      next: () => {
        this.clave = crypto.randomUUID();
        this.elegidos.set(new Set());
        this.enviando.set(false);
        this.pagadoAhora.emit(`${periodos.length === 1 ? 'Mes pagado' : periodos.length + ' meses pagados'} de ${this.nombre()}.`);
        this.cargar();
      },
      error: (e) => {
        this.enviando.set(false);
        if (!llevarADepositarSiFaltaSaldo(e, this.router, `pagar ${this.nombre()}`)) {
          this.error.set(mensajeDeError(e));
        }
      },
    });
  }
}
