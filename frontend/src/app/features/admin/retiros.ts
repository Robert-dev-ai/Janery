import { CurrencyPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';

import { ApiService, MenuObligaciones, Obligacion, ResumenSocio } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { DialogoService } from '../../shared/components/dialogo';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-admin-retiros',
  imports: [CurrencyPipe, FormsModule],
  template: `
    <h1>Retiros y descuentos de socios</h1>
    <p class="suave">Las ganancias de un año se liberan el 1 de enero siguiente. Usted decide a quién autoriza a retirar y qué se descuenta de lo que deba.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }

    @if (socios().length === 0) {
      <p class="vacio">Aún no hay socios.</p>
    } @else {
      <div class="tabla">
        <table>
          <thead><tr><th>Socio</th><th class="num">Ganancias liberadas</th><th class="num">Debe</th><th>Retiro</th><th>Acción</th></tr></thead>
          <tbody>
            @for (s of socios(); track s.socioId) {
              <tr>
                <td>{{ s.nombre }}</td>
                <td class="num">{{ s.gananciasDisponibles | currency: 'USD' }}</td>
                <td class="num">{{ s.obligacionesPendientes | currency: 'USD' }}</td>
                <td>{{ s.retiroAutorizado ? 'Autorizado' : 'Sin autorizar' }}</td>
                <td><button type="button" class="secundario" (click)="abrir(s.socioId)">Decidir</button></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }

    @if (menu(); as m) {
      <section class="tarjeta mt-4">
        <h2>{{ m.nombre }}</h2>
        <p>Ganancias liberadas: <strong>{{ m.gananciasDisponibles | currency: 'USD' }}</strong> · Debe: <strong>{{ m.total | currency: 'USD' }}</strong></p>

        @if (m.obligaciones.length) {
          <h3>Descontar de sus ganancias</h3>
          <form (ngSubmit)="descontar(m)">
            @for (o of m.obligaciones; track $index) {
              <div class="fila-opcion">
                <label>
                  <input type="checkbox" name="o{{ $index }}" [(ngModel)]="elegidas[$index]" />
                  {{ o.descripcion }}: {{ o.importe | currency: 'USD' }}
                </label>
                @if (o.tipo === 'CUOTA' && elegidas[$index]) {
                  <input type="number" min="0" step="1" name="i{{ $index }}"
                    aria-label="Importe a descontar de esta letra; vacío, completa" placeholder="Completa"
                    [(ngModel)]="importes[$index]" />
                }
              </div>
            }
            <p class="suave">Una letra puede cubrirse en parte: lo primero que se cobra es el capital.</p>
            <button type="submit" [disabled]="!hayElegidas()">Descontar lo elegido</button>
          </form>
        } @else {
          <p>Está al día.</p>
        }

        <h3>Retiro de ganancias de este año</h3>
        @if (m.retiroAutorizado) {
          <p>Autorizado.</p>
          <button type="button" class="peligro" (click)="revocar(m)">Quitar autorización</button>
        } @else {
          <button type="button" [disabled]="m.total > 0" (click)="autorizar(m)">Autorizar retiro</button>
          @if (m.total > 0) { <p class="suave">Debe estar al día para autorizarlo.</p> }
        }
      </section>
    }
  `,
})
export class AdminRetiros implements OnInit {
  private readonly api = inject(ApiService);
  private readonly dialogo = inject(DialogoService);

  protected readonly socios = signal<ResumenSocio[]>([]);
  protected readonly menu = signal<MenuObligaciones | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  protected elegidas: boolean[] = [];
  protected importes: (number | null)[] = [];

  // Una clave por intento: se renueva solo tras un éxito, para que reintentar no duplique.
  private clave: string = crypto.randomUUID();

  constructor() {
    alCambiar(() => this.actualizarEnVivo());
  }

  ngOnInit(): void {
    this.cargarSocios();
  }

  protected abrir(socioId: number): void {
    this.api.obligaciones(socioId).subscribe({
      next: (m) => {
        this.elegidas = m.obligaciones.map(() => false);
        this.importes = m.obligaciones.map(() => null);
        this.menu.set(m);
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  /** El menú abierto se actualiza solo si no hay nada marcado, para no perder lo que el presidente eligió. */
  private actualizarEnVivo(): void {
    this.cargarSocios();
    const abierto = this.menu();
    if (abierto && !this.hayElegidas()) {
      this.abrir(abierto.socioId);
    }
  }

  protected hayElegidas(): boolean {
    return this.elegidas.some((e) => e);
  }

  protected descontar(m: MenuObligaciones): void {
    const descuentos = m.obligaciones
      .map((o: Obligacion, i) => ({ o, i }))
      .filter(({ i }) => this.elegidas[i])
      .map(({ o, i }) => ({ tipo: o.tipo, periodo: o.periodo, cuotaId: o.cuotaId, importe: this.importes[i] || null }));
    this.ejecutar(this.api.descontarObligaciones(m.socioId, descuentos, this.clave), 'Descuento aplicado.', m.socioId, () => {
      this.clave = crypto.randomUUID();
    });
  }

  protected async autorizar(m: MenuObligaciones): Promise<void> {
    if (await this.dialogo.confirmar(`¿Autorizar a ${m.nombre} a retirar sus ganancias este año?`, 'Autorizar')) {
      this.ejecutar(this.api.autorizarRetiro(m.socioId), 'Retiro autorizado.', m.socioId);
    }
  }

  protected revocar(m: MenuObligaciones): void {
    this.ejecutar(this.api.revocarRetiro(m.socioId), 'Autorización retirada.', m.socioId);
  }

  private ejecutar(accion: Observable<unknown>, aviso: string, socioId: number, alTerminar?: () => void): void {
    this.error.set(null);
    this.aviso.set(null);
    accion.subscribe({
      next: () => {
        this.aviso.set(aviso);
        alTerminar?.();
        this.cargarSocios();
        this.abrir(socioId);
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  private cargarSocios(): void {
    this.api.socios().subscribe({
      next: (s) => this.socios.set(s),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
