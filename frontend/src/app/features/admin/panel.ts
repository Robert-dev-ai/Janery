import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HandCoins, Landmark, LucideAngularModule, Receipt, TriangleAlert, Users, Wallet } from 'lucide-angular';
import { forkJoin } from 'rxjs';

import { AuthService } from '../../core/auth/auth';
import { ApiService, EstadoContable, EventoAuditoria, SeccionContable } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { BarraGrafico, GraficoBarras } from '../../shared/components/grafico-barras';
import { Indicador } from '../../shared/components/indicador';
import { mensajeDeError } from '../../shared/utils/errores';

const USD = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

const ESTADOS_CREDITO = [
  { estado: 'SOLICITADO', etiqueta: 'Por aprobar' },
  { estado: 'ACTIVO', etiqueta: 'Activos' },
  { estado: 'FINALIZADO', etiqueta: 'Finalizados' },
  { estado: 'RECHAZADO', etiqueta: 'Rechazados' },
];
const ESTADOS_CUENTA = [
  { estado: 'PENDIENTE_PAGO', etiqueta: 'Pago pendiente' },
  { estado: 'ACTIVA', etiqueta: 'Activas' },
  { estado: 'PENDIENTE_CIERRE', etiqueta: 'Cierre pendiente' },
];

/** Cada bloque se pide y se muestra solo si el permiso lo autoriza; los totales los calcula el backend. */
@Component({
  selector: 'app-admin-panel',
  imports: [CurrencyPipe, DatePipe, GraficoBarras, Indicador, LucideAngularModule, RouterLink],
  template: `
    <h1>Panel</h1>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }

    <div class="rejilla-indicadores">
      @if (saldoCaja() !== null) {
        <app-indicador titulo="Caja principal" ruta="/areas/tesoreria" [icono]="iconos.Landmark" [valor]="saldoCaja() | currency: 'USD'" />
      }
      @if (saldoCajaChica() !== null) {
        <app-indicador titulo="Caja chica" ruta="/areas/caja-chica" [icono]="iconos.Receipt" [valor]="saldoCajaChica() | currency: 'USD'" />
      }
      @if (socios() !== null) {
        <app-indicador titulo="Socios" ruta="/admin/retiros" tono="informacion" [icono]="iconos.Users" [valor]="socios()" />
      }
      @if (creditos().length) {
        <app-indicador titulo="Créditos activos" ruta="/admin/creditos" tono="informacion" [icono]="iconos.HandCoins" [valor]="creditos()[1].valor" />
      }
      @if (cuentas().length) {
        <app-indicador titulo="Cuentas activas" ruta="/admin/cuentas" tono="informacion" [icono]="iconos.Wallet" [valor]="cuentas()[1].valor" />
      }
    </div>

    <section class="tarjeta">
      <h2>Alertas</h2>
      @for (a of alertas(); track a.titulo) {
        <a class="alerta" [routerLink]="a.ruta">
          <lucide-icon [img]="iconos.TriangleAlert" [size]="16" aria-hidden="true" />
          <span>{{ a.titulo }}</span><strong>{{ a.total }}</strong>
        </a>
      } @empty {
        <p class="suave">No hay alertas ni tareas pendientes.</p>
      }
    </section>

    <div class="rejilla-dos">
      @if (creditos().length) {
        <section class="tarjeta">
          <h2>Créditos por estado</h2>
          <app-grafico-barras [datos]="creditos()" />
        </section>
      }
      @if (cuentas().length) {
        <section class="tarjeta">
          <h2>Cuentas por estado</h2>
          <app-grafico-barras [datos]="cuentas()" />
        </section>
      }
    </div>

    @if (contable(); as c) {
      <section class="tarjeta">
        <div class="encabezado-tarjeta"><h2>Resumen financiero · {{ c.periodo }}</h2><a routerLink="/admin/contabilidad/balance">Ver balance</a></div>
        @if (c.diferencia !== 0) {
          <p class="error" role="alert">El balance no cuadra: diferencia de {{ c.diferencia | currency: 'USD' }}.</p>
        }
        <div class="rejilla-indicadores">
          <div><p class="suave">Activos</p><p class="importe">{{ c.totalActivos | currency: 'USD' }}</p></div>
          <div><p class="suave">Cartera por cobrar</p><p class="importe">{{ valor(c.activoCorriente, '1.4') | currency: 'USD' }}</p></div>
          <div><p class="suave">Ahorros captados</p><p class="importe">{{ valor(c.pasivos, '2.1') + valor(c.pasivos, '2.2') | currency: 'USD' }}</p></div>
          <div><p class="suave">Aportes</p><p class="importe">{{ valor(c.capital, '3.1') + valor(c.capital, '3.2') | currency: 'USD' }}</p></div>
          <div><p class="suave">Resultado del mes</p><p class="importe">{{ c.resultadoMes | currency: 'USD' }}</p></div>
        </div>
      </section>
    }
    @if (ganancias().length) {
      <div class="rejilla-dos">
        <section class="tarjeta"><h2>Ganancias por mes</h2><app-grafico-barras [datos]="ganancias()" /></section>
        <section class="tarjeta"><h2>Gastos por mes</h2><app-grafico-barras [datos]="gastos()" /></section>
      </div>
    }

    @if (actividad(); as lista) {
      <section class="tarjeta">
        <div class="encabezado-tarjeta">
          <h2>Actividad reciente</h2>
          <a routerLink="/admin/auditoria">Ver auditoría</a>
        </div>
        @if (lista.length) {
          <div class="tabla">
            <table>
              <thead><tr><th>Fecha</th><th>Acción</th><th>Módulo</th></tr></thead>
              <tbody>
                @for (e of lista; track e.id) {
                  <tr>
                    <td>{{ e.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                    <td>{{ e.accion }}</td>
                    <td>{{ e.modulo }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        } @else {
          <p class="suave">Aún no hay actividad registrada.</p>
        }
      </section>
    }
  `,
})
export class AdminPanel implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  protected readonly iconos = { HandCoins, Landmark, Receipt, TriangleAlert, Users, Wallet };
  protected readonly saldoCaja = signal<number | null>(null);
  protected readonly saldoCajaChica = signal<number | null>(null);
  protected readonly socios = signal<number | null>(null);
  protected readonly creditos = signal<BarraGrafico[]>([]);
  protected readonly cuentas = signal<BarraGrafico[]>([]);
  protected readonly actividad = signal<EventoAuditoria[] | null>(null);
  protected readonly sociosConObligaciones = signal(0);
  protected readonly retiros = signal(0);
  protected readonly depositos = signal(0);
  protected readonly contable = signal<EstadoContable | null>(null);
  protected readonly ganancias = signal<BarraGrafico[]>([]);
  protected readonly gastos = signal<BarraGrafico[]>([]);
  protected readonly error = signal<string | null>(null);

  protected readonly alertas = computed(() => [
    { titulo: 'Créditos por aprobar', ruta: '/admin/creditos', total: this.creditos()[0]?.valor ?? 0 },
    { titulo: 'Aperturas por confirmar', ruta: '/admin/cuentas', total: this.cuentas()[0]?.valor ?? 0 },
    { titulo: 'Cierres por confirmar', ruta: '/admin/cuentas', total: this.cuentas()[2]?.valor ?? 0 },
    { titulo: 'Retiros por entregar', ruta: '/admin/solicitudes-retiro', total: this.retiros() },
    { titulo: 'Depósitos por confirmar', ruta: '/admin/solicitudes-deposito', total: this.depositos() },
    { titulo: 'Socios con obligaciones vencidas', ruta: '/admin/retiros', total: this.sociosConObligaciones() },
  ].filter((a) => a.total > 0));

  constructor() {
    alCambiar(() => this.ngOnInit());
  }

  protected valor(seccion: SeccionContable, codigo: string): number {
    return seccion.lineas.find((l) => l.codigo === codigo)?.valor ?? 0;
  }

  ngOnInit(): void {
    const fallo = (e: unknown) => this.error.set(mensajeDeError(e));
    if (this.auth.tienePermiso('CONSULTAR_CAJA')) {
      this.api.tesoreria().subscribe({ next: (d) => this.saldoCaja.set(d.saldo), error: fallo });
    }
    if (this.auth.tienePermiso('GESTIONAR_CAJA_CHICA')) {
      this.api.cajaChica(0).subscribe({ next: (d) => this.saldoCajaChica.set(d.saldo), error: fallo });
    }
    if (this.auth.tienePermiso('GESTIONAR_RESULTADOS')) {
      this.api.socios().subscribe({ next: (s) => {
        this.socios.set(s.length);
        this.sociosConObligaciones.set(s.filter((x) => x.obligacionesPendientes > 0).length);
      }, error: fallo });
    }
    if (this.auth.tienePermiso('APROBAR_CREDITOS')) {
      forkJoin(ESTADOS_CREDITO.map((e) => this.api.creditosAdmin(e.estado, 0))).subscribe({
        next: (r) => this.creditos.set(r.map((d, i) => ({ etiqueta: ESTADOS_CREDITO[i].etiqueta, valor: d.total, texto: `${d.total}` }))),
        error: fallo,
      });
    }
    if (this.auth.tienePermiso('GESTIONAR_CUENTAS')) {
      forkJoin(ESTADOS_CUENTA.map((e) => this.api.cuentas(e.estado, 0))).subscribe({
        next: (r) => this.cuentas.set(r.map((d, i) => ({ etiqueta: ESTADOS_CUENTA[i].etiqueta, valor: d.total, texto: `${d.total}` }))),
        error: fallo,
      });
    }
    if (this.auth.tienePermiso('GESTIONAR_CUENTAS')) {
      this.api.solicitudesRetiro('PENDIENTE', 0).subscribe({ next: (d) => this.retiros.set(d.total), error: fallo });
      this.api.solicitudesDeposito('PENDIENTE', 0).subscribe({ next: (d) => this.depositos.set(d.total), error: fallo });
    }
    if (this.auth.tienePermiso('CONSULTAR_CONTABILIDAD')) {
      this.api.estadoContable(null).subscribe({ next: (e) => this.contable.set(e), error: fallo });
      this.api.evolucionContable().subscribe({
        next: (m) => {
          this.ganancias.set(m.map((x) => ({ etiqueta: x.periodo, valor: x.ganancias, texto: USD.format(x.ganancias) })));
          this.gastos.set(m.map((x) => ({ etiqueta: x.periodo, valor: x.gastos, texto: USD.format(x.gastos) })));
        },
        error: fallo,
      });
    }
    if (this.auth.tienePermiso('CONSULTAR_AUDITORIA')) {
      this.api.auditoria(0).subscribe({ next: (d) => this.actividad.set(d.contenido.slice(0, 6)), error: fallo });
    }
  }
}
