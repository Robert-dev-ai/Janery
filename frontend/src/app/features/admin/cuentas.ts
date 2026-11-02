import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';

import { ApiService, Cuenta, Pagina } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { Estado } from '../../shared/components/estado';
import { Paginador } from '../../shared/components/paginador';
import { DialogoService } from '../../shared/components/dialogo';
import { mensajeDeError } from '../../shared/utils/errores';

interface Formulario {
  cuenta: Cuenta;
  tipo: 'depositos' | 'retiros';
  importe: number | null;
  metodoPago: string;
  referencia: string;
  /** Se conserva entre reintentos del mismo formulario: el backend no duplica la operación. */
  clave: string;
}

@Component({
  selector: 'app-admin-cuentas',
  imports: [CurrencyPipe, Estado, FormsModule, Paginador],
  template: `
    <h1>Cuentas</h1>
    <div class="herramientas">
      <label>Estado
        <select [ngModel]="estado()" (ngModelChange)="filtrar($event)">
          <option value="PENDIENTE_PAGO">Aperturas pendientes</option>
          <option value="PENDIENTE_CIERRE">Cierres pendientes</option>
          <option value="ACTIVA">Activas</option>
          <option value="">Todas</option>
        </select>
      </label>
      <label>Buscar en esta página
        <input type="search" placeholder="Número o titular" [ngModel]="busqueda()" (ngModelChange)="busqueda.set($event)" />
      </label>
    </div>

    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }

    @if (formulario(); as f) {
      <form class="tarjeta estrecho" (ngSubmit)="registrar(f)">
        <h2>{{ f.tipo === 'depositos' ? 'Depósito' : 'Retiro' }} · {{ f.cuenta.numero }}</h2>
        <p class="suave">{{ f.cuenta.titular }}. Saldo: {{ f.cuenta.saldoDisponible | currency: 'USD' }}</p>
        <label>Importe (USD) <input type="number" min="0" step="1" name="importe" [(ngModel)]="f.importe" required /></label>
        <label>Forma de pago
          <select name="metodo" [(ngModel)]="f.metodoPago">
            <option value="EFECTIVO">Efectivo</option>
            <option value="TRANSFERENCIA">Transferencia</option>
          </select>
        </label>
        <label>Referencia <input name="referencia" maxlength="100" [(ngModel)]="f.referencia" /></label>
        <div class="acciones">
          <button type="submit" [disabled]="!f.importe || f.importe <= 0">Registrar</button>
          <button type="button" class="secundario" (click)="formulario.set(null)">Cancelar</button>
        </div>
      </form>
    }

    @if (datos(); as d) {
      @if (d.total === 0) {
        <p class="vacio">No hay cuentas en este estado.</p>
      } @else {
        <div class="tabla">
          <table>
            <thead><tr><th>Cuenta</th><th>Titular</th><th>Estado</th><th>Pago</th><th class="num">Saldo</th><th>Acciones</th></tr></thead>
            <tbody>
              @for (c of filas(); track c.id) {
                <tr>
                  <td>{{ c.numero }}</td>
                  <td>{{ c.titular }}</td>
                  <td><app-estado [valor]="c.estado" /></td>
                  <td>{{ c.metodoPago }} {{ c.referenciaPago }}</td>
                  <td class="num">{{ c.saldoDisponible | currency: 'USD' }}</td>
                  <td class="acciones">
                    @switch (c.estado) {
                      @case ('PENDIENTE_PAGO') {
                        <button type="button" (click)="confirmarApertura(c)">Confirmar pago</button>
                        <button type="button" class="peligro" (click)="rechazar(c)">Rechazar</button>
                      }
                      @case ('PENDIENTE_CIERRE') {
                        <button type="button" (click)="confirmarCierre(c)">Confirmar cierre</button>
                      }
                      @case ('ACTIVA') {
                        <button type="button" (click)="abrir(c, 'depositos')">Depósito</button>
                        <button type="button" class="secundario" (click)="abrir(c, 'retiros')">Retiro</button>
                      }
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-paginador [pagina]="d.pagina" [tamano]="d.tamano" [total]="d.total" (cambiar)="cargar($event)" />
      }
    }
  `,
})
export class AdminCuentas implements OnInit {
  private readonly api = inject(ApiService);
  private readonly dialogo = inject(DialogoService);

  protected readonly estado = signal('PENDIENTE_PAGO');
  protected readonly datos = signal<Pagina<Cuenta> | null>(null);
  protected readonly busqueda = signal('');
  protected readonly filas = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    return (this.datos()?.contenido ?? []).filter((x) => !texto || (x.numero + ' ' + x.titular).toLowerCase().includes(texto));
  });
  protected readonly formulario = signal<Formulario | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  constructor() {
    alCambiar(() => this.cargar(this.datos()?.pagina ?? 0));
  }

  ngOnInit(): void {
    this.cargar(0);
  }

  protected filtrar(estado: string): void {
    this.estado.set(estado);
    this.cargar(0);
  }

  protected cargar(pagina: number): void {
    this.api.cuentas(this.estado() || null, pagina).subscribe({
      next: (d) => this.datos.set(d),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected async confirmarApertura(c: Cuenta): Promise<void> {
    if (await this.dialogo.confirmar(`¿Confirma que recibió el pago de apertura de ${c.titular}?`, 'Confirmar pago')) {
      this.ejecutar(this.api.confirmarApertura(c.id), `Cuenta ${c.numero} activada.`);
    }
  }

  protected async rechazar(c: Cuenta): Promise<void> {
    const motivo = await this.dialogo.pedirTexto(`Motivo del rechazo de ${c.numero}:`, 'Rechazar');
    if (motivo) {
      this.ejecutar(this.api.rechazarApertura(c.id, motivo), `Solicitud ${c.numero} rechazada.`);
    }
  }

  protected async confirmarCierre(c: Cuenta): Promise<void> {
    if (await this.dialogo.confirmar(`¿Confirma que devolvió el depósito a ${c.titular} y que cierra la cuenta?`, 'Confirmar cierre', true)) {
      this.ejecutar(this.api.confirmarCierre(c.id), `Cuenta ${c.numero} cerrada.`);
    }
  }

  protected abrir(cuenta: Cuenta, tipo: 'depositos' | 'retiros'): void {
    this.formulario.set({ cuenta, tipo, importe: null, metodoPago: 'EFECTIVO', referencia: '', clave: crypto.randomUUID() });
  }

  protected registrar(f: Formulario): void {
    this.ejecutar(
      this.api.movimiento(f.cuenta.id, f.tipo, f.importe!, f.metodoPago, f.referencia.trim() || null, f.clave),
      'Movimiento registrado.',
      () => this.formulario.set(null),
    );
  }

  private ejecutar(accion: Observable<unknown>, aviso: string, alTerminar?: () => void): void {
    this.error.set(null);
    this.aviso.set(null);
    accion.subscribe({
      next: () => {
        this.aviso.set(aviso);
        alTerminar?.();
        this.cargar(this.datos()?.pagina ?? 0);
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
