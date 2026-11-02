import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';

import { ApiService, Pagina, SolicitudRetiro } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { DialogoService } from '../../shared/components/dialogo';
import { Estado } from '../../shared/components/estado';
import { Paginador } from '../../shared/components/paginador';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-solicitudes-retiro',
  imports: [CurrencyPipe, DatePipe, Estado, FormsModule, Paginador],
  template: `
    <h1>Retiros de dinero</h1>
    <p class="suave">Confirme la entrega solo cuando el dinero ya se entregó o transfirió: en ese momento sale de la caja principal.</p>
    <div class="herramientas">
      <label>Estado
        <select [ngModel]="estado()" (ngModelChange)="filtrar($event)">
          <option value="PENDIENTE">Por entregar</option>
          <option value="ENTREGADA">Entregadas</option>
          <option value="RECHAZADA">Rechazadas</option>
          <option value="CANCELADA">Canceladas</option>
          <option value="">Todas</option>
        </select>
      </label>
    </div>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }
    @if (datos(); as d) {
      @if (d.total === 0) {
        <p class="vacio">No hay solicitudes en este estado.</p>
      } @else {
        <div class="tabla">
          <table>
            <thead><tr><th>Código</th><th>Fecha</th><th>Titular</th><th>Cuenta</th><th class="num">Importe</th><th>Entrega</th><th>Estado</th><th>Acciones</th></tr></thead>
            <tbody>
              @for (s of d.contenido; track s.id) {
                <tr>
                  <td class="codigo">{{ s.codigo }}</td>
                  <td>{{ s.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                  <td>{{ s.titular }}</td>
                  <td>{{ s.numeroCuenta }}</td>
                  <td class="num">{{ s.importe | currency: 'USD' }}@if (s.costo) { <br /><small class="suave">+ {{ s.costo | currency: 'USD' }} de transferencia</small> }</td>
                  <td>
                    @if (s.banco; as b) {
                      {{ b.banco }} · {{ b.tipoCuenta === 'AHORROS' ? 'Ahorros' : 'Corriente' }} {{ b.numeroCuenta }}<br /><span class="suave">{{ b.titularCuenta }}</span>
                    } @else {
                      Efectivo
                    }
                  </td>
                  <td><app-estado [valor]="s.estado" /></td>
                  <td class="acciones">
                    @if (s.estado === 'PENDIENTE') {
                      <button type="button" (click)="entregar(s)">Confirmar entrega</button>
                      <button type="button" class="peligro" (click)="rechazar(s)">Rechazar</button>
                    } @else {
                      {{ s.motivo ?? s.referencia ?? '' }}
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
export class SolicitudesRetiro implements OnInit {
  private readonly api = inject(ApiService);
  private readonly dialogo = inject(DialogoService);

  protected readonly estado = signal('PENDIENTE');
  protected readonly datos = signal<Pagina<SolicitudRetiro> | null>(null);
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
    this.api.solicitudesRetiro(this.estado() || null, pagina).subscribe({
      next: (d) => this.datos.set(d),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected async entregar(s: SolicitudRetiro): Promise<void> {
    const costo = s.costo ? ` (más ${s.costo} USD del costo de la transferencia)` : '';
    if (await this.dialogo.confirmar(`¿Confirma que entregó ${s.importe} USD a ${s.titular}${costo}? Saldrán de la caja principal.`, 'Confirmar entrega')) {
      this.ejecutar(this.api.entregarRetiro(s.id, null), 'Entrega registrada.');
    }
  }

  protected async rechazar(s: SolicitudRetiro): Promise<void> {
    const motivo = await this.dialogo.pedirTexto(`Motivo del rechazo a ${s.titular}:`, 'Rechazar');
    if (motivo) {
      this.ejecutar(this.api.rechazarRetiro(s.id, motivo), 'Solicitud rechazada. El importe volvió a la cuenta.');
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
