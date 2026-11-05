import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { forkJoin, Observable } from 'rxjs';

import { Aporte, ApiService, PeriodoParticipacion, ResumenSocio } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { llevarADepositarSiFaltaSaldo, mensajeDeError } from '../../shared/utils/errores';
import { PagoMeses } from './pago-meses';

const TIPOS: Record<string, string> = {
  APORTE_INICIAL: 'Aporte inicial',
  APORTE_OBLIGATORIO: 'Aporte mensual',
  APORTE_PERSONAL: 'Aporte personal',
  APORTE_COMPRA: 'Compra de capital',
  APORTE_VENTA: 'Venta de capital',
  RETIRO_GANANCIA: 'Retiro de ganancias',
  DESCUENTO_OBLIGACION: 'Descuento por obligaciones',
  GANANCIA: 'Ganancia',
  PERDIDA: 'Pérdida',
  RESERVA: 'Fondo de reserva',
  DISTRIBUCION: 'Ganancia distribuida',
};

@Component({
  selector: 'app-inversiones',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, FormsModule, PagoMeses],
  template: `
    <h1>Mi fondo personal</h1>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }

    @if (resumen(); as r) {
      <div class="importes">
        <section class="tarjeta">
          <h2>Valor total</h2>
          <p class="importe">{{ r.valorActual | currency: 'USD' }}</p>
          <p class="suave">{{ r.porcentaje | number: '1.2-4' }} % de la participación</p>
        </section>
        <section class="tarjeta">
          <h2>Rendimiento de este mes</h2>
          @if (r.rendimientoMes !== null) {
            <p class="importe" [class.positivo]="r.rendimientoMes > 0" [class.negativo]="r.rendimientoMes < 0">{{ r.rendimientoMes > 0 ? '+' : '' }}{{ r.rendimientoMes | number: '1.2-2' }} %</p>
          } @else {
            <p class="importe">—</p>
          }
          <p class="suave">Sube con cada ingreso de Janery y baja con cada gasto. Al cerrar el mes, lo ganado cuenta para los repartos siguientes.</p>
        </section>
        <section class="tarjeta">
          <h2>Aportes iniciales</h2>
          <p class="importe">{{ r.totalInicial | currency: 'USD' }}</p>
        </section>
        <section class="tarjeta">
          <h2>Ganancias</h2>
          <p class="importe">{{ r.ganancias | currency: 'USD' }}</p>
          <p class="suave">Ya descontados el fondo de reserva y las pérdidas.</p>
          <p>Disponible para retirar: <strong>{{ r.gananciasDisponibles | currency: 'USD' }}</strong></p>
          <p class="suave">Las ganancias de cada año se liberan el 1 de enero siguiente, cuando el presidente lo autoriza. Van a su cuenta principal.</p>
          @if (r.obligacionesPendientes > 0) {
            <p class="error">Obligaciones vencidas: {{ r.obligacionesPendientes | currency: 'USD' }}.</p>
          }
          @if (r.gananciasDisponibles > 0 && !r.retiroAutorizado) {
            <p class="suave">Su retiro aún no está autorizado.</p>
          }
          @if (r.gananciasDisponibles > 0 && r.retiroAutorizado) {
            <form class="acciones" (ngSubmit)="retirar()">
              <label>Importe (USD)
                <input type="number" min="0" step="1" name="retiro" [(ngModel)]="importeRetiro" />
              </label>
              <button type="submit" [disabled]="enviando() || !importeRetiro || importeRetiro <= 0">Retirar</button>
            </form>
          }
        </section>
        @if (r.compraventas !== 0) {
          <section class="tarjeta">
            <h2>Compraventas de capital</h2>
            <p class="importe">{{ r.compraventas | currency: 'USD' }}</p>
          </section>
        }
        <section class="tarjeta">
          <h2>Aportes mensuales</h2>
          <p class="importe">{{ r.totalObligatorio | currency: 'USD' }}</p>
          <p class="suave">{{ r.aporteObligatorio | currency: 'USD' }} cada mes. Puede pagar varios meses, o todo el año, de una vez.</p>
          <app-pago-meses tipo="OBLIGATORIO" [importe]="r.aporteObligatorio" (pagadoAhora)="pagado($event)" />
        </section>
        <section class="tarjeta">
          <h2>Aportes personales</h2>
          <p class="importe">{{ r.totalPersonal | currency: 'USD' }}</p>
          <form class="acciones" (ngSubmit)="personal()">
            <label>Importe (USD)
              <input type="number" min="0" step="1" name="personal" [(ngModel)]="importe" />
            </label>
            <button type="submit" [disabled]="enviando() || !importe || importe <= 0">Aportar</button>
          </form>
        </section>
        <section class="tarjeta">
          <h2>Caja chica</h2>
          <p class="importe">{{ r.totalCajaChica | currency: 'USD' }}</p>
          <p class="suave">Fondo aparte: no aumenta su participación. {{ r.aporteCajaChica | currency: 'USD' }} cada mes.</p>
          <app-pago-meses tipo="CAJA_CHICA" [importe]="r.aporteCajaChica" (pagadoAhora)="pagado($event)" />
        </section>
      </div>
    } @else if (!error()) {
      <p class="cargando" role="status">Cargando…</p>
    }

    <h2>Evolución mensual</h2>
    @for (p of periodos(); track p.periodo) {
      <section class="tarjeta">
        <div class="encabezado-tarjeta">
          <h3 class="m-0">{{ p.periodo }}</h3>
          @if (p.rendimiento !== null && p.rendimiento !== 0) {
            <span class="estado" [class.estado-exito]="p.rendimiento > 0" [class.estado-peligro]="p.rendimiento < 0">{{ p.rendimiento > 0 ? '+' : '' }}{{ p.rendimiento | number: '1.2-2' }} %</span>
          }
        </div>
        <p>Valor al inicio: {{ p.valorInicial | currency: 'USD' }}</p>
        @if (p.variaciones.length) {
          <div class="tabla">
            <table>
              <thead><tr><th scope="col">Fecha</th><th scope="col">Concepto</th><th scope="col" class="num">Variación</th></tr></thead>
              <tbody>
                @for (v of p.variaciones; track $index) {
                  <tr>
                    <td>{{ v.fecha | date: 'mediumDate' : 'UTC' }}</td>
                    <td>{{ tipo(v.tipo) }}</td>
                    <td class="num">{{ v.importe > 0 ? '+' : '−' }}{{ abs(v.importe) | currency: 'USD' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        } @else {
          <p class="suave">Sin variaciones.</p>
        }
        <p><strong>Valor al cierre: {{ p.valorFinal | currency: 'USD' }}</strong></p>
      </section>
    }

    @if (obligatorios().length || personales().length) {
      <h2>Mis aportes</h2>
      <div class="importes">
        <section class="tarjeta">
          <h3>Mensuales</h3>
          @for (a of obligatorios(); track a.id) { <p>{{ a.periodo }}: {{ a.importe | currency: 'USD' }}</p> } @empty { <p class="suave">Sin aportes.</p> }
        </section>
        <section class="tarjeta">
          <h3>Personales</h3>
          @for (a of personales(); track a.id) { <p>{{ a.fecha | date: 'mediumDate' : 'America/Guayaquil' }}: {{ a.importe | currency: 'USD' }}</p> } @empty { <p class="suave">Sin aportes.</p> }
        </section>
      </div>
    }
  `,
})
export class Inversiones implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly resumen = signal<ResumenSocio | null>(null);
  protected readonly periodos = signal<PeriodoParticipacion[]>([]);
  private readonly aportes = signal<Aporte[]>([]);
  protected readonly obligatorios = computed(() => this.aportes().filter((a) => a.tipo === 'OBLIGATORIO'));
  protected readonly personales = computed(() => this.aportes().filter((a) => a.tipo === 'PERSONAL'));
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  /** Mientras una operación está en curso: evita el doble clic y deja ver que se está procesando. */
  protected readonly enviando = signal(false);

  protected importe: number | null = null;
  protected importeRetiro: number | null = null;

  // Una clave por intento: se renueva solo tras un éxito, para que reintentar no duplique.
  private clave: string = crypto.randomUUID();

  constructor() {
    alCambiar(() => this.cargar());
  }

  ngOnInit(): void {
    this.cargar();
  }

  protected tipo(codigo: string): string {
    return TIPOS[codigo] ?? codigo;
  }

  protected abs(valor: number): number {
    return Math.abs(valor);
  }

  protected retirar(): void {
    this.ejecutar(this.api.retirarGanancias(this.importeRetiro!, this.clave), 'Ganancias enviadas a su cuenta principal.', () => {
      this.importeRetiro = null;
    });
  }

  protected pagado(aviso: string): void {
    this.error.set(null);
    this.aviso.set(aviso);
    this.cargar();
  }

  protected personal(): void {
    this.ejecutar(this.api.aportar('PERSONAL', this.importe, this.clave), 'Aporte personal registrado.', () => {
      this.importe = null;
    });
  }

  private ejecutar(accion: Observable<unknown>, aviso: string, alTerminar?: () => void): void {
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
        if (!llevarADepositarSiFaltaSaldo(e, this.router, 'este aporte')) {
          this.error.set(mensajeDeError(e));
        }
      },
    });
  }

  private cargar(): void {
    forkJoin([this.api.miResumenSocio(), this.api.miParticipacion(), this.api.misAportes()]).subscribe({
      next: ([resumen, periodos, aportes]) => {
        this.resumen.set(resumen);
        this.periodos.set(periodos);
        this.aportes.set(aportes);
      },
      error: (e) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }
}
