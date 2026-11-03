import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AuthService } from '../../core/auth/auth';
import { ApiService, CajaChica, SocioCajaChica } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { Paginador } from '../../shared/components/paginador';
import { nombreConcepto } from '../../shared/utils/etiquetas';
import { mensajeDeError } from '../../shared/utils/errores';
import { PagoMeses } from '../socios/pago-meses';

const MESES = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];

/** Quien administra caja chica registra gastos y aportes recibidos en persona; los socios la consultan y pagan lo suyo. */
@Component({
  selector: 'app-area-caja-chica',
  imports: [CurrencyPipe, DatePipe, FormsModule, PagoMeses, Paginador],
  template: `
    <h1>Caja chica</h1>
    <p class="suave">Fondo independiente de la caja principal, con el aporte mensual de cada socio.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }
    @if (datos(); as d) {
      <section class="total">
        <div>
          <h2>Saldo de caja chica</h2>
          <p class="total-importe">{{ d.saldo | currency: 'USD' }}</p>
        </div>
      </section>

      @if (esSocio) {
        <section class="seccion">
          <h2 class="seccion-titulo">Pagar mi caja chica</h2>
          <p class="suave">Elija uno o varios meses: puede pagar por adelantado todo el año. Sale de su cuenta principal.</p>
          <app-pago-meses tipo="CAJA_CHICA" [importe]="aporte()" (pagadoAhora)="pagado($event)" />
        </section>
      }

      @if (administra) {
        <div class="rejilla-dos mb-6">
          <form class="tarjeta seccion-accion" (ngSubmit)="recibir()">
            <h2>Recibir un aporte en persona</h2>
            <p class="suave">Si un socio le paga a usted en efectivo, regístrelo aquí: el dinero entra directo a caja chica.</p>
            <label>¿Quién pagó?
              <select name="socio" [(ngModel)]="socioId" (ngModelChange)="elegidos.set([])">
                <option [ngValue]="null">Elija un socio</option>
                @for (s of socios(); track s.socioId) { <option [ngValue]="s.socioId">{{ s.nombre }}</option> }
              </select>
            </label>
            @if (socioElegido(); as s) {
              <div class="meses" role="group" aria-label="Meses que pagó">
                @for (m of s.meses; track m.periodo) {
                  <label [class.pagado]="m.cajaChicaPagado">
                    <input type="checkbox" [disabled]="m.cajaChicaPagado" [checked]="m.cajaChicaPagado || elegidos().includes(m.periodo)" (change)="alternar(m.periodo)" />
                    {{ mes(m.periodo) }}@if (m.cajaChicaPagado) { ✓ }
                  </label>
                }
              </div>
            }
            <button type="submit" [disabled]="!socioId || !elegidos().length || enviando()">
              Registrar {{ elegidos().length || '' }} {{ elegidos().length === 1 ? 'mes' : 'meses' }}@if (elegidos().length) { : {{ elegidos().length * aporte() | currency: 'USD' }} }
            </button>
          </form>

          <form class="tarjeta" (ngSubmit)="registrar()">
            <h2>Registrar gasto</h2>
            <label>Importe (USD) <input type="number" min="0" step="any" name="importe" [(ngModel)]="importe" /></label>
            <label>Detalle <input name="detalle" maxlength="200" [(ngModel)]="detalle" /></label>
            <button type="submit" [disabled]="!importe || importe <= 0 || !detalle.trim()">Registrar</button>
          </form>
        </div>
      }

      <h2>Movimientos</h2>
      @if (d.movimientos.total === 0) {
        <p class="vacio">Aún no hay movimientos.</p>
      } @else {
        <div class="tabla">
          <table>
            <thead><tr><th>Fecha</th><th>Movimiento</th><th>De quién</th><th>Detalle</th><th class="num">Importe</th></tr></thead>
            <tbody>
              @for (m of d.movimientos.contenido; track m.id) {
                <tr>
                  <td>{{ m.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                  <td>{{ concepto(m.concepto) }}</td>
                  <td>{{ m.quien }}</td>
                  <td>{{ m.detalle }}</td>
                  <td class="num" [class.positivo]="m.sentido !== 'SALIDA'">{{ m.sentido === 'SALIDA' ? '−' : '+' }}{{ m.importe | currency: 'USD' }}</td>
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
  private readonly auth = inject(AuthService);

  protected readonly administra = this.auth.tienePermiso('GESTIONAR_CAJA_CHICA');
  protected readonly esSocio = !!this.auth.yo()?.esSocio;
  protected readonly datos = signal<CajaChica | null>(null);
  protected readonly socios = signal<SocioCajaChica[]>([]);
  protected readonly aporte = signal(0);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly elegidos = signal<string[]>([]);
  protected readonly enviando = signal(false);
  protected socioId: number | null = null;
  protected readonly socioElegido = computed(() => {
    this.elegidos();
    return this.socios().find((s) => s.socioId === this.socioId) ?? null;
  });

  protected importe: number | null = null;
  protected detalle = '';

  // Una clave por intento: se renueva solo tras un éxito, para que reintentar no duplique.
  private clave: string = crypto.randomUUID();
  private claveAporte: string = crypto.randomUUID();

  constructor() {
    alCambiar(() => this.cargar(this.datos()?.movimientos?.pagina ?? 0));
  }

  ngOnInit(): void {
    this.cargar(0);
    this.api.condicionesSocio().subscribe({ next: (c) => this.aporte.set(c.aporteCajaChica) });
  }

  protected cargar(pagina: number): void {
    this.api.cajaChica(pagina).subscribe({
      next: (d) => this.datos.set(d),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
    if (this.administra) {
      this.api.sociosCajaChica().subscribe({ next: (s) => this.socios.set(s), error: (e) => this.error.set(mensajeDeError(e)) });
    }
  }

  protected mes(periodo: string): string {
    return MESES[Number(periodo.slice(5, 7)) - 1];
  }

  protected concepto(codigo: string): string {
    return nombreConcepto(codigo);
  }

  protected alternar(periodo: string): void {
    this.elegidos.update((l) => (l.includes(periodo) ? l.filter((p) => p !== periodo) : [...l, periodo]));
  }

  protected pagado(texto: string): void {
    this.aviso.set(texto);
    this.cargar(0);
  }

  protected recibir(): void {
    const socio = this.socioElegido();
    this.error.set(null);
    this.aviso.set(null);
    this.enviando.set(true);
    this.api.recibirAporteCajaChica(this.socioId!, [...this.elegidos()].sort(), this.claveAporte).subscribe({
      next: () => {
        this.claveAporte = crypto.randomUUID();
        this.aviso.set(`Aporte de ${socio?.nombre} registrado en caja chica.`);
        this.elegidos.set([]);
        this.enviando.set(false);
        this.cargar(0);
      },
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.enviando.set(false);
      },
    });
  }

  protected registrar(): void {
    this.error.set(null);
    this.aviso.set(null);
    this.api.gastoCajaChica(this.importe!, this.detalle.trim(), this.clave).subscribe({
      next: () => {
        this.clave = crypto.randomUUID();
        this.importe = null;
        this.detalle = '';
        this.aviso.set('Gasto registrado.');
        this.cargar(0);
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
