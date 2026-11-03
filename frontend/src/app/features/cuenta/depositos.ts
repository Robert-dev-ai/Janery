import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { of, switchMap } from 'rxjs';

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
    @if (motivo) { <p class="alerta-atraso" role="alert">{{ motivo }}</p> }
    <p class="suave">Avísenos cuánto va a depositar, en efectivo o por transferencia, y adjunte el comprobante. El importe se suma a su saldo cuando el personal confirme que lo recibió.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }

    <form class="tarjeta estrecho" (ngSubmit)="solicitar()">
      <label>Importe (USD) <input type="number" min="0" step="any" name="importe" [(ngModel)]="importe" /></label>
      <label>¿Cómo lo entrega?
        <select name="medio" [(ngModel)]="medio">
          <option value="EFECTIVO">En efectivo</option>
          <option value="TRANSFERENCIA">Por transferencia</option>
        </select>
      </label>
      @if (medio === 'TRANSFERENCIA') {
        <label>Número de la transferencia <input name="referencia" maxlength="100" [(ngModel)]="referencia" /></label>
      }
      <label>Comprobante de pago {{ medio === 'TRANSFERENCIA' ? '(obligatorio)' : '(si tiene uno)' }}
        <input type="file" accept="application/pdf,image/jpeg,image/png,image/webp" (change)="elegir($event)" />
      </label>
      <p class="suave">Foto o PDF del comprobante, hasta 10 MB.</p>
      <button type="submit" [disabled]="!valido() || enviando()">Avisar depósito</button>
    </form>

    <h2>Mis depósitos</h2>
    @if (solicitudes(); as lista) {
      @if (lista.length) {
        <div class="tabla">
          <table>
            <thead><tr><th>Código</th><th>Fecha</th><th class="num">Importe</th><th>Medio</th><th>Comprobante</th><th>Estado</th><th>Detalle</th><th>Acción</th></tr></thead>
            <tbody>
              @for (s of lista; track s.id) {
                <tr>
                  <td class="codigo">{{ s.codigo }}</td>
                  <td>{{ s.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                  <td class="num">{{ s.importe | currency: 'USD' }}</td>
                  <td>{{ s.medio === 'EFECTIVO' ? 'Efectivo' : 'Transferencia' }}</td>
                  <td>
                    @if (s.comprobantes) {
                      Adjunto
                    } @else if (s.estado === 'PENDIENTE') {
                      <label class="m-0 text-sm font-normal">Adjuntar
                        <input type="file" accept="application/pdf,image/jpeg,image/png,image/webp" (change)="adjuntar(s, $event)" />
                      </label>
                    } @else {
                      —
                    }
                  </td>
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
  protected readonly enviando = signal(false);
  /** Explicación cuando se llega aquí porque faltó saldo para otra operación. */
  protected readonly motivo: string | null = (inject(Router).getCurrentNavigation()?.extras.state?.['aviso'] as string) ?? null;

  protected importe: number | null = null;
  protected medio = 'EFECTIVO';
  protected referencia = '';
  protected comprobante: File | null = null;

  // Una clave por intento: se renueva solo tras un éxito, para que reintentar no duplique.
  private clave: string = crypto.randomUUID();

  constructor() {
    alCambiar(() => this.ngOnInit());
  }

  ngOnInit(): void {
    this.api.misDepositos().subscribe({ next: (s) => this.solicitudes.set(s.filter((x) => !x.pago)), error: (e) => this.error.set(mensajeDeError(e)) });
  }

  protected elegir(evento: Event): void {
    this.comprobante = (evento.target as HTMLInputElement).files?.[0] ?? null;
  }

  protected valido(): boolean {
    const transferencia = this.medio === 'TRANSFERENCIA';
    return !!this.importe && this.importe > 0 && (!transferencia || (!!this.referencia.trim() && !!this.comprobante));
  }

  protected solicitar(): void {
    this.error.set(null);
    this.aviso.set(null);
    this.enviando.set(true);
    const referencia = this.medio === 'TRANSFERENCIA' ? this.referencia.trim() : null;
    const archivo = this.comprobante;
    this.api.solicitarDeposito({ importe: this.importe!, medio: this.medio, referencia }, this.clave).pipe(
      switchMap((s) => (archivo ? this.api.subirComprobantes('DEPOSITO', s.id, [archivo]) : of(null))),
    ).subscribe({
      next: () => {
        this.clave = crypto.randomUUID();
        this.importe = null;
        this.referencia = '';
        this.comprobante = null;
        this.enviando.set(false);
        this.aviso.set('Depósito registrado. Se sumará a su saldo cuando el personal lo confirme.');
        this.ngOnInit();
      },
      error: (e) => {
        // Si el aviso se creó pero falló el comprobante, se puede adjuntar desde la tabla.
        this.clave = crypto.randomUUID();
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
        this.ngOnInit();
      },
    });
  }

  protected adjuntar(s: SolicitudDeposito, evento: Event): void {
    const archivo = (evento.target as HTMLInputElement).files?.[0];
    if (!archivo) {
      return;
    }
    this.error.set(null);
    this.api.subirComprobantes('DEPOSITO', s.id, [archivo]).subscribe({
      next: () => {
        this.aviso.set('Comprobante adjuntado.');
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
