import { Component, inject, input, OnInit, signal } from '@angular/core';

import { ApiService, Comprobante } from '../../core/services/api';
import { descargar } from '../../shared/utils/descarga';
import { mensajeDeError } from '../../shared/utils/errores';

/** Comprobantes de un pago, para revisarlos antes de confirmar que el dinero llegó. */
@Component({
  selector: 'app-comprobantes',
  template: `
    @for (c of lista(); track c.id) {
      <button type="button" class="enlace-boton block" (click)="bajar(c)">📎 {{ c.nombre }}</button>
    } @empty {
      <span class="suave">{{ cargado() ? 'Sin comprobante' : '…' }}</span>
    }
    @if (error()) { <span class="negativo text-xs">{{ error() }}</span> }
  `,
})
export class Comprobantes implements OnInit {
  readonly tipo = input.required<'APERTURA' | 'DEPOSITO'>();
  readonly referenciaId = input.required<number>();

  private readonly api = inject(ApiService);
  protected readonly lista = signal<Comprobante[]>([]);
  protected readonly cargado = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.comprobantesAdmin(this.tipo(), this.referenciaId()).subscribe({
      next: (l) => {
        this.lista.set(l);
        this.cargado.set(true);
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected bajar(c: Comprobante): void {
    this.api.descargarComprobante(c.id).subscribe({ next: (b) => descargar(b, c.nombre), error: (e) => this.error.set(mensajeDeError(e)) });
  }
}
