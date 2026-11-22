import { CurrencyPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { environment } from '../../../environments/environment';
import { DialogoService } from '../../shared/components/dialogo';
import { mensajeDeError } from '../../shared/utils/errores';

interface AhorroCredito {
  id: number;
  codigo: string;
  importe: number;
  estado: string;
  ahorroActual: number;
  ahorroInicial: number;
  liberado: boolean;
}

/**
 * TEMPORAL (22/11/2026): corrige el ahorro letras de los créditos anteriores a Janery. Se borra junto con
 * AjusteAhorroLetrasController.java y su pestaña en datos-anteriores.ts cuando se terminen de corregir.
 */
@Component({
  selector: 'app-ajuste-ahorro-letras',
  imports: [CurrencyPipe, FormsModule],
  template: `
    <section class="tarjeta">
      <h2 class="seccion-titulo">Corregir ahorro letras</h2>
      <p class="suave">Para los créditos anteriores a Janery que quedaron con más ahorro letras del que de verdad tienen. Escriba lo que la persona tiene hoy ahorrado en cada crédito; si no tiene nada, ponga 0. No mueve dinero de la caja ni de su cuenta.</p>
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }
      <div class="tabla">
        <table>
          <thead><tr><th scope="col">Crédito</th><th scope="col" class="num">Importe</th><th scope="col">Estado</th><th scope="col" class="num">Ahorro letras hoy</th><th scope="col">Ahorro real</th><th scope="col"></th></tr></thead>
          <tbody>
            @for (c of creditos(); track c.id) {
              <tr>
                <td>{{ c.codigo }}</td>
                <td class="num">{{ c.importe | currency: 'USD' }}</td>
                <td>{{ c.estado }}</td>
                <td class="num">{{ c.ahorroActual | currency: 'USD' }}</td>
                @if (c.liberado) {
                  <td colspan="2" class="suave">Ya pasó a la cuenta principal</td>
                } @else {
                  <td><input type="number" min="0" step="any" [name]="'ahorro' + c.id" [attr.aria-label]="'Ahorro real de ' + c.codigo" [(ngModel)]="nuevos[c.id]" /></td>
                  <td><button type="button" [disabled]="enviando() || nuevos[c.id] === null || nuevos[c.id] === undefined" (click)="corregir(c)">Corregir</button></td>
                }
              </tr>
            } @empty {
              <tr><td colspan="6" class="vacio">Esta persona no tiene créditos a letras anteriores a Janery.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
})
export class AjusteAhorroLetras {
  readonly usuarioId = input.required<number>();

  private readonly http = inject(HttpClient);
  private readonly dialogo = inject(DialogoService);
  private readonly base = `${environment.apiBaseUrl}/admin/creditos/existentes`;

  protected readonly creditos = signal<AhorroCredito[]>([]);
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected nuevos: Record<number, number | null> = {};

  constructor() {
    effect(() => this.cargar(this.usuarioId()));
  }

  private cargar(usuarioId: number): void {
    this.http.get<AhorroCredito[]>(`${this.base}/personas/${usuarioId}/ahorro-letras`).subscribe({
      next: (l) => this.creditos.set(l),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected async corregir(c: AhorroCredito): Promise<void> {
    const valor = Number(this.nuevos[c.id]);
    if (!(valor >= 0)) {
      return;
    }
    if (!(await this.dialogo.confirmar(`¿Dejar el ahorro letras de ${c.codigo} en ${valor.toFixed(2)} USD? Hoy tiene ${c.ahorroActual.toFixed(2)} USD.`, 'Corregir'))) {
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    this.aviso.set(null);
    this.http.put<AhorroCredito>(`${this.base}/creditos/${c.id}/ahorro-letras`, { ahorro: valor, motivo: 'Ahorro letras no cobrado antes de Janery' }).subscribe({
      next: (r) => {
        this.enviando.set(false);
        this.creditos.update((l) => l.map((x) => (x.id === r.id ? r : x)));
        this.nuevos[c.id] = null;
        this.aviso.set(`${r.codigo}: ahorro letras corregido a ${r.ahorroActual.toFixed(2)} USD.`);
      },
      error: (e) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }
}
