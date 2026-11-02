import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../core/auth/auth';
import { ApiService, EstadoSimulacion, PersonaPrueba } from '../core/services/api';
import { DialogoService } from '../shared/components/dialogo';
import { mensajeDeError } from '../shared/utils/errores';

/**
 * Barra del simulador: iniciar y finalizar la simulación, adelantar la fecha y crear personas de prueba. Solo aparece
 * si el backend corre con el perfil simulacion; en otro caso la ruta responde 404 y no se muestra nada.
 */
@Component({
  selector: 'app-barra-simulacion',
  imports: [DatePipe, FormsModule],
  template: `
    @if (estado(); as e) {
      <aside class="simulacion" aria-label="Simulador">
        @if (e.activa) {
          <strong>Simulación en curso, con los datos copiados el {{ e.iniciadaEn | date: 'dd/MM/yyyy HH:mm' }}. Nada de lo que haga aquí cambia los datos reales.</strong>
          <span>Fecha simulada: {{ e.fecha | date: 'dd/MM/yyyy' : 'UTC' }} (+{{ e.diasAdelantados }} días)</span>
        } @else {
          <strong>Simulador sin datos. Pulse «Iniciar simulación» para trabajar sobre una copia de los datos reales de este momento.</strong>
        }
        @if (puedeUsar()) {
          @if (e.activa) {
            <button type="button" class="secundario" [disabled]="ocupado()" (click)="avanzarDias(1)">+1 día</button>
            <button type="button" class="secundario" [disabled]="ocupado()" (click)="avanzarDias(15)">+15 días</button>
            <button type="button" class="secundario" [disabled]="ocupado()" (click)="avanzarDias(30)">+30 días</button>
            <label>
              Ir a
              <input type="date" name="destinoSimulacion" [(ngModel)]="destino" />
            </label>
            <button type="button" class="secundario" [disabled]="ocupado() || !destino" (click)="avanzarFecha()">Avanzar</button>
            <button type="button" class="secundario" [disabled]="ocupado()" (click)="crearPersona()">+ Persona de prueba</button>
            <button type="button" class="secundario" [disabled]="ocupado()" (click)="iniciar()">Volver a copiar los datos</button>
            <button type="button" class="peligro" [disabled]="ocupado()" (click)="finalizar()">Finalizar simulación</button>
          } @else {
            <button type="button" [disabled]="ocupado()" (click)="iniciar()">Iniciar simulación</button>
          }
          @if (ocupado()) { <span class="cargando" role="status">Trabajando…</span> }
        }
        @if (creada(); as p) {
          <span class="exito m-0" role="status">{{ p.nombre }}: entre con {{ p.correo }} y la contraseña {{ p.contrasena }} (cuenta {{ p.cuenta }}).</span>
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
  private readonly dialogo = inject(DialogoService);

  protected readonly estado = signal<EstadoSimulacion | null>(null);
  protected readonly ocupado = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly creada = signal<PersonaPrueba | null>(null);
  protected destino = '';

  ngOnInit(): void {
    this.api.estadoSimulacion().subscribe({ next: (e) => this.estado.set(e), error: () => this.estado.set(null) });
  }

  protected puedeUsar(): boolean {
    return this.auth.tienePermiso('ADMIN_PARAMETROS') || this.auth.tienePermiso('USAR_SIMULADOR');
  }

  protected avanzarDias(dias: number): void {
    this.avanzar({ dias });
  }

  protected avanzarFecha(): void {
    this.avanzar({ fecha: this.destino });
  }

  protected async crearPersona(): Promise<void> {
    const nombre = await this.dialogo.pedirTexto('Nombre de la persona de prueba. Tendrá cuenta activa con 1000 USD; escriba «socio» al final para que sea socia (por ejemplo: Ana socio).', 'Crear');
    if (!nombre?.trim()) {
      return;
    }
    const socio = /\bsocio$/i.test(nombre.trim());
    const nombres = nombre.trim().replace(/\s*\bsocio$/i, '') || 'Prueba';
    this.ejecutar(this.api.crearPersonaPrueba(nombres, 1000, socio), (p) => this.creada.set(p as PersonaPrueba));
  }

  protected async iniciar(): Promise<void> {
    const mensaje = this.estado()?.activa
      ? '¿Volver a copiar los datos reales de este momento? Se pierde lo probado hasta ahora. Los datos reales no se tocan.'
      : '¿Iniciar una simulación? Se copian todos los datos reales de este momento (personas, cuentas, créditos, ahorros, caja). '
        + 'Podrá cambiar lo que quiera: los datos reales no se tocan.';
    if (await this.dialogo.confirmar(mensaje, 'Iniciar simulación')) {
      this.ejecutar(this.api.iniciarSimulacion(), (e) => {
        this.estado.set(e as EstadoSimulacion);
        this.creada.set(null);
      });
    }
  }

  protected async finalizar(): Promise<void> {
    const mensaje = '¿Finalizar la simulación? Se descarta todo lo que hizo aquí y la fecha vuelve a hoy. Los datos reales no cambiaron.';
    if (await this.dialogo.confirmar(mensaje, 'Finalizar simulación', true)) {
      this.ejecutar(this.api.finalizarSimulacion(), (e) => {
        this.estado.set(e as EstadoSimulacion);
        this.creada.set(null);
      });
    }
  }

  private avanzar(destino: { dias?: number; fecha?: string }): void {
    this.ejecutar(this.api.avanzarSimulacion(destino), (e) => {
      this.estado.set(e as EstadoSimulacion);
      this.destino = '';
    });
  }

  private ejecutar(accion: import('rxjs').Observable<unknown>, alTerminar: (r: unknown) => void): void {
    this.ocupado.set(true);
    this.error.set(null);
    accion.subscribe({
      next: (r) => {
        alTerminar(r);
        this.ocupado.set(false);
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
