import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';

import { ApiService, Pagina, SolicitudDeposito } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { DialogoService } from '../../shared/components/dialogo';
import { Estado } from '../../shared/components/estado';
import { Paginador } from '../../shared/components/paginador';
import { mensajeDeError } from '../../shared/utils/errores';
import { Comprobantes } from './comprobantes';

@Component({
  selector: 'app-solicitudes-deposito',
  imports: [Comprobantes, CurrencyPipe, DatePipe, Estado, FormsModule, Paginador],
  template: `
    <h1>Depósitos por confirmar</h1>
    <p class="suave">Confirme solo cuando el dinero ya se recibió: en ese momento entra a la cuenta de la persona y a la caja principal.</p>
    <div class="herramientas">
      <label>Estado
        <select [ngModel]="estado()" (ngModelChange)="filtrar($event)">
          <option value="PENDIENTE">Por confirmar</option>
          <option value="CONFIRMADA">Confirmados</option>
          <option value="RECHAZADA">Rechazados</option>
          <option value="CANCELADA">Cancelados</option>
          <option value="">Todos</option>
        </select>
      </label>
    </div>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }
    @if (datos(); as d) {
      @if (d.total === 0) {
        <p class="vacio">No hay depósitos en este estado.</p>
      } @else {
        <div class="tabla">
          <table>
            <thead><tr><th>Código</th><th>Fecha</th><th>Titular</th><th>Cuenta</th><th class="num">Importe</th><th>Medio</th><th>Comprobante</th><th>Estado</th><th>Acciones</th></tr></thead>
            <tbody>
              @for (s of d.contenido; track s.id) {
                <tr>
                  <td class="codigo">{{ s.codigo }}</td>
                  <td>{{ s.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                  <td>{{ s.titular }}</td>
                  <td>{{ s.numeroCuenta }}</td>
                  <td class="num">{{ s.importe | currency: 'USD' }}</td>
                  <td>{{ s.medio === 'EFECTIVO' ? 'Efectivo' : 'Transferencia ' + s.referencia }}</td>
                  <td><app-comprobantes tipo="DEPOSITO" [referenciaId]="s.id" /></td>
                  <td><app-estado [valor]="s.estado" /></td>
                  <td class="acciones">
                    @if (s.estado === 'PENDIENTE') {
                      <button type="button" (click)="confirmar(s)">Confirmar recepción</button>
                      <button type="button" class="peligro" (click)="rechazar(s)">Rechazar</button>
                    } @else {
                      {{ s.motivo ?? '' }}
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-paginador [pagina]="d.pagina" [tamano]="d.tamano" [total]="d.total" (cambiar)="cargar($event)" />
      }
    } @else if (!error()) {
      <p class="cargando" role="status">Cargando…</p>
    }
  `,
})
export class SolicitudesDeposito implements OnInit {
  private readonly api = inject(ApiService);
  private readonly dialogo = inject(DialogoService);

  protected readonly estado = signal('PENDIENTE');
  protected readonly datos = signal<Pagina<SolicitudDeposito> | null>(null);
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
    this.api.solicitudesDeposito(this.estado() || null, pagina).subscribe({
      next: (d) => this.datos.set(d),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected async confirmar(s: SolicitudDeposito): Promise<void> {
    if (await this.dialogo.confirmar(`¿Confirma que recibió ${s.importe} USD de ${s.titular}? Entrarán a su cuenta y a la caja principal.`, 'Confirmar recepción')) {
      this.ejecutar(this.api.confirmarDeposito(s.id), 'Depósito confirmado.');
    }
  }

  protected async rechazar(s: SolicitudDeposito): Promise<void> {
    const motivo = await this.dialogo.pedirTexto(`Motivo del rechazo a ${s.titular}:`, 'Rechazar');
    if (motivo) {
      this.ejecutar(this.api.rechazarDeposito(s.id, motivo), 'Depósito rechazado.');
    }
  }

  private ejecutar(accion: Observable<void>, aviso: string): void {
    this.error.set(null);
    this.aviso.set(null);
    accion.subscribe({
      next: () => {
        this.aviso.set(aviso);
        this.cargar(this.datos()?.pagina ?? 0);
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
