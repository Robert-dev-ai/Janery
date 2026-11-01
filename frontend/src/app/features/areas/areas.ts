import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService, CargoVista } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { mensajeDeError } from '../../shared/utils/errores';

const FUNCIONES: Record<string, { ruta: string; titulo: string }> = {
  GESTIONAR_CUENTAS: { ruta: '/admin/cuentas', titulo: 'Cuentas' },
  APROBAR_CREDITOS: { ruta: '/admin/creditos', titulo: 'Aprobar créditos' },
  CONSULTAR_CAJA: { ruta: '/areas/tesoreria', titulo: 'Tesorería' },
  GESTIONAR_CAJA_CHICA: { ruta: '/areas/caja-chica', titulo: 'Caja chica' },
  GESTIONAR_RESULTADOS: { ruta: '/admin/resultados', titulo: 'Resultados y socios' },
  ADMIN_PARAMETROS: { ruta: '/admin/parametros', titulo: 'Parámetros' },
  ADMIN_USUARIOS: { ruta: '/admin/usuarios', titulo: 'Usuarios' },
  CONSULTAR_AUDITORIA: { ruta: '/admin/auditoria', titulo: 'Auditoría' },
  CONSULTAR_INFORMES: { ruta: '/admin/reportes', titulo: 'Informes' },
};

@Component({
  selector: 'app-areas',
  imports: [RouterLink],
  template: `
    <h1>Mis áreas de trabajo</h1>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    } @else if (areas(); as lista) {
      @for (a of lista; track a.codigo) {
        <section class="tarjeta">
          <h2>{{ a.nombre }}</h2>
          @if (funciones(a).length) {
            <div class="acciones">
              @for (f of funciones(a); track f.ruta) {
                <a [routerLink]="f.ruta">{{ f.titulo }}</a>
              }
            </div>
          } @else {
            <p class="suave">Este cargo aún no tiene funciones asignadas.</p>
          }
        </section>
      } @empty {
        <p>No tiene cargos asignados.</p>
      }
    } @else {
      <p role="status">Cargando…</p>
    }
  `,
})
export class Areas implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly areas = signal<CargoVista[] | null>(null);
  protected readonly error = signal<string | null>(null);

  constructor() {
    alCambiar(() => this.ngOnInit());
  }

  ngOnInit(): void {
    this.api.misAreas().subscribe({
      next: (a) => this.areas.set(a),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected funciones(area: CargoVista): { ruta: string; titulo: string }[] {
    return area.permisos.map((p) => FUNCIONES[p]).filter((f) => f !== undefined);
  }
}
