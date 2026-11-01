import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, Observable } from 'rxjs';

import { Aporte, ApiService, PeriodoParticipacion, ResumenSocio } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { mensajeDeError } from '../../shared/utils/errores';

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
  imports: [CurrencyPipe, DatePipe, DecimalPipe, FormsModule],
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
                <input type="number" min="0.01" step="0.01" name="retiro" [(ngModel)]="importeRetiro" />
              </label>
              <button type="submit" [disabled]="!importeRetiro || importeRetiro <= 0">Retirar</button>
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
          @if (r.obligatorioDelMesPagado) {
            <p class="suave">{{ r.periodoActual }} pagado.</p>
          } @else {
            <button type="button" (click)="obligatorio()">Pagar {{ r.periodoActual }}: {{ r.aporteObligatorio | currency: 'USD' }}</button>
          }
        </section>
        <section class="tarjeta">
          <h2>Aportes personales</h2>
          <p class="importe">{{ r.totalPersonal | currency: 'USD' }}</p>
          <form class="acciones" (ngSubmit)="personal()">
            <label>Importe (USD)
              <input type="number" min="0.01" step="0.01" name="personal" [(ngModel)]="importe" />
            </label>
            <button type="submit" [disabled]="!importe || importe <= 0">Aportar</button>
          </form>
        </section>
        <section class="tarjeta">
          <h2>Caja chica</h2>
          <p class="importe">{{ r.totalCajaChica | currency: 'USD' }}</p>
          <p class="suave">Fondo aparte: no aumenta su participación.</p>
          @if (r.cajaChicaDelMesPagado) {
            <p class="suave">{{ r.periodoActual }} pagado.</p>
          } @else {
            <button type="button" (click)="cajaChica()">Pagar {{ r.periodoActual }}: {{ r.aporteCajaChica | currency: 'USD' }}</button>
          }
        </section>
      </div>
    } @else if (!error()) {
      <p role="status">Cargando…</p>
    }

    <h2>Evolución mensual</h2>
    @for (p of periodos(); track p.periodo) {
      <section class="tarjeta">
        <h3>{{ p.periodo }}</h3>
        <p>Valor al inicio: {{ p.valorInicial | currency: 'USD' }}</p>
        @if (p.variaciones.length) {
          <div class="tabla">
            <table>
              <thead><tr><th>Fecha</th><th>Concepto</th><th class="num">Variación</th></tr></thead>
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

  protected readonly resumen = signal<ResumenSocio | null>(null);
  protected readonly periodos = signal<PeriodoParticipacion[]>([]);
  private readonly aportes = signal<Aporte[]>([]);
  protected readonly obligatorios = computed(() => this.aportes().filter((a) => a.tipo === 'OBLIGATORIO'));
  protected readonly personales = computed(() => this.aportes().filter((a) => a.tipo === 'PERSONAL'));
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

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

  protected obligatorio(): void {
    this.ejecutar(this.api.aportar('OBLIGATORIO', null, this.clave), 'Aporte mensual registrado.');
  }

  protected personal(): void {
    this.ejecutar(this.api.aportar('PERSONAL', this.importe, this.clave), 'Aporte personal registrado.', () => {
      this.importe = null;
    });
  }

  protected cajaChica(): void {
    this.ejecutar(this.api.aportarCajaChica(this.clave), 'Aporte a caja chica registrado.');
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
    forkJoin([this.api.miResumenSocio(), this.api.miParticipacion(), this.api.misAportes()]).subscribe({
      next: ([resumen, periodos, aportes]) => {
        this.resumen.set(resumen);
        this.periodos.set(periodos);
        this.aportes.set(aportes);
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
