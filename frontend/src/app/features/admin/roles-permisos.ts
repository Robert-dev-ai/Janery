import { Component, inject, OnInit, signal } from '@angular/core';
import { forkJoin } from 'rxjs';

import { ApiService, CargoVista, PermisoVista } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-roles-permisos',
  template: `
    <h1>Cargos y permisos</h1>
    <p class="suave">Marque qué puede hacer cada cargo. El cambio se aplica en 15 minutos como máximo, o al volver a entrar.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    <div class="tabla">
      <table>
        <thead>
          <tr>
            <th scope="col">Permiso</th>
            @for (c of cargos(); track c.codigo) { <th scope="col">{{ c.nombre }}</th> }
          </tr>
        </thead>
        <tbody>
          @for (p of permisos(); track p.codigo) {
            <tr>
              <td>{{ p.descripcion }}</td>
              @for (c of cargos(); track c.codigo) {
                <td>
                  <input type="checkbox"
                    [attr.aria-label]="c.nombre + ': ' + p.descripcion"
                    [checked]="c.permisos.includes(p.codigo)"
                    [disabled]="protegido(c, p)"
                    (change)="cambiar(c, p, $any($event.target).checked)" />
                </td>
              }
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class RolesPermisos implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly cargos = signal<CargoVista[]>([]);
  protected readonly permisos = signal<PermisoVista[]>([]);
  protected readonly error = signal<string | null>(null);

  constructor() {
    alCambiar(() => this.cargar());
  }

  ngOnInit(): void {
    this.cargar();
  }

  protected protegido(c: CargoVista, p: PermisoVista): boolean {
    return c.codigo === 'PRESIDENCIA' && p.codigo === 'ADMIN_USUARIOS';
  }

  protected cambiar(c: CargoVista, p: PermisoVista, marcado: boolean): void {
    this.error.set(null);
    const accion = marcado ? this.api.concederPermiso(c.codigo, p.codigo) : this.api.retirarPermiso(c.codigo, p.codigo);
    accion.subscribe({
      next: () => this.cargar(),
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.cargar();
      },
    });
  }

  private cargar(): void {
    forkJoin([this.api.cargos(), this.api.permisos()]).subscribe({
      next: ([cargos, permisos]) => {
        this.cargos.set(cargos);
        this.permisos.set(permisos);
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }
}
