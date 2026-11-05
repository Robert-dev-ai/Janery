import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, CreditoDetalle, DetalleFinanciero, MovimientoGeneral, Pagina, PersonaFinanciera, ResumenGeneral } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { Estado } from '../../shared/components/estado';
import { Paginador } from '../../shared/components/paginador';
import { nombreConcepto, nombreFamilia } from '../../shared/utils/etiquetas';
import { mensajeDeError } from '../../shared/utils/errores';

/** Solo para socios. Muestra a todas las personas sin datos de contacto; el backend también lo exige. */
@Component({
  selector: 'app-area-general',
  imports: [CurrencyPipe, DatePipe, Estado, FormsModule, Paginador],
  template: `
    <h1>Estado general</h1>
    <p class="suave">Área exclusiva de socios: cómo va la caja y lo que hace cada persona en créditos, ahorros y movimientos.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }

    @if (resumen(); as r) {
      <div class="importes">
        <section class="tarjeta"><h2>Caja principal</h2><p class="importe">{{ r.cajaPrincipal | currency: 'USD' }}</p></section>
        <section class="tarjeta"><h2>Fondo de reserva</h2><p class="importe">{{ r.fondoReserva | currency: 'USD' }}</p></section>
        <section class="tarjeta"><h2>Capital prestado</h2><p class="importe">{{ r.capitalPrestado | currency: 'USD' }}</p><p class="suave">{{ r.creditosActivos }} créditos activos</p></section>
        <section class="tarjeta"><h2>Ahorros</h2><p class="importe">{{ r.ahorros | currency: 'USD' }}</p></section>
        <section class="tarjeta"><h2>Saldos en cuentas</h2><p class="importe">{{ r.saldosEnCuentas | currency: 'USD' }}</p><p class="suave">{{ r.personasConCuenta }} personas</p></section>
      </div>
    }

    <h2>Personas</h2>
    <div class="herramientas">
      <label>Buscar por nombre
        <input type="search" [ngModel]="buscar()" (ngModelChange)="filtrar($event)" />
      </label>
    </div>
    @if (personas(); as d) {
      @if (d.total === 0) {
        <p class="vacio">No se encontraron personas.</p>
      } @else {
        <div class="tabla">
          <table>
            <thead><tr><th scope="col">Persona</th><th scope="col" class="num">Saldo</th><th scope="col" class="num">Ahorros</th><th scope="col">Créditos</th><th scope="col" class="num">Capital pendiente</th><th scope="col">Letras vencidas</th><th scope="col"></th></tr></thead>
            <tbody>
              @for (p of d.contenido; track p.id) {
                <tr>
                  <td>{{ p.nombre }} @if (p.socio) { <span class="estado estado-informacion">Socio</span> }</td>
                  <td class="num">{{ p.saldoCuenta | currency: 'USD' }}</td>
                  <td class="num">{{ p.ahorros | currency: 'USD' }}</td>
                  <td>{{ p.creditosActivos }}</td>
                  <td class="num">{{ p.capitalPendiente | currency: 'USD' }}</td>
                  <td>@if (p.cuotasVencidas > 0) { <span class="estado estado-peligro">{{ p.cuotasVencidas }}</span> } @else { Al día }</td>
                  <td><button type="button" class="secundario" (click)="abrir(p)">Ver detalle</button></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-paginador [pagina]="d.pagina" [tamano]="d.tamano" [total]="d.total" (cambiar)="cargarPersonas($event)" />
      }
    }

    @if (detalle(); as x) {
      <section class="tarjeta ficha-detalle">
        <div class="encabezado-tarjeta">
          <h2>{{ x.persona.nombre }} · {{ x.persona.cuenta }}</h2>
          <button type="button" class="secundario" (click)="detalle.set(null)">Cerrar</button>
        </div>
        <h3>Créditos y letras</h3>
        @let creditos = x.creditos;
        <div class="tabla">
          <table>
            <thead><tr><th scope="col">Crédito</th><th scope="col">Estado</th><th scope="col" class="num">Importe</th><th scope="col" class="num">Capital pendiente</th><th scope="col">Letras pagadas</th><th scope="col">Próximo vencimiento</th></tr></thead>
            <tbody>
              @for (c of creditos; track c.id) {
                <tr>
                  <td>{{ familia(c.familia) }} {{ c.modalidad ?? '' }}</td>
                  <td><app-estado [valor]="c.estado" /></td>
                  <td class="num">{{ c.importe | currency: 'USD' }}</td>
                  <td class="num">{{ c.capitalPendiente | currency: 'USD' }}</td>
                  <td>{{ pagadas(c) }} de {{ c.cuotas.length }}</td>
                  <td>{{ proxima(c) ? (proxima(c)!.vencimiento | date: 'dd/MM/yyyy' : 'UTC') : '—' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="suave">Sin créditos.</td></tr>
              }
            </tbody>
          </table>
        </div>
        <h3 class="mt-4">Ahorros</h3>
        @for (a of x.ahorros; track a.id) {
          <div class="fila-dato"><span>{{ a.nombre }} <app-estado [valor]="a.estado" /></span><strong class="num">{{ a.saldo | currency: 'USD' }}</strong></div>
        } @empty {
          <p class="suave">Sin ahorros.</p>
        }
        <h3 class="mt-4">Últimos movimientos</h3>
        @for (m of x.movimientos; track m.id) {
          <div class="fila-dato"><span>{{ concepto(m.concepto) }} <span class="suave">{{ m.fecha | date: 'short' : 'America/Guayaquil' }}</span></span><strong class="num">{{ m.sentido === 'SALIDA' ? '−' : '+' }}{{ m.importe | currency: 'USD' }}</strong></div>
        } @empty {
          <p class="suave">Sin movimientos.</p>
        }
      </section>
    }

    <h2 class="mt-4">Movimientos generales</h2>
    @if (movimientos(); as d) {
      @if (d.total === 0) {
        <p class="vacio">Aún no hay movimientos.</p>
      } @else {
        <div class="tabla">
          <table>
            <thead><tr><th scope="col">Fecha</th><th scope="col">Persona</th><th scope="col">Concepto</th><th scope="col" class="num">Importe</th></tr></thead>
            <tbody>
              @for (m of d.contenido; track m.id) {
                <tr>
                  <td>{{ m.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                  <td>{{ m.persona }}</td>
                  <td>{{ concepto(m.concepto) }}</td>
                  <td class="num">{{ m.sentido === 'SALIDA' ? '−' : '+' }}{{ m.importe | currency: 'USD' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-paginador [pagina]="d.pagina" [tamano]="d.tamano" [total]="d.total" (cambiar)="cargarMovimientos($event)" />
      }
    }
  `,
})
export class AreaGeneral implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly familia = nombreFamilia;
  protected readonly concepto = nombreConcepto;
  protected readonly resumen = signal<ResumenGeneral | null>(null);
  protected readonly personas = signal<Pagina<PersonaFinanciera> | null>(null);
  protected readonly movimientos = signal<Pagina<MovimientoGeneral> | null>(null);
  protected readonly detalle = signal<DetalleFinanciero | null>(null);
  protected readonly buscar = signal('');
  protected readonly error = signal<string | null>(null);

  constructor() {
    alCambiar(() => {
      this.ngOnInit();
      const abierto = this.detalle();
      if (abierto) {
        this.abrir(abierto.persona);
      }
    });
  }

  ngOnInit(): void {
    this.api.resumenGeneral().subscribe({ next: (r) => this.resumen.set(r), error: (e) => this.error.set(mensajeDeError(e)) });
    this.cargarPersonas(this.personas()?.pagina ?? 0);
    this.cargarMovimientos(this.movimientos()?.pagina ?? 0);
  }

  protected filtrar(texto: string): void {
    this.buscar.set(texto);
    this.cargarPersonas(0);
  }

  protected cargarPersonas(pagina: number): void {
    this.api.personasGeneral(this.buscar(), pagina).subscribe({ next: (d) => this.personas.set(d), error: (e) => this.error.set(mensajeDeError(e)) });
  }

  protected cargarMovimientos(pagina: number): void {
    this.api.movimientosGeneral(pagina).subscribe({ next: (d) => this.movimientos.set(d), error: (e) => this.error.set(mensajeDeError(e)) });
  }

  protected abrir(p: PersonaFinanciera): void {
    this.api.detalleGeneral(p.id).subscribe({ next: (d) => this.detalle.set(d), error: (e) => this.error.set(mensajeDeError(e)) });
  }

  protected pagadas(c: CreditoDetalle): number {
    return c.cuotas.filter((q) => q.pagadaEn).length;
  }

  protected proxima(c: CreditoDetalle) {
    return c.estado === 'ACTIVO' ? c.cuotas.find((q) => !q.pagadaEn) : undefined;
  }
}
