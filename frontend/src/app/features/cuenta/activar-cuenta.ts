import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { switchMap } from 'rxjs';

import { AuthService } from '../../core/auth/auth';
import { ApiService, Cuenta } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-activar-cuenta',
  imports: [ReactiveFormsModule],
  template: `
    <div class="estrecho">
      <h1>Activar cuenta</h1>

      @if (!auth.yo()?.tienePerfil) {
        <form class="tarjeta" [formGroup]="perfil" (ngSubmit)="guardarPerfil()">
          <h2>1. Sus datos</h2>
          <label>Nombres <input formControlName="nombres" autocomplete="given-name" /></label>
          <label>Apellidos <input formControlName="apellidos" autocomplete="family-name" /></label>
          <label>Cédula o identificación <input formControlName="identificacion" /></label>
          <label>Teléfono <input type="tel" formControlName="telefono" autocomplete="tel" /></label>
          <label>Dirección <input formControlName="direccion" autocomplete="street-address" /></label>
          <button type="submit" [disabled]="perfil.invalid || enviando()">Continuar</button>
        </form>
      } @else if (cuenta()?.estado === 'PENDIENTE_PAGO') {
        <section class="tarjeta">
          <h2>Solicitud recibida</h2>
          <p>Cuenta <strong>{{ cuenta()?.numero }}</strong>, pago por {{ cuenta()?.metodoPago === 'EFECTIVO' ? 'efectivo' : 'transferencia' }}.</p>
          <p>Su cuenta se activará cuando se confirme el pago. Vuelva a esta página más tarde.</p>
          <button type="button" class="secundario" (click)="actualizar()">Comprobar de nuevo</button>
        </section>
      } @else if (auth.yo()?.estadoCuenta === 'PENDIENTE_CIERRE') {
        <section class="tarjeta">
          <h2>Cierre en proceso</h2>
          <p>Su solicitud de cierre está pendiente de confirmación. Al confirmarse se le devolverá su depósito.</p>
        </section>
      } @else {
        <form class="tarjeta" [formGroup]="apertura" (ngSubmit)="solicitar()">
          <h2>2. Pago de apertura</h2>
          <label>Forma de pago
            <select formControlName="metodoPago">
              <option value="EFECTIVO">Efectivo</option>
              <option value="TRANSFERENCIA">Transferencia</option>
            </select>
          </label>
          @if (esTransferencia()) {
            <label>Número de la transferencia <input formControlName="referenciaPago" /></label>
          }
          <button type="submit" [disabled]="(esTransferencia() && !apertura.value.referenciaPago?.trim()) || enviando()">
            Solicitar apertura
          </button>
        </form>
      }
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    </div>
  `,
})
export class ActivarCuenta implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly perfil = this.fb.group({
    nombres: ['', Validators.required],
    apellidos: ['', Validators.required],
    identificacion: ['', Validators.required],
    telefono: ['', Validators.required],
    direccion: ['', Validators.required],
  });
  protected readonly apertura = this.fb.group({
    metodoPago: ['EFECTIVO'],
    referenciaPago: [''],
  });
  private readonly metodo = signal('EFECTIVO');
  protected readonly esTransferencia = computed(() => this.metodo() === 'TRANSFERENCIA');
  protected readonly cuenta = signal<Cuenta | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);

  constructor() {
    alCambiar(() => this.actualizar());
  }

  ngOnInit(): void {
    this.apertura.controls.metodoPago.valueChanges.subscribe((m) => this.metodo.set(m));
    this.actualizar();
  }

  protected actualizar(): void {
    this.auth.cargarYo().subscribe((yo) => {
      if (yo.estadoCuenta === 'ACTIVA') {
        this.router.navigateByUrl('/app/cuenta');
      } else if (yo.estadoCuenta) {
        this.api.miCuenta().subscribe((c) => this.cuenta.set(c));
      }
    });
  }

  protected guardarPerfil(): void {
    this.ejecutar(this.api.completarPerfil(this.perfil.getRawValue()).pipe(switchMap(() => this.auth.cargarYo())));
  }

  protected solicitar(): void {
    const { metodoPago, referenciaPago } = this.apertura.getRawValue();
    const referencia = metodoPago === 'TRANSFERENCIA' ? referenciaPago.trim() : null;
    this.ejecutar(this.api.solicitarApertura(metodoPago, referencia).pipe(switchMap(() => this.auth.cargarYo())));
  }

  private ejecutar(accion: ReturnType<AuthService['cargarYo']>): void {
    this.enviando.set(true);
    this.error.set(null);
    accion.subscribe({
      next: () => {
        this.enviando.set(false);
        this.actualizar();
      },
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.enviando.set(false);
      },
    });
  }
}
