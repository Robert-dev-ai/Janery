import { Component, computed, ElementRef, HostListener, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Bell, LucideAngularModule } from 'lucide-angular';

import { AuthService } from '../core/auth/auth';
import { ApiService } from '../core/services/api';
import { alCambiar } from '../core/services/tiempo-real';

interface Pendiente {
  titulo: string;
  ruta: string;
  total: number;
}

/** No hay un servicio de notificaciones: en gestión muestra las tareas según los permisos; en el panel personal, lo que atañe a la persona. */
@Component({
  selector: 'app-avisos-pendientes',
  imports: [LucideAngularModule, RouterLink],
  host: { class: 'avisos' },
  template: `
    <button type="button" class="icono-boton" aria-haspopup="menu" [attr.aria-expanded]="abierto()"
      [attr.aria-label]="'Notificaciones: ' + total()" title="Notificaciones" (click)="abierto.set(!abierto())">
      <lucide-icon [img]="iconos.Bell" [size]="20" aria-hidden="true" />
      @if (total() > 0) { <span class="contador">{{ total() }}</span> }
    </button>
    @if (abierto()) {
      <div class="ajustes-menu" role="menu" aria-label="Notificaciones">
        @for (p of conTareas(); track p.titulo) {
          <a role="menuitem" [routerLink]="p.ruta" (click)="abierto.set(false)">{{ p.total }} · {{ p.titulo }}</a>
        } @empty {
          <p class="ajustes-correo">No hay notificaciones.</p>
        }
      </div>
    }
  `,
})
export class AvisosPendientes implements OnInit {
  readonly gestion = input(false);

  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly elemento = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly iconos = { Bell };
  protected readonly abierto = signal(false);
  private readonly aperturas = signal(0);
  private readonly cierres = signal(0);
  private readonly creditos = signal(0);
  private readonly cupos = signal(0);
  private readonly retiros = signal(0);
  private readonly depositos = signal(0);

  private readonly misRetiros = signal(0);
  private readonly misDepositos = signal(0);
  private readonly misVencidas = signal(0);

  protected readonly conTareas = computed<Pendiente[]>(() => (!this.gestion() ? [
    { titulo: 'Letras atrasadas', ruta: '/app/creditos', total: this.misVencidas() },
    { titulo: 'Retiros en espera de entrega', ruta: '/app/retiros', total: this.misRetiros() },
    { titulo: 'Depósitos en espera de confirmación', ruta: '/app/depositos', total: this.misDepositos() },
  ] : [
    { titulo: 'Aperturas por confirmar', ruta: '/admin/cuentas', total: this.aperturas() },
    { titulo: 'Cierres por confirmar', ruta: '/admin/cuentas', total: this.cierres() },
    { titulo: 'Créditos por aprobar', ruta: '/admin/creditos', total: this.creditos() },
    { titulo: 'Cupos emergentes por aprobar', ruta: '/admin/creditos', total: this.cupos() },
    { titulo: 'Retiros por entregar', ruta: '/admin/solicitudes-retiro', total: this.retiros() },
    { titulo: 'Depósitos por confirmar', ruta: '/admin/solicitudes-deposito', total: this.depositos() },
  ]).filter((p) => p.total > 0));
  protected readonly total = computed(() => this.conTareas().reduce((suma, p) => suma + p.total, 0));

  constructor() {
    alCambiar(() => this.ngOnInit());
  }

  ngOnInit(): void {
    if (!this.gestion()) {
      if (this.auth.yo()?.estadoCuenta === 'ACTIVA') {
        this.api.misRetiros().subscribe({ next: (s) => this.misRetiros.set(s.filter((x) => x.estado === 'PENDIENTE').length), error: () => undefined });
        this.api.misDepositos().subscribe({ next: (s) => this.misDepositos.set(s.filter((x) => x.estado === 'PENDIENTE').length), error: () => undefined });
        this.api.misCreditos().subscribe({
          // Lo calcula el backend: en la simulación, su fecha no es la del navegador.
          next: (c) => this.misVencidas.set(c.reduce((suma, x) => suma + x.letrasAtrasadas, 0)),
          error: () => undefined,
        });
      }
      return;
    }
    if (this.auth.tienePermiso('GESTIONAR_CUENTAS')) {
      this.api.cuentas('PENDIENTE_PAGO', 0).subscribe({ next: (d) => this.aperturas.set(d.total), error: () => undefined });
      this.api.cuentas('PENDIENTE_CIERRE', 0).subscribe({ next: (d) => this.cierres.set(d.total), error: () => undefined });
      this.api.solicitudesRetiro('PENDIENTE', 0).subscribe({ next: (d) => this.retiros.set(d.total), error: () => undefined });
      this.api.solicitudesDeposito('PENDIENTE', 0).subscribe({ next: (d) => this.depositos.set(d.total), error: () => undefined });
    }
    if (this.auth.tienePermiso('APROBAR_CREDITOS')) {
      this.api.creditosAdmin('SOLICITADO', 0).subscribe({ next: (d) => this.creditos.set(d.total), error: () => undefined });
      this.api.solicitudesCupoAdmin().subscribe({ next: (d) => this.cupos.set(d.length), error: () => undefined });
    }
  }

  @HostListener('document:click', ['$event'])
  protected alHacerClic(evento: Event): void {
    if (!this.elemento.nativeElement.contains(evento.target as Node)) {
      this.abierto.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  protected alPulsarEscape(): void {
    this.abierto.set(false);
  }
}
