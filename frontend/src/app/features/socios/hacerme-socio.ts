import { CurrencyPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';

import { AuthService } from '../../core/auth/auth';
import { ApiService, CondicionesSocio } from '../../core/services/api';
import { DialogoService } from '../../shared/components/dialogo';
import { llevarADepositarSiFaltaSaldo, mensajeDeError } from '../../shared/utils/errores';

/** Cómo un cliente se convierte en socio: pagando el aporte inicial desde su cuenta principal. */
@Component({
  selector: 'app-hacerme-socio',
  imports: [CurrencyPipe, RouterLink],
  template: `
    <h1>Convertirse en socio</h1>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }

    @if (auth.yo()?.esSocio) {
      <section class="tarjeta">
        <p class="exito" role="status">Ya es socio de Janery.</p>
        <a routerLink="/app/inversiones">Ver mi fondo y mis aportes</a>
      </section>
    } @else if (condiciones(); as c) {
      <section class="seccion">
        <h2 class="seccion-titulo">¿Cómo me hago socio?</h2>
        <p>Pagando el <strong>aporte inicial de {{ c.aporteInicial | currency: 'USD' }}</strong>. Sale de su cuenta principal y pasa a ser su capital en Janery.</p>
        <p class="suave"><strong>Importante:</strong> el aporte inicial no se retira. Si un día quiere dejar de ser socio, lo vende a otro socio, al precio que acuerden entre los dos.</p>
      </section>

      <div class="rejilla-dos mb-6">
        <section class="tarjeta">
          <h2>Beneficios</h2>
          <ul class="list-disc space-y-2 pl-5 text-sm">
            <li><strong>Recibe parte de las ganancias de Janery</strong>, según el valor de su fondo personal: intereses de los créditos, aperturas de cuenta y demás ingresos.</li>
            <li><strong>Su fondo crece</strong> con sus aportes y sus ganancias. Las ganancias de cada año se pueden retirar desde el 1 de enero siguiente.</li>
            <li><strong>Cupo emergente de socio</strong> de hasta {{ c.cupoEmergente | currency: 'USD' }}: pide dinero cuando lo necesite y paga interés solo por los días que lo usa.</li>
            <li><strong>Más créditos:</strong> además del crédito a letras normal, los de inversión, estudio y personalizado, con condiciones de socio y sin mora por atraso.</li>
            <li>Consulta el balance, los libros contables, la tesorería, la caja chica y los resultados de Janery.</li>
          </ul>
        </section>
        <section class="tarjeta">
          <h2>Compromisos</h2>
          <ul class="list-disc space-y-2 pl-5 text-sm">
            <li>Aporte mensual de {{ c.aporteObligatorio | currency: 'USD' }}, que también se suma a su fondo.</li>
            <li>Aporte a caja chica de {{ c.aporteCajaChica | currency: 'USD' }} al mes.</li>
            <li>Puede pagar ambos por adelantado, hasta todo el año.</li>
            <li>Si Janery tiene pérdidas, también se reparten entre los socios.</li>
          </ul>
        </section>
      </div>

      <section class="tarjeta seccion-accion estrecho">
        <h2>Hacerme socio</h2>
        <p>Saldo de su cuenta principal: <strong>{{ saldo() | currency: 'USD' }}</strong></p>
        @if (saldo() >= c.aporteInicial) {
          <button type="button" [disabled]="enviando()" (click)="hacerme(c)">Pagar {{ c.aporteInicial | currency: 'USD' }} y hacerme socio</button>
        } @else {
          <p class="suave">Le faltan {{ c.aporteInicial - saldo() | currency: 'USD' }}. Deposite primero en su cuenta principal.</p>
          <a class="boton" routerLink="/app/depositos">Depositar dinero</a>
        }
      </section>
    } @else if (!error()) {
      <p class="cargando" role="status">Cargando…</p>
    }
  `,
})
export class HacermeSocio implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly dialogo = inject(DialogoService);
  private readonly router = inject(Router);

  protected readonly condiciones = signal<CondicionesSocio | null>(null);
  protected readonly saldo = signal(0);
  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);
  private clave: string = crypto.randomUUID();

  ngOnInit(): void {
    this.api.condicionesSocio().subscribe({ next: (c) => this.condiciones.set(c), error: (e) => this.error.set(mensajeDeError(e)) });
    this.api.miCuenta().subscribe({ next: (c) => this.saldo.set(c.saldoDisponible), error: (e) => this.error.set(mensajeDeError(e)) });
  }

  protected async hacerme(c: CondicionesSocio): Promise<void> {
    const mensaje = `¿Pagar el aporte inicial de ${c.aporteInicial} USD desde su cuenta principal? No se puede retirar: solo venderlo a otro socio.`;
    if (!(await this.dialogo.confirmar(mensaje, 'Hacerme socio'))) {
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    // Se renueva el token: el nuevo lleva el acceso de socio a las finanzas.
    this.api.hacermeSocio(this.clave).pipe(switchMap(() => this.auth.renovar()), switchMap(() => this.auth.cargarYo())).subscribe({
      next: () => this.router.navigateByUrl('/app/inversiones'),
      error: (e) => {
        this.enviando.set(false);
        if (!llevarADepositarSiFaltaSaldo(e, this.router, 'el aporte inicial')) {
          this.error.set(mensajeDeError(e));
        }
      },
    });
  }
}
