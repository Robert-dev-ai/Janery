import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../core/auth/auth';
import { ApiService, EstadoSimulacion } from '../core/services/api';
import { mensajeDeError } from '../shared/utils/errores';

/** Solo aparece si el backend corre con el perfil simulacion; en otro caso la ruta responde 404 y no se muestra nada. */
@Component({
  selector: 'app-barra-simulacion',
  imports: [DatePipe, FormsModule],
  template: `
    @if (estado(); as e) {
      <aside class="simulacion" aria-label="Simulación de fecha">
        <strong>Modo simulación: hoy es {{ e.fecha | date: 'dd/MM/yyyy' : 'UTC' }}</strong>
        <span>(+{{ e.diasAdelantados }} días)</span>
        @if (auth.tienePermiso('ADMIN_PARAMETROS')) {
          <button type="button" class="secundario" [disabled]="ocupado()" (click)="avanzarDias(1)">+1 día</button>
          <button type="button" class="secundario" [disabled]="ocupado()" (click)="avanzarDias(15)">+15 días</button>
          <button type="button" class="secundario" [disabled]="ocupado()" (click)="avanzarDias(30)">+30 días</button>
          <label>
            Ir a
            <input type="date" name="destinoSimulacion" [(ngModel)]="destino" />
          </label>
          <button type="button" class="secundario" [disabled]="ocupado() || !destino" (click)="avanzarFecha()">Avanzar</button>
        }
        @if (error(); as mensaje) {
          <span class="error" role="alert">{{ mensaje }}</span>
        }
      </aside>
    }
  `,
})
export class BarraSimulacion implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly estado = signal<EstadoSimulacion | null>(null);
  protected readonly ocupado = signal(false);
  protected readonly error = signal<string | null>(null);
  protected destino = '';

  ngOnInit(): void {
    this.api.estadoSimulacion().subscribe({ next: (e) => this.estado.set(e), error: () => this.estado.set(null) });
  }

  protected avanzarDias(dias: number): void {
    this.avanzar({ dias });
  }

  protected avanzarFecha(): void {
    this.avanzar({ fecha: this.destino });
  }

  private avanzar(destino: { dias?: number; fecha?: string }): void {
    this.ocupado.set(true);
    this.error.set(null);
    this.api.avanzarSimulacion(destino).subscribe({
      next: (e) => {
        this.estado.set(e);
        this.ocupado.set(false);
        this.destino = '';
        this.recargarPagina();
      },
      error: (e) => {
        this.ocupado.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  /** Vuelve a pedir los datos sin recargar el navegador, para no perder la sesión. */
  private recargarPagina(): void {
    const actual = this.router.url;
    this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => this.router.navigateByUrl(actual));
  }
}
