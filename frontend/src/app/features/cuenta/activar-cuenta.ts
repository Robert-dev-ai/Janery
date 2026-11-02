import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { of, switchMap } from 'rxjs';

import { AuthService } from '../../core/auth/auth';
import { ApiService, Comprobante, Cuenta } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { CampoTelefono } from '../../shared/components/campo-telefono';
import { mensajeDeError } from '../../shared/utils/errores';
import { cedulaValida, erroresDeFormulario, telefonoValido } from '../../shared/utils/validadores';

const TIPOS_COMPROBANTE = 'application/pdf,image/jpeg,image/png,image/webp';

@Component({
  selector: 'app-activar-cuenta',
  imports: [CampoTelefono, ReactiveFormsModule],
  template: `
    <div class="estrecho">
      <h1>Activar cuenta</h1>

      @if (!auth.yo()?.tienePerfil) {
        <form class="tarjeta" [formGroup]="perfil" (ngSubmit)="guardarPerfil()">
          <h2>1. Sus datos</h2>
          <label>Nombres <input formControlName="nombres" autocomplete="given-name" maxlength="100" /></label>
          <label>Apellidos <input formControlName="apellidos" autocomplete="family-name" maxlength="100" /></label>
          <label>Cédula (10 números) <input formControlName="identificacion" inputmode="numeric" maxlength="10" (input)="soloNumeros($event)" /></label>
          <label for="telefono-activar">Teléfono</label>
          <app-campo-telefono class="mb-3 block" idCampo="telefono-activar" formControlName="telefono" />
          <div class="pie-formulario">
            <button type="submit" [disabled]="enviando()">Continuar</button>
            @if (errores().length) {
              <ul class="errores-formulario" role="alert">@for (e of errores(); track e) { <li>{{ e }}</li> }</ul>
            }
          </div>
        </form>
      } @else if (cuenta()?.estado === 'PENDIENTE_PAGO') {
        <section class="tarjeta">
          <h2>Solicitud recibida</h2>
          <p>Cuenta <strong>{{ cuenta()?.numero }}</strong>, pago por {{ cuenta()?.metodoPago === 'EFECTIVO' ? 'efectivo' : 'transferencia' }}.</p>
          <h3>Comprobante de pago</h3>
          @for (c of comprobantes(); track c.id) {
            <p class="suave">✓ {{ c.nombre }}</p>
          } @empty {
            <p [class.alerta-atraso]="cuenta()?.metodoPago === 'TRANSFERENCIA'" [class.suave]="cuenta()?.metodoPago !== 'TRANSFERENCIA'">
              {{ cuenta()?.metodoPago === 'TRANSFERENCIA' ? 'Falta el comprobante de su transferencia: sin él no se puede confirmar el pago.' : 'Si le dieron un recibo, puede adjuntarlo.' }}
            </p>
          }
          <label>Adjuntar comprobante (foto o PDF, hasta 10 MB)
            <input type="file" [accept]="tipos" [disabled]="enviando()" (change)="adjuntar($event)" />
          </label>
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
            <label>Número de la transferencia <input formControlName="referenciaPago" maxlength="100" /></label>
          }
          <label>Comprobante de pago {{ esTransferencia() ? '(obligatorio)' : '(si tiene uno)' }}
            <input type="file" [accept]="tipos" (change)="elegir($event)" />
          </label>
          <p class="suave">Todo dinero que entra a Janery desde fuera se respalda con su comprobante: foto o PDF, hasta 10 MB.</p>
          <button type="submit" [disabled]="(esTransferencia() && (!apertura.value.referenciaPago?.trim() || !archivo())) || enviando()">
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

  protected readonly tipos = TIPOS_COMPROBANTE;
  private static readonly ETIQUETAS: Record<string, string> = {
    nombres: 'Los nombres', apellidos: 'Los apellidos', identificacion: 'La cédula', telefono: 'El teléfono',
  };

  protected readonly perfil = this.fb.group({
    nombres: ['', [Validators.required, Validators.pattern(/\S/)]],
    apellidos: ['', [Validators.required, Validators.pattern(/\S/)]],
    identificacion: ['', [Validators.required, cedulaValida]],
    telefono: ['', [Validators.required, telefonoValido]],
  });
  protected readonly apertura = this.fb.group({
    metodoPago: ['EFECTIVO'],
    referenciaPago: [''],
  });
  private readonly metodo = signal('EFECTIVO');
  protected readonly esTransferencia = computed(() => this.metodo() === 'TRANSFERENCIA');
  protected readonly cuenta = signal<Cuenta | null>(null);
  protected readonly comprobantes = signal<Comprobante[]>([]);
  protected readonly archivo = signal<File | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);
  private readonly intentado = signal(false);

  constructor() {
    alCambiar(() => this.actualizar());
  }

  ngOnInit(): void {
    this.apertura.controls.metodoPago.valueChanges.subscribe((m) => this.metodo.set(m));
    this.actualizar();
  }

  protected errores(): string[] {
    return erroresDeFormulario(this.perfil, ActivarCuenta.ETIQUETAS, this.intentado());
  }

  protected soloNumeros(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const limpio = campo.value.replace(/\D/g, '').slice(0, 10);
    if (limpio !== campo.value) {
      campo.value = limpio;
      this.perfil.controls.identificacion.setValue(limpio);
    }
  }

  protected actualizar(): void {
    this.auth.cargarYo().subscribe((yo) => {
      if (yo.estadoCuenta === 'ACTIVA') {
        this.router.navigateByUrl('/app/inicio');
      } else if (yo.estadoCuenta) {
        this.api.miCuenta().subscribe((c) => {
          this.cuenta.set(c);
          if (c.estado === 'PENDIENTE_PAGO') {
            this.api.misComprobantes('APERTURA', c.id).subscribe((l) => this.comprobantes.set(l));
          }
        });
      }
    });
  }

  protected elegir(evento: Event): void {
    this.archivo.set((evento.target as HTMLInputElement).files?.[0] ?? null);
  }

  protected adjuntar(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const archivo = campo.files?.[0];
    const cuenta = this.cuenta();
    if (!archivo || !cuenta) {
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    this.api.subirComprobantes('APERTURA', cuenta.id, [archivo]).subscribe({
      next: (l) => {
        this.comprobantes.set(l);
        this.enviando.set(false);
        campo.value = '';
      },
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.enviando.set(false);
        campo.value = '';
      },
    });
  }

  protected guardarPerfil(): void {
    this.intentado.set(true);
    if (this.perfil.invalid) {
      this.perfil.markAllAsTouched();
      return;
    }
    const datos = this.perfil.getRawValue();
    this.ejecutar(this.api.completarPerfil({ ...datos, nombres: datos.nombres.trim(), apellidos: datos.apellidos.trim() })
      .pipe(switchMap(() => this.auth.cargarYo())));
  }

  protected solicitar(): void {
    const { metodoPago, referenciaPago } = this.apertura.getRawValue();
    const referencia = metodoPago === 'TRANSFERENCIA' ? referenciaPago.trim() : null;
    const archivo = this.archivo();
    this.ejecutar(this.api.solicitarApertura(metodoPago, referencia).pipe(
      // Si falla la subida, la solicitud ya existe: el comprobante se puede adjuntar en la pantalla siguiente.
      switchMap((c) => (archivo ? this.api.subirComprobantes('APERTURA', c.id, [archivo]) : of(null))),
      switchMap(() => this.auth.cargarYo()),
    ));
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
        this.actualizar();
      },
    });
  }
}
