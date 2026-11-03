import { CurrencyPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { switchMap } from 'rxjs';

import { AuthService } from '../../core/auth/auth';
import { ApiService, Cuenta } from '../../core/services/api';
import { mensajeDeError } from '../../shared/utils/errores';

/** Cierre de la cuenta principal. Antes estaba en «Mi cuenta»; el saldo y sus acciones están en el resumen. */
@Component({
  selector: 'app-cerrar-cuenta',
  imports: [CurrencyPipe],
  template: `
    @if (cuenta(); as c) {
      <section class="tarjeta cierre-cuenta">
        <h2>Cerrar la cuenta</h2>
        <p class="suave">Cuenta {{ c.numero }} · {{ c.titular }}</p>
        <p class="suave">Depósito reembolsable</p>
        <p class="importe">{{ c.depositoReembolsable | currency: 'USD' }}</p>
        <p class="suave">Se le devuelve al cerrar la cuenta. No forma parte de su saldo disponible. Antes debe retirar su saldo y no tener depósitos, pagos ni retiros pendientes.</p>
        @if (cerrando()) {
          <div class="acciones">
            <button type="button" class="peligro" (click)="cerrar()">Confirmar cierre</button>
            <button type="button" class="secundario" (click)="cerrando.set(false)">Volver</button>
          </div>
        } @else {
          <button type="button" class="enlace-discreto" (click)="cerrando.set(true)">Solicitar cierre de cuenta</button>
        }
      </section>
    } @else if (!error()) {
      <p class="cargando" role="status">Cargando…</p>
    }
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
  `,
})
export class CerrarCuenta implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly cuenta = signal<Cuenta | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly cerrando = signal(false);

  ngOnInit(): void {
    this.api.miCuenta().subscribe({
      next: (c) => this.cuenta.set(c),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected cerrar(): void {
    this.error.set(null);
    this.api.solicitarCierre().pipe(switchMap(() => this.auth.cargarYo())).subscribe({
      next: () => this.router.navigateByUrl('/activar-cuenta'),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
