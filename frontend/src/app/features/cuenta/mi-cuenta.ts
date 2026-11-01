import { CurrencyPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';

import { AuthService } from '../../core/auth/auth';
import { ApiService, Cuenta } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-mi-cuenta',
  imports: [CurrencyPipe, RouterLink],
  template: `
    <h1>Mi cuenta</h1>
    @if (cuenta(); as c) {
      <p class="suave">Cuenta {{ c.numero }} · {{ c.titular }}</p>
      <div class="importes">
        <section class="tarjeta">
          <h2>Saldo disponible</h2>
          <p class="importe">{{ c.saldoDisponible | currency: 'USD' }}</p>
          <a routerLink="/app/movimientos">Ver movimientos</a>
        </section>
        <section class="tarjeta">
          <h2>Depósito reembolsable</h2>
          <p class="importe">{{ c.depositoReembolsable | currency: 'USD' }}</p>
          <p class="suave">No forma parte del saldo disponible. Se devuelve al cerrar la cuenta.</p>
        </section>
      </div>
      <section class="tarjeta">
        <h2>Cerrar la cuenta</h2>
        <p class="suave">Debe retirar antes su saldo disponible.</p>
        <button type="button" class="peligro" (click)="cerrar()">Solicitar cierre</button>
      </section>
    } @else if (!error()) {
      <p role="status">Cargando…</p>
    }
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
  `,
})
export class MiCuenta implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly cuenta = signal<Cuenta | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    alCambiar(() => this.ngOnInit());
  }

  ngOnInit(): void {
    this.api.miCuenta().subscribe({
      next: (c) => this.cuenta.set(c),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected cerrar(): void {
    if (!confirm('¿Solicitar el cierre de su cuenta principal?')) {
      return;
    }
    this.error.set(null);
    this.api.solicitarCierre().pipe(switchMap(() => this.auth.cargarYo())).subscribe({
      next: () => this.router.navigateByUrl('/activar-cuenta'),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
