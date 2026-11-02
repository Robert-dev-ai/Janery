import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, SolicitudDeposito } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { DialogoService } from '../../shared/components/dialogo';
import { Estado } from '../../shared/components/estado';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-depositos',
  imports: [CurrencyPipe, DatePipe, Estado, FormsModule],
  template: `
    <h1>Depositar dinero</h1>
    <p class="suave">Avísenos cuánto va a depositar, en efectivo o por transferencia. El importe se suma a su saldo cuando el personal confirme que lo recibió.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }

    <form class="tarjeta estrecho" (ngSubmit)="solicitar()">
      <label>Importe (USD) <input type="number" min="0" step="1" name="importe" [(ngModel)]="importe" /></label>
      <label>¿Cómo lo entrega?
        <select name="medio" [(ngModel)]="medio">
          <option value="EFECTIVO">En efectivo</option>
          <option value="TRANSFERENCIA">Por transferencia</option>
        </select>
      </label>
      @if (medio === 'TRANSFERENCIA') {
        <label>Número de la transferencia <input name="referencia" maxlength="100" [(ngModel)]="referencia" /></label>
      }
      <button type="submit" [disabled]="!valido()">Avisar depósito</button>
    </form>

    <h2>Mis depósitos</h2>
    @if (solicitudes(); as lista) {
      @if (lista.length) {
        <div class="tabla">
          <table>
            <thead><tr><th>Fecha</th><th class="num">Importe</th><th>Medio</th><th>Estado</th><th>Detalle</th><th>Acción</th></tr></thead>
            <tbody>
              @for (s of lista; track s.id) {
                <tr>
                  <td>{{ s.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                  <td class="num">{{ s.importe | currency: 'USD' }}</td>
                  <td>{{ s.medio === 'EFECTIVO' ? 'Efectivo' : 'Transferencia' }}</td>
                  <td><app-estado [valor]="s.estado" /></td>
                  <td>{{ s.motivo ?? s.referencia ?? '—' }}</td>
                  <td>@if (s.estado === 'PENDIENTE') { <button type="button" class="secundario" (click)="cancelar(s)">Cancelar</button> }</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <p class="vacio">Aún no ha avisado depósitos.</p>
      }
    } @else if (!error()) {
      <p class="cargando" role="status">Cargando…</p>
    }
  `,
})
export class Depositos implements OnInit {
  private readonly api = inject(ApiService);
  private readonly dialogo = inject(DialogoService);

  protected readonly solicitudes = signal<SolicitudDeposito[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  protected importe: number | null = null;
  protected medio = 'EFECTIVO';
  protected referencia = '';

  // Una clave por intento: se renueva solo tras un éxito, para que reintentar no duplique.
  private clave: string = crypto.randomUUID();

  constructor() {
    alCambiar(() => this.ngOnInit());
  }

  ngOnInit(): void {
    this.api.misDepositos().subscribe({ next: (s) => this.solicitudes.set(s), error: (e) => this.error.set(mensajeDeError(e)) });
  }

  protected valido(): boolean {
    return !!this.importe && this.importe > 0 && (this.medio === 'EFECTIVO' || !!this.referencia.trim());
  }

  protected solicitar(): void {
    this.error.set(null);
    this.aviso.set(null);
    const referencia = this.medio === 'TRANSFERENCIA' ? this.referencia.trim() : null;
    this.api.solicitarDeposito({ importe: this.importe!, medio: this.medio, referencia }, this.clave).subscribe({
      next: () => {
        this.clave = crypto.randomUUID();
        this.importe = null;
        this.referencia = '';
        this.aviso.set('Depósito registrado. Se sumará a su saldo cuando el personal lo confirme.');
        this.ngOnInit();
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected async cancelar(s: SolicitudDeposito): Promise<void> {
    if (await this.dialogo.confirmar('¿Cancelar este aviso de depósito?', 'Cancelar aviso', true)) {
      this.error.set(null);
      this.api.cancelarDeposito(s.id).subscribe({ next: () => this.ngOnInit(), error: (e) => this.error.set(mensajeDeError(e)) });
    }
  }
}
