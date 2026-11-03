import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService, CargoVista } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { mensajeDeError } from '../../shared/utils/errores';

interface Funcion {
  ruta: string;
  titulo: string;
  detalle: string;
}

const FUNCIONES: Record<string, Funcion> = {
  GESTIONAR_CUENTAS: { ruta: '/admin/cuentas', titulo: 'Cuentas, depósitos y retiros', detalle: 'Confirma aperturas, depósitos y entregas de dinero.' },
  APROBAR_CREDITOS: { ruta: '/admin/creditos', titulo: 'Créditos', detalle: 'Tramita créditos en ventanilla y vota su aprobación.' },
  CONSULTAR_CAJA: { ruta: '/areas/tesoreria', titulo: 'Tesorería', detalle: 'Ve la caja principal y de quién es el dinero.' },
  GESTIONAR_CAJA_CHICA: { ruta: '/areas/caja-chica', titulo: 'Caja chica', detalle: 'Registra gastos y los aportes que recibe en persona.' },
  GESTIONAR_RESULTADOS: { ruta: '/admin/resultados', titulo: 'Resultados y socios', detalle: 'Ganancias, pérdidas, aportes iniciales y cierre del año.' },
  ADMIN_PARAMETROS: { ruta: '/admin/parametros', titulo: 'Parámetros', detalle: 'Cambia tasas, límites y valores del sistema.' },
  ADMIN_USUARIOS: { ruta: '/admin/usuarios', titulo: 'Usuarios', detalle: 'Cargos, socios, contraseñas y correos.' },
  CONSULTAR_AUDITORIA: { ruta: '/admin/auditoria', titulo: 'Auditoría', detalle: 'Todo lo que hizo cada persona en el sistema.' },
  CONSULTAR_INFORMES: { ruta: '/admin/reportes', titulo: 'Informes', detalle: 'Consulta y descarga informes de toda la organización.' },
  CONSULTAR_CONTABILIDAD: { ruta: '/admin/contabilidad/balance', titulo: 'Balance general', detalle: 'Balance, libro diario y libro mayor.' },
  REGISTRAR_CREDITOS_EXISTENTES: { ruta: '/admin/creditos', titulo: 'Créditos anteriores a Janery', detalle: 'Registra los créditos que ya existían, con sus condiciones y letras pagadas.' },
  REGISTRAR_ASIENTOS: { ruta: '/admin/contabilidad/registro', titulo: 'Registro manual', detalle: 'Registra ingresos, gastos y bienes a mano.' },
  VER_USO_APP: { ruta: '/admin/uso', titulo: 'Uso de la aplicación', detalle: 'Qué pantallas se usan más, para decidir qué mejorar.' },
  USAR_SIMULADOR: { ruta: '/admin/simulador', titulo: 'Simulador', detalle: 'Prueba créditos, ahorros y más sin tocar los datos reales.' },
  PUBLICAR_ANUNCIOS: { ruta: '/admin/panel', titulo: 'Aviso de la cabecera', detalle: 'Publica el aviso que corre arriba, con el lápiz junto al título.' },
};

/** En qué consiste cada cargo, en una frase. */
const DESCRIPCIONES: Record<string, string> = {
  PRESIDENCIA: 'Dirige Janery: tiene acceso a todo y es una de las tres firmas que aprueban cada crédito.',
  VICEPRESIDENCIA: 'Reemplaza a la presidencia y, como ella, puede dar la firma de dirección al aprobar créditos.',
  TESORERIA: 'Cuida el dinero de Janery: confirma depósitos y retiros, tramita los créditos y su firma es necesaria para aprobarlos.',
  COMITE_CREDITO: 'Analiza las solicitudes de crédito; su firma es necesaria para aprobarlas o rechazarlas.',
  SECRETARIA: 'Lleva las actas y la comunicación con los socios.',
  CAJA_CHICA: 'Administra la caja chica: registra sus gastos y recibe en persona los aportes de los socios.',
  ADMINISTRACION: 'Cuida el funcionamiento del sistema: mira qué se usa más, prueba en el simulador y propone mejoras.',
  ASUNTOS_SOCIALES: 'Organiza las actividades sociales de los socios.',
};

@Component({
  selector: 'app-areas',
  imports: [RouterLink],
  template: `
    <h1>Mis cargos</h1>
    <p class="suave">Lo que le corresponde por cada cargo y a qué pantallas tiene acceso.</p>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    } @else if (areas(); as lista) {
      @for (a of lista; track a.codigo) {
        <section class="cargo">
          <div>
            <h2>{{ a.nombre }}</h2>
            <p class="suave mb-0">{{ descripcion(a.codigo) }}</p>
          </div>
          <div>
            <h3 class="text-sm text-suave">Tiene acceso a</h3>
            @if (funciones(a).length) {
              <ul>
                @for (f of funciones(a); track f.ruta) {
                  <li><a [routerLink]="f.ruta"><span><strong>{{ f.titulo }}</strong><small>{{ f.detalle }}</small></span></a></li>
                }
              </ul>
            } @else {
              <p class="suave">Este cargo aún no tiene pantallas asignadas. La presidencia puede darle permisos en Roles y permisos.</p>
            }
          </div>
        </section>
      } @empty {
        <p class="vacio">No tiene cargos asignados.</p>
      }
    } @else {
      <p class="cargando" role="status">Cargando…</p>
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

  protected descripcion(codigo: string): string {
    return DESCRIPCIONES[codigo] ?? '';
  }

  protected funciones(area: CargoVista): Funcion[] {
    return area.permisos.map((p) => FUNCIONES[p]).filter((f) => f !== undefined);
  }
}
