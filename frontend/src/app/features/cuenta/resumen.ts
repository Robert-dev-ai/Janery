import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Banknote, BanknoteArrowDown, HandCoins, LucideAngularModule, PiggyBank, TrendingUp, Wallet } from 'lucide-angular';

import { AuthService } from '../../core/auth/auth';
import { Ahorro, ApiService, Credito, Cuenta, MovimientoCuenta, ResumenSocio } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { Estado } from '../../shared/components/estado';
import { GraficoBarras } from '../../shared/components/grafico-barras';
import { Indicador } from '../../shared/components/indicador';
import { nombreConcepto, nombreFamilia } from '../../shared/utils/etiquetas';
import { mensajeDeError } from '../../shared/utils/errores';

const USD = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

@Component({
  selector: 'app-resumen',
  imports: [CurrencyPipe, DatePipe, Estado, GraficoBarras, Indicador, LucideAngularModule, RouterLink],
  template: `
    <div class="encabezado-pagina">
      <h1>Resumen</h1>
      @if (cuenta(); as c) {
        <p class="saldo-titulo">Cuenta principal {{ c.numero }}<strong>{{ c.saldoDisponible | currency: 'USD' }}</strong></p>
      }
    </div>
    <div class="acciones-principales">
      <a class="boton" routerLink="/app/depositos"><lucide-icon [img]="iconos.BanknoteArrowDown" [size]="20" aria-hidden="true" /> Depositar</a>
      <a class="boton-claro" routerLink="/app/retiros"><lucide-icon [img]="iconos.Banknote" [size]="20" aria-hidden="true" /> Retirar</a>
    </div>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }

    @if (cuenta(); as c) {
      <div class="rejilla-indicadores">
        <app-indicador titulo="Saldo disponible" ruta="/app/cuenta" [icono]="iconos.Wallet" [valor]="c.saldoDisponible | currency: 'USD'" [nota]="'Cuenta ' + c.numero" />
        <app-indicador titulo="Total en créditos" ruta="/app/creditos" tono="advertencia" [icono]="iconos.HandCoins" [valor]="totalDeuda() | currency: 'USD'" nota="Lo que debe en total" />
        <app-indicador titulo="Total en ahorros" ruta="/app/ahorros" [icono]="iconos.PiggyBank" [valor]="totalAhorros() | currency: 'USD'" nota="Sin contar ahorro letras" />
        @if (socio(); as s) {
          <app-indicador titulo="Fondo personal" ruta="/app/inversiones" tono="informacion" [icono]="iconos.TrendingUp" [valor]="s.valorActual | currency: 'USD'"
            [nota]="variacion(s) + ' · Aporte de ' + s.periodoActual + (s.obligatorioDelMesPagado ? ' pagado' : ' pendiente')" />
        }
      </div>
    } @else if (!error()) {
      <p class="cargando" role="status">Cargando…</p>
    }

    @if (atrasadas() > 0) {
      <p class="alerta-atraso" role="alert">Tiene {{ atrasadas() }} {{ atrasadas() === 1 ? 'letra atrasada' : 'letras atrasadas' }}.
        @if (mora() > 0) { La mora acumulada es de {{ mora() | currency: 'USD' }} y sigue subiendo cada día. }
        <a routerLink="/app/creditos">Ver créditos</a></p>
    }

    <div class="rejilla-dos">
      <section class="tarjeta">
        <div class="encabezado-tarjeta"><h2>Créditos y cuotas</h2><a routerLink="/app/creditos">Ver créditos</a></div>
        @for (c of creditos(); track c.id) {
          <div class="fila-dato">
            <div>
              <strong>{{ familia(c.familia) }} {{ c.modalidad ?? '' }}</strong>
              <p class="suave">
                Pendiente: {{ c.totalPendiente | currency: 'USD' }}
                @if (c.letrasAtrasadas > 0) { · <span class="negativo">{{ c.letrasAtrasadas }} atrasada{{ c.letrasAtrasadas === 1 ? '' : 's' }}</span> }
                @if (proximaLetra(c); as l) {
                  · Próxima letra: {{ l.total | currency: 'USD' }}, vence {{ l.vencimiento | date: 'dd/MM/yyyy' : 'UTC' }}
                }
              </p>
            </div>
            <app-estado [valor]="c.estado" />
          </div>
        } @empty {
          <p class="suave">No tiene créditos vigentes.</p>
        }
      </section>

      <section class="tarjeta">
        <div class="encabezado-tarjeta"><h2>Ahorros</h2><a routerLink="/app/ahorros">Ver ahorros</a></div>
        @for (a of ahorros(); track a.id) {
          <div class="fila-dato">
            <span>{{ a.nombre }}</span>
            <strong class="num">{{ a.saldo | currency: 'USD' }}</strong>
          </div>
        } @empty {
          <p class="suave">Aún no tiene ahorros.</p>
        }
      </section>

      @if (composicion().length) {
        <section class="tarjeta">
          <div class="encabezado-tarjeta"><h2>Composición de su fondo</h2><a routerLink="/app/inversiones">Ver aportes</a></div>
          <app-grafico-barras [datos]="composicion()" />
        </section>
      }

      <section class="tarjeta">
        <div class="encabezado-tarjeta"><h2>Últimos movimientos</h2><a routerLink="/app/movimientos">Ver historial</a></div>
        @for (m of movimientos(); track m.id) {
          <div class="fila-dato">
            <div>
              <span>{{ concepto(m.concepto) }}</span>
              @if (m.detalle) { <p class="suave">{{ m.detalle }}</p> }
              <p class="suave">{{ m.fecha | date: 'short' : 'America/Guayaquil' }}</p>
            </div>
            <strong class="num" [class.positivo]="m.sentido !== 'SALIDA'">{{ m.sentido === 'SALIDA' ? '−' : '+' }}{{ m.importe | currency: 'USD' }}</strong>
          </div>
        } @empty {
          <p class="suave">Aún no tiene movimientos.</p>
        }
      </section>
    </div>
  `,
})
export class Resumen implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  protected readonly familia = nombreFamilia;
  protected readonly concepto = nombreConcepto;
  protected readonly iconos = { Banknote, BanknoteArrowDown, HandCoins, PiggyBank, TrendingUp, Wallet };
  protected readonly cuenta = signal<Cuenta | null>(null);
  protected readonly creditos = signal<Credito[]>([]);
  protected readonly ahorros = signal<Ahorro[]>([]);
  protected readonly movimientos = signal<MovimientoCuenta[]>([]);
  protected readonly socio = signal<ResumenSocio | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly totalDeuda = computed(() => this.creditos().filter((c) => c.estado === 'ACTIVO').reduce((t, c) => t + c.totalPendiente, 0));
  protected readonly atrasadas = computed(() => this.creditos().reduce((t, c) => t + c.letrasAtrasadas, 0));
  protected readonly mora = computed(() => this.creditos().reduce((t, c) => t + c.moraPendiente, 0));
  protected readonly totalAhorros = computed(() => this.ahorros().filter((a) => a.estado === 'ACTIVA').reduce((t, a) => t + a.saldo, 0));

  protected readonly composicion = computed(() => {
    const s = this.socio();
    if (!s) {
      return [];
    }
    return [
      { etiqueta: 'Aportes iniciales', valor: s.totalInicial },
      { etiqueta: 'Aportes mensuales', valor: s.totalObligatorio },
      { etiqueta: 'Aportes personales', valor: s.totalPersonal },
      { etiqueta: 'Ganancias', valor: s.ganancias },
    ].map((b) => ({ ...b, texto: USD.format(b.valor) }));
  });

  constructor() {
    alCambiar(() => this.ngOnInit());
  }

  ngOnInit(): void {
    const fallo = (e: unknown) => this.error.set(mensajeDeError(e));
    this.api.miCuenta().subscribe({ next: (c) => this.cuenta.set(c), error: fallo });
    this.api.misCreditos().subscribe({
      next: (c) => this.creditos.set(c.filter((x) => x.estado === 'ACTIVO' || x.estado === 'SOLICITADO')),
      error: fallo,
    });
    this.api.misAhorros().subscribe({ next: (a) => this.ahorros.set(a), error: fallo });
    this.api.misMovimientos(0).subscribe({ next: (d) => this.movimientos.set(d.contenido.slice(0, 5)), error: fallo });
    if (this.auth.yo()?.esSocio) {
      this.api.miResumenSocio().subscribe({ next: (s) => this.socio.set(s), error: fallo });
    }
  }

  /** Cuánto subió o bajó el fondo este mes, con flecha: el sentido no depende del color. */
  protected variacion(s: ResumenSocio): string {
    const v = s.variacionMes ?? 0;
    const signo = v > 0 ? '▲ +' : v < 0 ? '▼ −' : '';
    const porcentaje = s.rendimientoMes === null || s.rendimientoMes === undefined ? '' : ` (${s.rendimientoMes > 0 ? '+' : ''}${s.rendimientoMes} %)`;
    return v === 0 ? 'Sin cambios este mes' : `${signo}${USD.format(Math.abs(v))}${porcentaje} este mes`;
  }

  protected proximaLetra(c: Credito) {
    return c.cuotas?.find((l) => !c.cuotasPagadas?.includes(l.numero));
  }
}
