import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, Observable, of } from 'rxjs';

import { AuthService } from '../../core/auth/auth';
import { ApiService, CierreAnual, Pagina, Resultado, ResultadosEnEspera, ResumenSocio } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { Paginador } from '../../shared/components/paginador';
import { DialogoService } from '../../shared/components/dialogo';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-admin-resultados',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, FormsModule, Paginador],
  template: `
    <h1>{{ gestiona ? 'Resultados y socios' : 'Resultados' }}</h1>
    @if (!gestiona) { <p class="suave">Ganancias y pérdidas de Janery y cierres de cada año. Solo para consultar: los registra la presidencia.</p> }
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }

    @if (gestiona) {
    <h2>Socios</h2>
    @if (socios().length === 0) {
      <p class="vacio">Aún no hay socios. Regístrelos en Usuarios.</p>
    } @else {
      <div class="tabla">
        <table>
          <thead><tr><th scope="col">Socio</th><th scope="col" class="num">Iniciales</th><th scope="col" class="num">Mensuales</th><th scope="col" class="num">Personales</th><th scope="col" class="num">Ganancias</th><th scope="col" class="num">Fondo personal</th><th scope="col" class="num">Participación</th><th scope="col" class="num">Este mes</th><th scope="col" class="num">Caja chica</th></tr></thead>
          <tbody>
            @for (s of socios(); track s.socioId) {
              <tr>
                <td>{{ s.nombre }}</td>
                <td class="num">{{ s.totalInicial | currency: 'USD' }}</td>
                <td class="num">{{ s.totalObligatorio | currency: 'USD' }}</td>
                <td class="num">{{ s.totalPersonal | currency: 'USD' }}</td>
                <td class="num">{{ s.ganancias | currency: 'USD' }}</td>
                <td class="num">{{ s.valorActual | currency: 'USD' }}</td>
                <td class="num">{{ s.porcentaje | number: '1.2-4' }} %</td>
                <td class="num">{{ s.rendimientoMes === null ? '—' : (s.rendimientoMes > 0 ? '+' : '') + (s.rendimientoMes | number: '1.2-2') + ' %' }}</td>
                <td class="num">{{ s.totalCajaChica | currency: 'USD' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <form class="tarjeta estrecho mt-4" (ngSubmit)="registrarInicial()">
        <h2>Registrar aporte inicial</h2>
        <p class="suave">Dinero recibido fuera del sistema. Entra a la caja principal y al fondo personal del socio.</p>
        <label>Socio
          <select name="socioInicial" [(ngModel)]="socioInicial">
            @for (s of socios(); track s.socioId) { <option [ngValue]="s.socioId">{{ s.nombre }}</option> }
          </select>
        </label>
        <label>Importe (USD) <input type="number" min="0" step="1" name="importeInicial" [(ngModel)]="importeInicial" /></label>
        <label>Forma de pago
          <select name="metodoInicial" [(ngModel)]="metodoInicial">
            <option value="EFECTIVO">Efectivo</option>
            <option value="TRANSFERENCIA">Transferencia</option>
          </select>
        </label>
        <label>Referencia <input name="referenciaInicial" maxlength="100" [(ngModel)]="referenciaInicial" /></label>
        <button type="submit" [disabled]="enviando() || !socioInicial || !importeInicial || importeInicial <= 0">Registrar</button>
      </form>

      <form class="tarjeta estrecho" (ngSubmit)="registrarCompraventa()">
        <h2>Compraventa de capital entre socios</h2>
        <p class="suave">Regístrela cuando ambos socios la hayan acordado. El comprador paga al vendedor desde su cuenta principal.</p>
        <label>Vendedor
          <select name="vendedor" [(ngModel)]="vendedor">
            @for (s of socios(); track s.socioId) { <option [ngValue]="s.socioId">{{ s.nombre }}</option> }
          </select>
        </label>
        <label>Comprador
          <select name="comprador" [(ngModel)]="comprador">
            @for (s of socios(); track s.socioId) { <option [ngValue]="s.socioId">{{ s.nombre }}</option> }
          </select>
        </label>
        <label>Capital que cambia de manos (USD) <input type="number" min="0" step="1" name="capital" [(ngModel)]="capitalVenta" /></label>
        <label>Precio acordado (USD) <input type="number" min="0" step="1" name="precio" [(ngModel)]="precioVenta" /></label>
        <button type="submit" [disabled]="enviando() || !vendedor || !comprador || vendedor === comprador || !capitalVenta || !precioVenta">Registrar</button>
      </form>
    }
    }

    @if (gestiona) {
    <form class="tarjeta estrecho mt-4" (ngSubmit)="registrar()">
      <h2>Registrar ganancia o pérdida</h2>
      <p class="suave"><strong>Intereses, aperturas y lo que entre por el registro manual ya se reparten solos.</strong> Use esto solo para lo extraordinario: registrar aquí algo ya cobrado lo repartiría dos veces.</p>
      <p class="suave">Con cada ganancia se separa al momento el fondo de reserva, y el resto va a los fondos personales según el valor de cada socio. La pérdida se reparte igual, sin tocar la reserva.</p>
      <label>Tipo
        <select name="tipo" [(ngModel)]="tipo">
          <option value="GANANCIA">Ganancia</option>
          <option value="PERDIDA">Pérdida</option>
        </select>
      </label>
      <label>Importe (USD) <input type="number" min="0" step="1" name="importe" [(ngModel)]="importe" /></label>
      <label>Concepto <input name="concepto" maxlength="200" [(ngModel)]="concepto" /></label>
      <label>Fecha (vacío: hoy) <input type="date" name="fecha" [(ngModel)]="fecha" /></label>
      <button type="submit" [disabled]="enviando() || !importe || importe <= 0 || !concepto.trim()">Registrar</button>
    </form>
    }

    @if (enEspera(); as e) {
      @if (e.ganancia > 0 || e.perdida > 0) {
        <p class="suave">En espera de reparto: {{ e.ganancia | currency: 'USD' }} de ganancia y {{ e.perdida | currency: 'USD' }} de pérdida. Se repartirán cuando alcance para todos los socios.</p>
      }
    }

    @if (resultados(); as r) {
      <h2>Resultados registrados</h2>
      @if (r.total === 0) {
        <p class="vacio">Aún no hay resultados.</p>
      } @else {
        <div class="tabla">
          <table>
            <thead><tr><th scope="col">Fecha</th><th scope="col">Concepto</th><th scope="col" class="num">Importe</th><th scope="col" class="num">A reserva</th></tr></thead>
            <tbody>
              @for (x of r.contenido; track x.id) {
                <tr>
                  <td>{{ x.fecha | date: 'mediumDate' : 'UTC' }}</td>
                  <td>{{ x.concepto }}</td>
                  <td class="num">{{ x.tipo === 'PERDIDA' ? '−' : '+' }}{{ x.importe | currency: 'USD' }}</td>
                  <td class="num">{{ x.reserva | currency: 'USD' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-paginador [pagina]="r.pagina" [tamano]="r.tamano" [total]="r.total" (cambiar)="cargarResultados($event)" />
      }
    }

    <section class="tarjeta mt-4">
      <h2>Cierre anual</h2>
      <p class="suave">Totaliza el año y bloquea nuevos resultados en él. No mueve dinero: la reserva y el reparto ya ocurrieron con cada resultado. No se puede deshacer.</p>
      @if (gestiona) {
      <form class="acciones" (ngSubmit)="cerrar()">
        <label>Año <input type="number" step="1" name="anio" [(ngModel)]="anio" /></label>
        <button type="submit" class="peligro" [disabled]="enviando() || !anio">Cerrar año</button>
      </form>
      }
      @if (cierres().length) {
        <div class="tabla">
          <table>
            <thead><tr><th scope="col">Año</th><th scope="col" class="num">Resultado neto</th><th scope="col" class="num">Reserva</th><th scope="col" class="num">A fondos personales</th></tr></thead>
            <tbody>
              @for (c of cierres(); track c.anio) {
                <tr>
                  <td>{{ c.anio }}</td>
                  <td class="num">{{ c.resultadoNeto | currency: 'USD' }}</td>
                  <td class="num">{{ c.reserva | currency: 'USD' }}</td>
                  <td class="num">{{ c.distribuido | currency: 'USD' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
})
export class AdminResultados implements OnInit {
  private readonly api = inject(ApiService);
  private readonly dialogo = inject(DialogoService);
  /** Los socios sin cargo solo consultan: no ven los formularios ni la lista de socios. */
  protected readonly gestiona = inject(AuthService).tienePermiso('GESTIONAR_RESULTADOS');

  protected readonly socios = signal<ResumenSocio[]>([]);
  protected readonly resultados = signal<Pagina<Resultado> | null>(null);
  protected readonly enEspera = signal<ResultadosEnEspera | null>(null);
  protected readonly cierres = signal<CierreAnual[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  /** Mientras una operación está en curso: evita el doble clic y deja ver que se está procesando. */
  protected readonly enviando = signal(false);

  protected tipo = 'GANANCIA';
  protected importe: number | null = null;
  protected concepto = '';
  protected fecha = '';
  protected anio: number | null = new Date().getFullYear() - 1;
  protected socioInicial: number | null = null;
  protected importeInicial: number | null = null;
  protected metodoInicial = 'EFECTIVO';
  protected referenciaInicial = '';

  // Una clave por intento: se renueva solo tras un éxito, para que reintentar no duplique.
  private claveInicial: string = crypto.randomUUID();
  private claveVenta: string = crypto.randomUUID();

  protected vendedor: number | null = null;
  protected comprador: number | null = null;
  protected capitalVenta: number | null = null;
  protected precioVenta: number | null = null;

  constructor() {
    alCambiar(() => this.cargar());
  }

  ngOnInit(): void {
    this.cargar();
  }

  protected registrarCompraventa(): void {
    this.ejecutar(
      this.api.registrarCompraventa(this.vendedor!, this.comprador!, this.capitalVenta!, this.precioVenta!, this.claveVenta),
      'Compraventa registrada.', () => {
        this.claveVenta = crypto.randomUUID();
        this.capitalVenta = null;
        this.precioVenta = null;
      });
  }

  protected registrarInicial(): void {
    this.ejecutar(
      this.api.registrarAporteInicial(this.socioInicial!, this.importeInicial!, this.metodoInicial,
        this.referenciaInicial.trim() || null, this.claveInicial),
      'Aporte inicial registrado.', () => {
        this.claveInicial = crypto.randomUUID();
        this.importeInicial = null;
        this.referenciaInicial = '';
      });
  }

  protected registrar(): void {
    this.ejecutar(this.api.registrarResultado(this.tipo, this.importe!, this.concepto.trim(), this.fecha || null),
      'Resultado registrado y repartido.', () => {
        this.importe = null;
        this.concepto = '';
        this.fecha = '';
      });
  }

  protected async cerrar(): Promise<void> {
    if (await this.dialogo.confirmar(`¿Cerrar el año ${this.anio}? No se puede deshacer.`, 'Cerrar año', true)) {
      this.ejecutar(this.api.cerrarAnio(this.anio!), `Año ${this.anio} cerrado.`);
    }
  }

  protected cargarResultados(pagina: number): void {
    this.api.resultadosEnEspera().subscribe({
      next: (e) => this.enEspera.set(e),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
    this.api.resultados(pagina).subscribe({
      next: (r) => this.resultados.set(r),
      error: (e) => this.error.set(mensajeDeError(e)),
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
        this.aviso.set(aviso);
        alTerminar?.();
        this.cargar();
      },
      error: (e) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  private cargar(): void {
    forkJoin([this.gestiona ? this.api.socios() : of([] as ResumenSocio[]), this.api.cierres()]).subscribe({
      next: ([socios, cierres]) => {
        this.socios.set(socios);
        this.cierres.set(cierres);
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
    this.cargarResultados(0);
  }
}
