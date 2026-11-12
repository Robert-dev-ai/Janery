import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { Component, computed, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, HistorialParametro, Parametro } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { mensajeDeError } from '../../shared/utils/errores';

const UNIDADES: Record<string, string> = {
  USD: 'USD',
  PCT: '%',
  PCT_DIARIO: '% diario',
  PCT_MENSUAL: '% mensual',
  PCT_ANUAL: '% anual',
  DIAS: 'días',
  MESES: 'meses',
  MES: 'mes del año',
  USOS_MES: 'veces al mes',
};

interface Subgrupo {
  titulo: string;
  /** Un parámetro entra al primer subgrupo cuyo filtro lo acepta. */
  incluye: (codigo: string) => boolean;
}

interface Grupo {
  id: string;
  titulo: string;
  detalle: string;
  incluye: (codigo: string) => boolean;
  subgrupos: Subgrupo[];
  /** Muestra las tasas y encajes por modalidad en un cuadro. */
  matrizLetras?: boolean;
}

const empieza = (...prefijos: string[]) => (codigo: string) => prefijos.some((p) => codigo.startsWith(p));

const GRUPOS: Grupo[] = [
  {
    id: 'cuenta', titulo: 'Cuenta principal', detalle: 'Pago de apertura de la cuenta y costo de los retiros a otros bancos.',
    incluye: empieza('APERTURA_', 'COSTO_TRANSFERENCIA'),
    subgrupos: [
      { titulo: 'Apertura de cuenta', incluye: empieza('APERTURA_') },
      { titulo: 'Retiros', incluye: empieza('COSTO_TRANSFERENCIA') },
    ],
  },
  {
    id: 'letras', titulo: 'Créditos a letras', detalle: 'Montos, plazos y desgravamen generales, suministros, y el interés y encaje de cada modalidad.',
    incluye: empieza('LETRAS_'), matrizLetras: true,
    subgrupos: [
      { titulo: 'Montos y plazos', incluye: empieza('LETRAS_IMPORTE_', 'LETRAS_PLAZO_') },
      { titulo: 'Desgravamen', incluye: empieza('LETRAS_DESGRAVAMEN') },
      { titulo: 'Suministros por modalidad (al inicio, no se devuelven)', incluye: empieza('LETRAS_SUMINISTROS_') },
    ],
  },
  {
    id: 'emergentes', titulo: 'Créditos emergentes', detalle: 'Emergente de clientes, cupo de socios y promociones de temporada.',
    incluye: empieza('EMERGENTE_', 'ESTACIONAL_'),
    subgrupos: [
      { titulo: 'Emergente de clientes', incluye: empieza('EMERGENTE_CLIENTE_') },
      { titulo: 'Cupo emergente de socios', incluye: empieza('EMERGENTE_SOCIO_') },
      { titulo: 'Promociones de temporada (socios)', incluye: empieza('ESTACIONAL_') },
    ],
  },
  {
    id: 'mora', titulo: 'Mora', detalle: 'Cargo por cada día de atraso de las letras de clientes.',
    incluye: empieza('MORA_'), subgrupos: [],
  },
  {
    id: 'ahorros', titulo: 'Ahorros', detalle: 'Interés del ahorro normal y tasas del plazo fijo según su duración.',
    incluye: empieza('AHORRO_', 'PLAZO_FIJO_'),
    subgrupos: [
      { titulo: 'Ahorro normal', incluye: empieza('AHORRO_') },
      { titulo: 'Plazo fijo', incluye: empieza('PLAZO_FIJO_') },
    ],
  },
  {
    id: 'socios', titulo: 'Socios y aportes', detalle: 'Aporte inicial, aporte mensual, caja chica y fondo de reserva.',
    incluye: empieza('APORTE_', 'FONDO_RESERVA'),
    subgrupos: [
      { titulo: 'Aportes', incluye: empieza('APORTE_') },
      { titulo: 'Fondo de reserva', incluye: empieza('FONDO_RESERVA') },
    ],
  },
];

const MODALIDADES: { codigo: string; nombre: string }[] = [
  { codigo: 'NORMAL', nombre: 'Normal' },
  { codigo: 'INVERSION', nombre: 'Inversión' },
  { codigo: 'ESTUDIO', nombre: 'Estudio' },
  { codigo: 'PERSONALIZADO', nombre: 'Personalizado' },
];

interface Edicion {
  parametro: Parametro;
  valor: number | null;
  vigenteDesde: string;
  motivo: string;
}

/** Orden natural: el plazo fijo a 3 meses antes que el de 12. */
function ordenar(a: Parametro, b: Parametro): number {
  return a.codigo.localeCompare(b.codigo, 'es', { numeric: true });
}

@Component({
  selector: 'app-admin-parametros',
  imports: [DatePipe, DecimalPipe, FormsModule, NgTemplateOutlet],
  template: `
    <h1>Parámetros</h1>
    <p class="suave">Valores que usa Janery para calcular. Los cambios solo afectan a operaciones futuras.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }

    <div class="herramientas">
      <label>Buscar un parámetro
        <input type="search" placeholder="Por ejemplo: mora, plazo fijo, encaje…" [ngModel]="busqueda()" (ngModelChange)="busqueda.set($event)" />
      </label>
    </div>
    @if (!busqueda().trim()) {
      <nav class="indice-parametros" aria-label="Temas">
        @for (g of grupos(); track g.id) {
          <a [href]="'#param-' + g.id" (click)="ir($event, g.id)">{{ g.titulo }} <span>{{ g.total }}</span></a>
        }
      </nav>
    }

    <div #panelEdicion>
      @if (edicion(); as e) {
        <form class="tarjeta seccion-accion estrecho" (ngSubmit)="guardar(e)">
          <h2>{{ e.parametro.nombre }}</h2>
          <p class="suave">{{ e.parametro.descripcion }}</p>
          <p>Valor actual: <strong>{{ e.parametro.vigente ? (e.parametro.vigente.valor | number: '1.0-6') : 'Sin valor' }} {{ unidad(e.parametro.unidad) }}</strong></p>
          <label>Nuevo valor ({{ unidad(e.parametro.unidad) }})
            <input type="number" min="0" step="any" name="valor" [(ngModel)]="e.valor" required />
          </label>
          <label>Vigente desde (vacío: ahora)
            <input type="datetime-local" name="desde" [(ngModel)]="e.vigenteDesde" />
          </label>
          <label>Motivo <input name="motivo" maxlength="300" [(ngModel)]="e.motivo" required /></label>
          <div class="acciones">
            <button type="submit" [disabled]="e.valor === null || !e.motivo.trim()">Guardar</button>
            <button type="button" class="secundario" (click)="edicion.set(null)">Cancelar</button>
          </div>
        </form>
      }

      @if (historial(); as h) {
        <section class="tarjeta">
          <h2>Historial de {{ historialDe() }}</h2>
          <div class="tabla">
            <table>
              <thead><tr><th scope="col">Registrado</th><th scope="col">Vigente desde</th><th scope="col" class="num">Anterior</th><th scope="col" class="num">Nuevo</th><th scope="col">Motivo</th></tr></thead>
              <tbody>
                @for (x of h; track $index) {
                  <tr>
                    <td>{{ x.fecha | date: 'short' : 'America/Guayaquil' }}</td>
                    <td>{{ x.vigenteDesde | date: 'short' : 'America/Guayaquil' }}</td>
                    <td class="num">{{ x.valorAnterior ?? '—' }}</td>
                    <td class="num">{{ x.valorNuevo }}</td>
                    <td>{{ x.motivo }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <button type="button" class="secundario" (click)="historial.set(null)">Cerrar historial</button>
        </section>
      }
    </div>

    @if (busqueda().trim()) {
      <section class="seccion">
        <h2 class="seccion-titulo">Resultados de «{{ busqueda().trim() }}»</h2>
        @if (encontrados().length) {
          <ng-container *ngTemplateOutlet="tabla; context: { $implicit: encontrados() }" />
        } @else {
          <p class="vacio">Ningún parámetro coincide.</p>
        }
      </section>
    } @else {
      @for (g of grupos(); track g.id) {
        <details class="seccion grupo-parametros" [id]="'param-' + g.id" open>
          <summary><span class="seccion-titulo m-0">{{ g.titulo }}</span> <span class="suave">{{ g.total }} parámetros</span></summary>
          <p class="suave mt-2">{{ g.detalle }}</p>
          @for (s of g.subgrupos; track s.titulo) {
            <h3 class="subgrupo-parametros">{{ s.titulo }}</h3>
            <ng-container *ngTemplateOutlet="tabla; context: { $implicit: s.parametros }" />
          }
          @if (g.matrizLetras) {
            <h3 class="subgrupo-parametros">Interés y encaje por modalidad</h3>
            <p class="suave">Los clientes solo piden el crédito Normal; las demás modalidades son de socios.</p>
            <div class="tabla">
              <table>
                <thead><tr><th scope="col">Modalidad</th><th scope="col" class="num">Interés clientes</th><th scope="col" class="num">Encaje clientes</th><th scope="col" class="num">Interés socios</th><th scope="col" class="num">Encaje socios</th></tr></thead>
                <tbody>
                  @for (m of modalidades; track m.codigo) {
                    <tr>
                      <td><strong>{{ m.nombre }}</strong></td>
                      @for (c of ['INTERES_' + m.codigo + '_CLIENTE', 'ENCAJE_' + m.codigo + '_CLIENTE', 'INTERES_' + m.codigo + '_SOCIO', 'ENCAJE_' + m.codigo + '_SOCIO']; track c) {
                        <td class="num">
                          @if (porCodigo().get('LETRAS_' + c); as p) {
                            <button type="button" class="valor-editable" [title]="'Cambiar ' + p.nombre" (click)="editar(p)">
                              {{ p.vigente ? (p.vigente.valor | number: '1.0-4') : '—' }} {{ unidad(p.unidad) }}
                            </button>
                            <button type="button" class="enlace-boton text-xs" (click)="verHistorial(p)">historial</button>
                          } @else {
                            <span class="suave" title="Solo para socios">Solo socios</span>
                          }
                        </td>
                      }
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
          @if (g.sueltos.length) {
            @if (g.subgrupos.length) { <h3 class="subgrupo-parametros">Otros</h3> }
            <ng-container *ngTemplateOutlet="tabla; context: { $implicit: g.sueltos }" />
          }
        </details>
      }
    }

    <ng-template #tabla let-lista>
      <div class="tabla mb-4">
        <table>
          <thead><tr><th scope="col">Parámetro</th><th scope="col" class="num">Valor vigente</th><th scope="col">Desde</th><th scope="col">Programado</th><th scope="col">Acciones</th></tr></thead>
          <tbody>
            @for (p of lista; track p.id) {
              <tr>
                <td>{{ p.nombre }}<br /><small class="suave">{{ p.descripcion }}</small></td>
                <td class="num"><strong>{{ p.vigente ? (p.vigente.valor | number: '1.0-6') : 'Sin valor' }}</strong> {{ unidad(p.unidad) }}</td>
                <td>{{ p.vigente?.vigenteDesde | date: 'shortDate' : 'America/Guayaquil' }}</td>
                <td>
                  @if (p.programado; as f) {
                    {{ f.valor | number: '1.0-6' }} desde {{ f.vigenteDesde | date: 'short' : 'America/Guayaquil' }}
                  }
                </td>
                <td class="acciones">
                  <button type="button" (click)="editar(p)">Cambiar</button>
                  <button type="button" class="secundario" (click)="verHistorial(p)">Historial</button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </ng-template>
  `,
})
export class AdminParametros implements OnInit {
  private readonly api = inject(ApiService);
  private readonly panelEdicion = viewChild.required<ElementRef<HTMLElement>>('panelEdicion');

  protected readonly modalidades = MODALIDADES;
  protected readonly parametros = signal<Parametro[]>([]);
  protected readonly busqueda = signal('');
  protected readonly edicion = signal<Edicion | null>(null);
  protected readonly historial = signal<HistorialParametro[] | null>(null);
  protected readonly historialDe = signal('');
  protected readonly error = signal<string | null>(null);

  protected readonly porCodigo = computed(() => new Map(this.parametros().map((p) => [p.codigo, p])));

  protected readonly grupos = computed(() => {
    const usados = new Set<number>();
    const lista = [...this.parametros()].sort(ordenar);
    const tomar = (filtro: (codigo: string) => boolean, dentro: Parametro[]) => {
      const propios = dentro.filter((p) => !usados.has(p.id) && filtro(p.codigo));
      propios.forEach((p) => usados.add(p.id));
      return propios;
    };
    const grupos = GRUPOS.map((g) => {
      const delGrupo = lista.filter((p) => g.incluye(p.codigo));
      const subgrupos = g.subgrupos.map((s) => ({ titulo: s.titulo, parametros: tomar(s.incluye, delGrupo) })).filter((s) => s.parametros.length);
      // Las tasas por modalidad van en el cuadro: no se repiten abajo.
      if (g.matrizLetras) {
        tomar(empieza('LETRAS_INTERES_', 'LETRAS_ENCAJE_'), delGrupo);
      }
      const sueltos = tomar(() => true, delGrupo);
      return { ...g, subgrupos, sueltos, total: delGrupo.length };
    });
    const resto = lista.filter((p) => !usados.has(p.id));
    if (resto.length) {
      grupos.push({ id: 'otros', titulo: 'Otros', detalle: 'Parámetros que no pertenecen a los temas anteriores.', incluye: () => true,
        subgrupos: [], sueltos: resto, total: resto.length });
    }
    return grupos.filter((g) => g.total > 0);
  });

  protected readonly encontrados = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    return [...this.parametros()].sort(ordenar)
      .filter((p) => `${p.nombre} ${p.descripcion} ${p.codigo}`.toLowerCase().includes(texto));
  });

  constructor() {
    alCambiar(() => this.cargar());
  }

  ngOnInit(): void {
    this.cargar();
  }

  protected unidad(codigo: string): string {
    return UNIDADES[codigo] ?? codigo;
  }

  protected ir(evento: Event, id: string): void {
    evento.preventDefault();
    document.getElementById('param-' + id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  protected editar(parametro: Parametro): void {
    this.historial.set(null);
    this.edicion.set({ parametro, valor: parametro.vigente?.valor ?? null, vigenteDesde: '', motivo: '' });
    this.mostrarPanel();
  }

  protected guardar(e: Edicion): void {
    this.error.set(null);
    const desde = e.vigenteDesde ? new Date(e.vigenteDesde).toISOString() : null;
    this.api.cambiarParametro(e.parametro.id, e.valor!, desde, e.motivo.trim()).subscribe({
      next: () => {
        this.edicion.set(null);
        this.cargar();
      },
      error: (err) => this.error.set(mensajeDeError(err)),
    });
  }

  protected verHistorial(p: Parametro): void {
    this.api.historialParametro(p.id).subscribe({
      next: (h) => {
        this.edicion.set(null);
        this.historialDe.set(p.nombre);
        this.historial.set(h);
        this.mostrarPanel();
      },
      error: (err) => this.error.set(mensajeDeError(err)),
    });
  }

  /** El formulario aparece arriba: se lleva allí la vista, aunque se haya elegido el parámetro al final de la página. */
  private mostrarPanel(): void {
    setTimeout(() => this.panelEdicion().nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' }));
  }

  private cargar(): void {
    this.api.parametros().subscribe({
      next: (p) => this.parametros.set(p),
      error: (err) => this.error.set(mensajeDeError(err)),
    });
  }
}
