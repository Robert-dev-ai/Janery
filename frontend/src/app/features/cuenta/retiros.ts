import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, SolicitudRetiro } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { DialogoService } from '../../shared/components/dialogo';
import { Estado } from '../../shared/components/estado';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-retiros',
  imports: [CurrencyPipe, DatePipe, Estado, FormsModule],
  template: `
    <h1>Retirar dinero</h1>
    <p class="suave">Su dinero está en la caja de Janery. Pídalo en efectivo o a su banco: el importe se aparta de su cuenta principal hasta que el personal se lo entregue. Para sacar un ahorro, muévalo antes a su cuenta principal.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }

    <form class="tarjeta estrecho" (ngSubmit)="solicitar()">
      @if (saldo() !== null) { <p>Saldo disponible: <strong>{{ saldo() | currency: 'USD' }}</strong></p> }
      <label>Importe (USD) <input type="number" min="0" step="1" name="importe" [(ngModel)]="importe" /></label>
      <label>¿Cómo lo quiere recibir?
        <select name="medio" [(ngModel)]="medio">
          <option value="EFECTIVO">En efectivo</option>
          <option value="TRANSFERENCIA">En mi cuenta de banco</option>
        </select>
      </label>
      @if (medio === 'TRANSFERENCIA') {
        <label>Banco
          <select name="opcionBanco" [(ngModel)]="opcionBanco">
            <option value="Banco Pichincha">Banco Pichincha</option>
            <option value="Banco Guayaquil">Banco Guayaquil</option>
            <option value="OTRO">Otro banco o cooperativa</option>
          </select>
        </label>
        @if (opcionBanco === 'OTRO') {
          <label>Nombre del banco o cooperativa <input name="banco" maxlength="100" [(ngModel)]="banco" /></label>
          <p class="alerta-atraso">La transferencia a otro banco o cooperativa cuesta {{ costoOtros | currency: 'USD' }}, lo que cobra el banco. Se descuenta de su cuenta junto con el retiro.</p>
        } @else {
          <p class="suave">A {{ opcionBanco }} la transferencia no tiene costo.</p>
        }
        <label>Tipo de cuenta
          <select name="tipoCuenta" [(ngModel)]="tipoCuenta">
            <option value="AHORROS">Ahorros</option>
            <option value="CORRIENTE">Corriente</option>
          </select>
        </label>
        <label>Número de cuenta <input name="numeroCuenta" maxlength="40" [(ngModel)]="numeroCuenta" /></label>
        <label>Titular de la cuenta <input name="titularCuenta" maxlength="150" [(ngModel)]="titularCuenta" /></label>
      }
      @if (importe && costo() > 0) {
        <p>Se apartarán de su cuenta <strong>{{ importe + costo() | currency: 'USD' }}</strong>: {{ importe | currency: 'USD' }} del retiro y {{ costo() | currency: 'USD' }} de la transferencia.</p>
      }
      <button type="submit" [disabled]="enviando() || !valido()">Solicitar retiro</button>
    </form>

    <h2>Mis solicitudes</h2>
    @if (solicitudes(); as lista) {
      @if (lista.length) {
        <div class="tabla">
          <table>
            <thead><tr><th scope="col">Código</th><th scope="col">Fecha</th><th scope="col" class="num">Importe</th><th scope="col" class="num">Costo</th><th scope="col">Medio</th><th scope="col">Estado</th><th scope="col">Detalle</th><th scope="col">Acción</th></tr></thead>
            <tbody>
              @for (s of lista; track s.id) {
                <tr>
                  <td class="codigo">{{ s.codigo }}</td>
                  <td>{{ s.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                  <td class="num">{{ s.importe | currency: 'USD' }}</td>
                  <td class="num">{{ s.costo ? (s.costo | currency: 'USD') : '—' }}</td>
                  <td>{{ s.medio === 'EFECTIVO' ? 'Efectivo' : 'Banco: ' + s.banco?.banco }}</td>
                  <td><app-estado [valor]="s.estado" /></td>
                  <td>{{ s.motivo ?? s.referencia ?? '—' }}</td>
                  <td>@if (s.estado === 'PENDIENTE') { <button type="button" class="secundario" (click)="cancelar(s)">Cancelar</button> }</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <p class="vacio">Aún no ha solicitado retiros.</p>
      }
    } @else if (!error()) {
      <p class="cargando" role="status">Cargando…</p>
    }
  `,
})
export class Retiros implements OnInit {
  private readonly api = inject(ApiService);
  private readonly dialogo = inject(DialogoService);

  protected readonly solicitudes = signal<SolicitudRetiro[] | null>(null);
  protected readonly saldo = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  /** Mientras una operación está en curso: evita el doble clic y deja ver que se está procesando. */
  protected readonly enviando = signal(false);

  protected importe: number | null = null;
  protected medio = 'EFECTIVO';
  protected banco = '';
  protected opcionBanco = 'Banco Pichincha';
  /** Costo vigente al escribir esto; el servidor aplica el del parámetro COSTO_TRANSFERENCIA_OTROS. */
  protected readonly costoOtros = 0.41;
  protected tipoCuenta = 'AHORROS';
  protected numeroCuenta = '';
  protected titularCuenta = '';

  // Una clave por intento: se renueva solo tras un éxito, para que reintentar no duplique.
  private clave: string = crypto.randomUUID();

  constructor() {
    alCambiar(() => this.ngOnInit());
  }

  ngOnInit(): void {
    this.api.misRetiros().subscribe({ next: (s) => this.solicitudes.set(s), error: (e) => this.error.set(mensajeDeError(e)) });
    this.api.miCuenta().subscribe({ next: (c) => this.saldo.set(c.saldoDisponible), error: (e) => this.error.set(mensajeDeError(e)) });
  }

  protected costo(): number {
    return this.medio === 'TRANSFERENCIA' && this.opcionBanco === 'OTRO' ? this.costoOtros : 0;
  }

  private nombreBanco(): string {
    return this.opcionBanco === 'OTRO' ? this.banco.trim() : this.opcionBanco;
  }

  protected valido(): boolean {
    if (!this.importe || this.importe <= 0) {
      return false;
    }
    return this.medio === 'EFECTIVO' || !!(this.nombreBanco() && this.numeroCuenta.trim() && this.titularCuenta.trim());
  }

  protected solicitar(): void {
    if (this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    this.aviso.set(null);
    const banco = this.medio === 'TRANSFERENCIA'
      ? { banco: this.nombreBanco(), tipoCuenta: this.tipoCuenta, numeroCuenta: this.numeroCuenta.trim(), titularCuenta: this.titularCuenta.trim() }
      : {};
    this.api.solicitarRetiro({ importe: this.importe!, medio: this.medio, ...banco }, this.clave).subscribe({
      next: () => {
        this.enviando.set(false);
        this.clave = crypto.randomUUID();
        this.importe = null;
        this.aviso.set('Solicitud registrada. El importe queda apartado hasta la entrega.');
        this.ngOnInit();
      },
      error: (e) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  protected async cancelar(s: SolicitudRetiro): Promise<void> {
    if (await this.dialogo.confirmar('¿Cancelar esta solicitud? El importe vuelve a su cuenta principal.', 'Cancelar solicitud', true)) {
      this.error.set(null);
      this.api.cancelarRetiro(s.id).subscribe({ next: () => this.ngOnInit(), error: (e) => this.error.set(mensajeDeError(e)) });
    }
  }
}
