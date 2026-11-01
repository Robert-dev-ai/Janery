import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, Observable } from 'rxjs';

import { ApiService, Credito, ProductosCredito, Simulacion } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { mensajeDeError } from '../../shared/utils/errores';

const FAMILIAS: Record<string, string> = {
  LETRAS: 'Crédito a letras',
  EMERGENTE_CLIENTE: 'Emergente',
  EMERGENTE_SOCIO: 'Cupo emergente',
  EMERGENTE_ESTACIONAL: 'Emergente estacional',
};

@Component({
  selector: 'app-creditos',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, FormsModule],
  template: `
    <h1>Créditos</h1>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }

    @for (c of creditos(); track c.id) {
      @if (c.estado !== 'FINALIZADO' && c.estado !== 'RECHAZADO' && !(esCupo(c) && c.totalPendiente === 0)) {
        <section class="tarjeta">
          <h2>{{ familia(c.familia) }} {{ c.modalidad ?? '' }} · {{ c.estado }}</h2>
          <p class="suave">
            Interés {{ c.tasaInteres | number: '1.0-4' }} %{{ c.unidadTasa === 'PCT_DIARIO' ? ' diario' : ' mensual' }}
            @if (c.tasaDesgravamen > 0) { · desgravamen {{ c.tasaDesgravamen | number: '1.0-4' }} % }
          </p>
          @if (c.estado === 'SOLICITADO') {
            <p>Solicitud de {{ c.importe | currency: 'USD' }} a {{ c.plazoMeses }} letras, pendiente de aprobación.</p>
          } @else {
            <div class="importes">
              <div>Capital pendiente<p class="importe">{{ c.capitalPendiente | currency: 'USD' }}</p></div>
              <div>Total pendiente<p class="importe">{{ c.totalPendiente | currency: 'USD' }}</p></div>
              @if (c.familia === 'LETRAS') {
                <div>Encaje<p class="importe">{{ c.encaje | currency: 'USD' }}</p></div>
                <div>Ahorro letras<p class="importe">{{ c.ahorroLetras | currency: 'USD' }}</p></div>
              }
            </div>

            @if (esCupo(c)) {
              <form class="acciones" (ngSubmit)="pagarCupo(c, importePago)">
                <label>Importe a pagar (USD)
                  <input type="number" min="0.01" step="0.01" name="pago{{ c.id }}" [(ngModel)]="importePago" />
                </label>
                <button type="submit" [disabled]="!importePago || importePago <= 0">Pagar</button>
              </form>
            } @else {
              <div class="tabla">
                <table>
                  <thead><tr><th>N.º</th><th>Vence</th><th class="num">Capital</th><th class="num">Interés</th><th class="num">Desgravamen</th><th class="num">Ahorro letras</th><th class="num">Total</th><th>Estado</th></tr></thead>
                  <tbody>
                    @for (l of c.cuotas; track l.numero) {
                      <tr>
                        <td>{{ l.numero }}</td>
                        <td>{{ l.vencimiento | date: 'mediumDate' : 'UTC' }}</td>
                        <td class="num">{{ l.capital | currency: 'USD' }}</td>
                        <td class="num">{{ l.interes | currency: 'USD' }}</td>
                        <td class="num">{{ l.fondoDesgravamen | currency: 'USD' }}</td>
                        <td class="num">{{ l.ahorroLetras | currency: 'USD' }}</td>
                        <td class="num">{{ l.total | currency: 'USD' }}</td>
                        <td>
                          @if (c.cuotasPagadas.includes(l.numero)) {
                            Pagada
                          } @else if (l.pagado > 0) {
                            Pagado {{ l.pagado | currency: 'USD' }}
                          } @else {
                            Pendiente
                          }
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
              @if (esUltima(c) && c.familia === 'LETRAS') {
                <label><input type="checkbox" style="display:inline;width:auto" [(ngModel)]="usarAhorro" /> Usar ahorro letras en esta última letra</label>
                <label><input type="checkbox" style="display:inline;width:auto" [(ngModel)]="usarEncaje" /> Usar encaje en esta última letra</label>
              }
              <p>Falta de la siguiente letra: <strong>{{ pendienteSiguiente(c) | currency: 'USD' }}</strong></p>
              <form class="acciones" (ngSubmit)="pagarCuota(c)">
                <label>Importe (USD; vacío, lo que falta)
                  <input type="number" min="0.01" step="0.01" name="letra{{ c.id }}" [(ngModel)]="importeLetra" />
                </label>
                <button type="submit">Pagar</button>
              </form>
              <p class="suave">Si paga solo una parte, lo primero que se cobra es el capital.</p>
            }
          }
        </section>
      }
    }

    @if (productos(); as p) {
      <section class="tarjeta">
        <h2>{{ p.perfil === 'SOCIO' ? 'Cupo emergente' : 'Crédito emergente' }}</h2>
        @if (p.perfil === 'SOCIO') {
          <p>Utilizado: {{ p.cupoUtilizado | currency: 'USD' }} · Disponible: <strong>{{ p.cupoDisponible | currency: 'USD' }}</strong></p>
          <p class="suave">Interés {{ p.emergenteInteres | number: '1.0-4' }} % y desgravamen {{ p.emergenteDesgravamen | number: '1.0-4' }} % diarios sobre lo utilizado.</p>
          @if (p.promocionAbierta) {
            <label>Destino
              <select name="destino" [(ngModel)]="promocion">
                <option [ngValue]="null">Cupo general</option>
                <option [ngValue]="p.promocionAbierta">Promoción {{ p.promocionAbierta }} (hasta {{ p.promocionLimite | currency: 'USD' }}, {{ p.promocionTasa | number: '1.0-4' }} % diario)</option>
              </select>
            </label>
          }
        } @else {
          <p>Hasta {{ p.emergenteLimite | currency: 'USD' }}, una vez al mes, a pagar el mes siguiente.</p>
          <p class="suave">Interés {{ p.emergenteInteres | number: '1.0-4' }} % y desgravamen {{ p.emergenteDesgravamen | number: '1.0-4' }} % mensuales.</p>
        }
        <form class="acciones" (ngSubmit)="pedirEmergente(p)">
          <label>Importe (USD)
            <input type="number" min="0.01" step="0.01" name="emergente" [(ngModel)]="importeEmergente" />
          </label>
          <button type="submit" [disabled]="!importeEmergente || importeEmergente <= 0">Solicitar</button>
        </form>
      </section>

      <section class="tarjeta">
        <h2>Crédito a letras</h2>
        <p class="suave">Letras mensuales. El encaje se paga al inicio y se devuelve al terminar.</p>
        <form (ngSubmit)="simular()">
          <label>Modalidad
            <select name="modalidad" [(ngModel)]="modalidad">
              @for (m of p.letras; track m.modalidad) {
                <option [value]="m.modalidad">{{ m.modalidad }}: {{ m.tasaInteres | number: '1.0-4' }} % mensual, encaje {{ m.porcentajeEncaje | number: '1.0-4' }} %</option>
              }
            </select>
          </label>
          <label>Importe (USD, de {{ p.importeMinimo | number }} a {{ p.importeMaximo | number }})
            <input type="number" name="importe" step="0.01" [(ngModel)]="importe" />
          </label>
          <label>Plazo (de {{ p.plazoMinimo }} a {{ p.plazoMaximo }} meses)
            <input type="number" name="plazo" step="1" [(ngModel)]="plazo" />
          </label>
          <button type="submit" class="secundario" [disabled]="!importe || !plazo">Simular</button>
        </form>
        @if (simulacion(); as s) {
          <p>Encaje al inicio: <strong>{{ s.encaje | currency: 'USD' }}</strong></p>
          <div class="tabla">
            <table>
              <thead><tr><th>N.º</th><th class="num">Capital</th><th class="num">Interés</th><th class="num">Desgravamen</th><th class="num">Ahorro letras</th><th class="num">Total</th></tr></thead>
              <tbody>
                @for (l of s.cuotas; track l.numero) {
                  <tr>
                    <td>{{ l.numero }}</td>
                    <td class="num">{{ l.capital | currency: 'USD' }}</td>
                    <td class="num">{{ l.interes | currency: 'USD' }}</td>
                    <td class="num">{{ l.fondoDesgravamen | currency: 'USD' }}</td>
                    <td class="num">{{ l.ahorroLetras | currency: 'USD' }}</td>
                    <td class="num">{{ l.total | currency: 'USD' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <button type="button" (click)="solicitar()">Solicitar este crédito</button>
        }
      </section>
    }
  `,
})
export class Creditos implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly creditos = signal<Credito[]>([]);
  protected readonly productos = signal<ProductosCredito | null>(null);
  protected readonly simulacion = signal<Simulacion | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  protected modalidad = 'NORMAL';
  protected importe: number | null = null;
  protected plazo: number | null = null;
  protected importeEmergente: number | null = null;
  protected importePago: number | null = null;
  protected importeLetra: number | null = null;
  protected promocion: string | null = null;
  protected usarAhorro = false;
  protected usarEncaje = false;

  // Una clave por intento: se renueva solo tras un éxito, para que reintentar no duplique.
  private clave: string = crypto.randomUUID();

  constructor() {
    alCambiar(() => this.cargar());
  }

  ngOnInit(): void {
    this.cargar();
  }

  protected familia(codigo: string): string {
    return FAMILIAS[codigo] ?? codigo;
  }

  protected esCupo(c: Credito): boolean {
    return c.familia === 'EMERGENTE_SOCIO' || c.familia === 'EMERGENTE_ESTACIONAL';
  }

  protected esUltima(c: Credito): boolean {
    return c.cuotasPagadas.length === c.cuotas.length - 1;
  }

  protected simular(): void {
    this.error.set(null);
    this.api.simularLetras(this.modalidad, this.importe!, this.plazo!).subscribe({
      next: (s) => this.simulacion.set(s),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected solicitar(): void {
    this.ejecutar(this.api.solicitarLetras(this.modalidad, this.importe!, this.plazo!), 'Solicitud enviada.', () => {
      this.simulacion.set(null);
    });
  }

  protected pedirEmergente(p: ProductosCredito): void {
    const accion = p.perfil === 'SOCIO'
      ? this.api.disponerCupo(this.importeEmergente!, this.promocion, this.clave)
      : this.api.solicitarEmergente(this.importeEmergente!, this.clave);
    this.ejecutar(accion, 'El dinero ya está en su cuenta principal.', () => {
      this.importeEmergente = null;
    });
  }

  protected pagarCupo(c: Credito, importe: number | null): void {
    this.ejecutar(this.api.pagarCredito(c.id, { importe: importe! }, this.clave), 'Pago registrado.', () => {
      this.importePago = null;
    });
  }

  protected pagarCuota(c: Credito): void {
    const final = this.esUltima(c) && c.familia === 'LETRAS';
    const cuerpo = { importe: this.importeLetra || null, usarAhorroLetras: final && this.usarAhorro, usarEncaje: final && this.usarEncaje };
    this.ejecutar(this.api.pagarCredito(c.id, cuerpo, this.clave), 'Pago registrado.', () => {
      this.importeLetra = null;
    });
  }

  protected pendienteSiguiente(c: Credito): number {
    const siguiente = c.cuotas.find((l) => !c.cuotasPagadas.includes(l.numero));
    return siguiente ? siguiente.total - siguiente.pagado : 0;
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
    forkJoin([this.api.misCreditos(), this.api.productosCredito()]).subscribe({
      next: ([creditos, productos]) => {
        this.creditos.set(creditos);
        this.productos.set(productos);
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
