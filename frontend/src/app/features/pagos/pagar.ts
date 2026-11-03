import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, debounceTime, distinctUntilChanged, of, Subject, switchMap } from 'rxjs';

import { AuthService } from '../../core/auth/auth';
import { ApiService, DestinoPago, MesAporte, OpcionesPago, PersonaPago, SolicitudDeposito } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { DialogoService } from '../../shared/components/dialogo';
import { Estado } from '../../shared/components/estado';
import { nombreFamilia } from '../../shared/utils/etiquetas';
import { mensajeDeError } from '../../shared/utils/errores';

const MESES = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];

interface Linea {
  destino: DestinoPago;
  para: string;
  concepto: string;
  importe: number;
}

/**
 * Pagar de una vez cuotas, ahorros y aportes. Un cliente paga lo suyo; un socio también lo de otras personas. El
 * comprobante es obligatorio: el personal lo revisa y, al confirmar, el dinero entra a la cuenta de cada beneficiario y
 * se paga cada cosa.
 */
@Component({
  selector: 'app-pagar',
  imports: [CurrencyPipe, DatePipe, Estado, FormsModule],
  template: `
    <h1>Pagar</h1>
    <p class="suave">Arme la lista de lo que va a pagar: letras, ahorros y aportes{{ esSocio() ? ', suyos o de otras personas' : '' }}. Al final adjunte el comprobante. Cuando el personal confirme que recibió el dinero, se paga cada cosa de la lista.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }

    <section class="tarjeta">
      <h2>1. ¿Qué va a pagar?</h2>
      <!-- Decisión del propietario: solo los socios pagan lo de otras personas. -->
      @if (esSocio()) {
        <div class="acciones mb-2" role="group" aria-label="De quién es el pago">
          <button type="button" [class.secundario]="paraOtra()" (click)="paraMi()">Algo mío</button>
          <button type="button" [class.secundario]="!paraOtra()" (click)="paraOtraPersona()">De otra persona</button>
        </div>
      }

      @if (paraOtra() && !opciones()) {
        <form class="herramientas" (ngSubmit)="buscar()">
          <label>Persona <input type="search" name="buscar" placeholder="Escriba al menos 3 letras del nombre, o la cédula" [(ngModel)]="texto" (ngModelChange)="alEscribir($event)" /></label>
        </form>
        @for (p of resultados(); track p.usuarioId) {
          <div class="fila-dato">
            <strong>{{ p.nombre }}</strong>
            <button type="button" (click)="elegirPersona(p)">Elegir</button>
          </div>
        } @empty {
          @if (buscado()) { <p class="vacio">Nadie con cuenta activa coincide con la búsqueda.</p> }
        }
      }

      @if (opciones(); as o) {
        @if (paraOtra()) {
          <div class="encabezado-tarjeta">
            <p class="m-0">Pago para <strong>{{ o.nombre }}</strong></p>
            <button type="button" class="secundario" (click)="otraPersona()">Elegir a otra persona</button>
          </div>
        }
        <form class="estrecho" (ngSubmit)="agregar()">
          <label>Concepto
            <select name="concepto" [ngModel]="concepto()" (ngModelChange)="elegirConcepto($event)">
              <option value="">Elija qué va a pagar…</option>
              <!-- Todas las categorías aparecen siempre; si no aplica, se dice por qué. -->
              <optgroup label="Créditos y letras">
                @for (c of o.creditos; track c.id) {
                  <option [value]="'C:' + c.id">Crédito {{ c.codigo }} · {{ familia(c.familia) }}{{ c.modalidad ? ' ' + c.modalidad.toLowerCase() : '' }}@if (c.proximaLetra) { · letra {{ c.proximaLetra | currency: 'USD' }}} · debe {{ c.totalPendiente | currency: 'USD' }}</option>
                } @empty {
                  <option disabled>{{ quien() }} créditos vigentes por pagar</option>
                }
              </optgroup>
              <optgroup label="Ahorros">
                @for (a of o.ahorros; track a.id) {
                  <option [value]="'A:' + a.id">Depósito al ahorro «{{ a.nombre }}»</option>
                } @empty {
                  <option disabled>{{ quien() }} ahorros abiertos (se abren en Ahorros)</option>
                }
              </optgroup>
              <optgroup label="Aportes de socio">
                @if (o.socio) {
                  <option value="APORTE_OBLIGATORIO">Aporte mensual ({{ o.aporteMensual | currency: 'USD' }} por mes)</option>
                  <option value="APORTE_PERSONAL">Aporte personal</option>
                  <option value="CAJA_CHICA">Caja chica ({{ o.aporteCajaChica | currency: 'USD' }} por mes)</option>
                } @else {
                  <option disabled>Solo para socios: {{ paraOtra() ? o.nombre + ' no es socio' : 'usted aún no es socio' }}</option>
                }
              </optgroup>
              <optgroup label="Cuenta principal">
                <option value="CUENTA">Depósito a la cuenta principal</option>
              </optgroup>
            </select>
          </label>

          @if (porMeses()) {
            <div class="meses" role="group" aria-label="Meses a pagar">
              @for (m of o.meses; track m.periodo) {
                <label [class.pagado]="mesNoDisponible(m)">
                  <input type="checkbox" [disabled]="mesNoDisponible(m)" [checked]="mesNoDisponible(m) || meses().has(m.periodo)" (change)="alternarMes(m.periodo)" />
                  {{ etiquetaMes(m.periodo) }}@if (mesPagado(m)) { ✓ }
                </label>
              }
            </div>
            <p class="suave">{{ meses().size }} {{ meses().size === 1 ? 'mes' : 'meses' }}: {{ importeMeses() | currency: 'USD' }}</p>
          } @else if (concepto()) {
            <label>Importe (USD) <input type="number" min="0" step="any" name="importe" placeholder="0" [(ngModel)]="importe" /></label>
            @if (creditoElegido(); as c) {
              <p class="suave">Falta en total {{ c.totalPendiente | currency: 'USD' }}. Si paga más que la letra, lo que sobra adelanta las siguientes.</p>
            }
          }
          <button type="submit" [disabled]="!puedeAgregar()">Agregar a la lista</button>
        </form>
      } @else if (cargandoOpciones()) {
        <p class="cargando" role="status">Cargando…</p>
      }
    </section>

    <section class="tarjeta">
      <h2>2. Lo que va a pagar</h2>
      @if (lineas().length) {
        <div class="tabla">
          <table>
            <thead><tr><th>Para</th><th>Concepto</th><th class="num">Importe</th><th></th></tr></thead>
            <tbody>
              @for (l of lineas(); track $index) {
                <tr>
                  <td>{{ l.para }}</td>
                  <td>{{ l.concepto }}</td>
                  <td class="num">{{ l.importe | currency: 'USD' }}</td>
                  <td><button type="button" class="enlace-boton" (click)="quitar($index)">Quitar</button></td>
                </tr>
              }
            </tbody>
            <tfoot><tr><th colspan="2">Total</th><th class="num">{{ total() | currency: 'USD' }}</th><th></th></tr></tfoot>
          </table>
        </div>
      } @else {
        <p class="vacio">Aún no agrega nada. Elija arriba qué va a pagar.</p>
      }
    </section>

    @if (lineas().length) {
      <form class="tarjeta estrecho" (ngSubmit)="enviar()">
        <h2>3. Comprobante de pago</h2>
        <label>¿Cómo entrega el dinero?
          <select name="medio" [(ngModel)]="medio">
            <option value="EFECTIVO">En efectivo</option>
            <option value="TRANSFERENCIA">Por transferencia</option>
          </select>
        </label>
        @if (medio === 'TRANSFERENCIA') {
          <label>Número de la transferencia <input name="referencia" maxlength="100" [(ngModel)]="referencia" /></label>
        }
        <label>Comprobante (obligatorio)
          <input #archivo type="file" multiple accept="application/pdf,image/jpeg,image/png,image/webp" (change)="elegirArchivos($event)" />
        </label>
        <p class="suave">Foto o PDF del comprobante o recibo, hasta 5 archivos de 10 MB cada uno.</p>
        <button type="submit" [disabled]="!listoParaEnviar() || enviando()">Enviar pago de {{ total() | currency: 'USD' }}</button>
      </form>
    }

    <h2>Mis pagos</h2>
    @if (pagos(); as lista) {
      @for (s of lista; track s.id) {
        <section class="tarjeta">
          <div class="encabezado-tarjeta">
            <p class="m-0"><span class="codigo">{{ s.codigo }}</span> · {{ s.fecha | date: 'short' : 'America/Guayaquil' }} · {{ s.medio === 'EFECTIVO' ? 'Efectivo' : 'Transferencia ' + s.referencia }}</p>
            <app-estado [valor]="s.estado" />
          </div>
          @for (d of s.destinos; track $index) {
            <div class="fila-dato"><span>{{ d.descripcion }}</span><strong class="num">{{ d.importe | currency: 'USD' }}</strong></div>
          }
          <div class="fila-dato"><strong>Total</strong><strong class="num">{{ s.importe | currency: 'USD' }}</strong></div>
          @if (s.motivo) { <p class="suave">{{ s.motivo }}</p> }
          @if (s.estado === 'PENDIENTE') {
            <button type="button" class="secundario" (click)="cancelar(s)">Cancelar pago</button>
          }
        </section>
      } @empty {
        <p class="vacio">Aún no ha hecho pagos desde aquí.</p>
      }
    } @else {
      <p class="cargando" role="status">Cargando…</p>
    }
  `,
})
export class Pagar implements OnInit {
  private readonly api = inject(ApiService);
  private readonly dialogo = inject(DialogoService);
  private readonly archivo = viewChild<ElementRef<HTMLInputElement>>('archivo');
  private readonly busquedas = new Subject<string>();
  /** Lo que se le puede pagar a cada persona ya consultada: no cambia hasta que el personal confirme un pago. */
  private readonly cache = new Map<number | 'yo', OpcionesPago>();

  private readonly auth = inject(AuthService);
  protected readonly esSocio = computed(() => !!this.auth.yo()?.esSocio);
  protected readonly paraOtra = signal(false);
  protected readonly resultados = signal<PersonaPago[]>([]);
  protected readonly buscado = signal(false);
  protected readonly opciones = signal<OpcionesPago | null>(null);
  protected readonly cargandoOpciones = signal(false);
  protected readonly concepto = signal('');
  protected readonly meses = signal(new Set<string>());
  protected readonly lineas = signal<Linea[]>([]);
  protected readonly pagos = signal<SolicitudDeposito[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);
  protected readonly enviando = signal(false);

  protected readonly total = computed(() => this.lineas().reduce((s, l) => s + l.importe, 0));
  protected readonly quien = computed(() => (this.paraOtra() ? `${this.opciones()?.nombre ?? 'La persona'} no tiene` : 'No tiene'));
  protected readonly porMeses = computed(() => this.concepto() === 'APORTE_OBLIGATORIO' || this.concepto() === 'CAJA_CHICA');
  protected readonly importeMeses = computed(() => {
    const o = this.opciones();
    const porMes = (this.concepto() === 'CAJA_CHICA' ? o?.aporteCajaChica : o?.aporteMensual) ?? 0;
    return this.meses().size * porMes;
  });
  protected readonly creditoElegido = computed(() =>
    this.concepto().startsWith('C:') ? this.opciones()?.creditos.find((c) => c.id === Number(this.concepto().slice(2))) ?? null : null);

  protected texto = '';
  protected importe: number | null = null;
  protected medio = 'EFECTIVO';
  protected referencia = '';
  private archivos: File[] = [];
  // Una clave por intento: se renueva solo tras un éxito, para que reintentar no duplique.
  private clave: string = crypto.randomUUID();

  constructor() {
    alCambiar(() => {
      this.cache.clear();
      this.cargarPagos();
    });
    this.busquedas.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap((t) => (t.length < 3 ? of(null) : this.api.buscarPersonasPago(t).pipe(catchError((e) => {
        this.error.set(mensajeDeError(e));
        return of(null);
      })))),
      takeUntilDestroyed(),
    ).subscribe((r) => {
      this.resultados.set(r ?? []);
      this.buscado.set(r !== null);
    });
  }

  ngOnInit(): void {
    this.paraMi();
    this.cargarPagos();
  }

  protected familia(codigo: string): string {
    return nombreFamilia(codigo);
  }

  protected paraMi(): void {
    this.paraOtra.set(false);
    this.cargarOpciones(null);
  }

  protected paraOtraPersona(): void {
    this.paraOtra.set(true);
    this.otraPersona();
  }

  protected otraPersona(): void {
    this.opciones.set(null);
    this.limpiarConcepto();
    this.resultados.set([]);
    this.buscado.set(false);
  }

  /** Busca al escribir, con una pausa corta; una búsqueda nueva descarta la anterior. */
  protected alEscribir(texto: string): void {
    this.busquedas.next(texto.trim());
  }

  protected buscar(): void {
    this.busquedas.next(this.texto.trim());
  }

  protected elegirPersona(p: PersonaPago): void {
    this.cargarOpciones(p.usuarioId);
  }

  private cargarOpciones(usuarioId: number | null): void {
    this.error.set(null);
    this.limpiarConcepto();
    const guardadas = this.cache.get(usuarioId ?? 'yo');
    this.opciones.set(guardadas ?? null);
    if (guardadas) {
      return;
    }
    this.cargandoOpciones.set(true);
    this.api.opcionesPago(usuarioId).subscribe({
      next: (o) => {
        this.cache.set(usuarioId ?? 'yo', o);
        this.opciones.set(o);
        this.cargandoOpciones.set(false);
      },
      error: (e) => {
        this.cargandoOpciones.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  protected elegirConcepto(valor: string): void {
    this.concepto.set(valor);
    this.meses.set(new Set());
    // Para un crédito se propone lo que falta de la próxima letra; se puede cambiar.
    this.importe = this.creditoElegido()?.proximaLetra ?? null;
  }

  private limpiarConcepto(): void {
    this.concepto.set('');
    this.meses.set(new Set());
    this.importe = null;
  }

  protected etiquetaMes(periodo: string): string {
    return MESES[Number(periodo.slice(5, 7)) - 1];
  }

  protected mesPagado(m: MesAporte): boolean {
    return this.concepto() === 'CAJA_CHICA' ? m.cajaChicaPagado : m.obligatorioPagado;
  }

  /** Pagado, o ya en la lista para esta misma persona. */
  protected mesNoDisponible(m: MesAporte): boolean {
    const o = this.opciones();
    const tipo = this.concepto();
    return this.mesPagado(m) || this.lineas().some((l) => l.destino.usuarioId === o?.usuarioId && l.destino.tipo === tipo
      && !!l.destino.periodos?.includes(m.periodo));
  }

  protected alternarMes(periodo: string): void {
    const nuevos = new Set(this.meses());
    if (nuevos.has(periodo)) {
      nuevos.delete(periodo);
    } else {
      nuevos.add(periodo);
    }
    this.meses.set(nuevos);
  }

  protected puedeAgregar(): boolean {
    if (!this.concepto()) {
      return false;
    }
    return this.porMeses() ? this.meses().size > 0 : !!this.importe && this.importe > 0;
  }

  protected agregar(): void {
    const o = this.opciones();
    if (!o || !this.puedeAgregar()) {
      return;
    }
    const valor = this.concepto();
    const para = this.paraOtra() ? o.nombre : 'Yo';
    let linea: Linea;
    if (valor.startsWith('C:')) {
      const c = this.creditoElegido()!;
      linea = { destino: { usuarioId: o.usuarioId, tipo: 'CUOTA_CREDITO', referenciaId: c.id, importe: this.importe! }, para,
        concepto: `Crédito ${c.codigo}`, importe: this.importe! };
    } else if (valor.startsWith('A:')) {
      const a = o.ahorros.find((x) => x.id === Number(valor.slice(2)))!;
      linea = { destino: { usuarioId: o.usuarioId, tipo: 'AHORRO', referenciaId: a.id, importe: this.importe! }, para,
        concepto: `Ahorro «${a.nombre}»`, importe: this.importe! };
    } else if (this.porMeses()) {
      const periodos = [...this.meses()].sort();
      const tipo = valor as 'APORTE_OBLIGATORIO' | 'CAJA_CHICA';
      linea = { destino: { usuarioId: o.usuarioId, tipo, periodos }, para,
        concepto: `${tipo === 'CAJA_CHICA' ? 'Caja chica' : 'Aporte mensual'}: ${periodos.map((p) => this.etiquetaMes(p)).join(', ')}`,
        importe: this.importeMeses() };
    } else {
      const tipo = valor as 'APORTE_PERSONAL' | 'CUENTA';
      linea = { destino: { usuarioId: o.usuarioId, tipo, importe: this.importe! }, para,
        concepto: tipo === 'CUENTA' ? 'Depósito a la cuenta principal' : 'Aporte personal', importe: this.importe! };
    }
    this.lineas.update((l) => [...l, linea]);
    this.aviso.set(null);
    this.limpiarConcepto();
  }

  protected quitar(indice: number): void {
    this.lineas.update((l) => l.filter((_, i) => i !== indice));
  }

  protected elegirArchivos(evento: Event): void {
    this.archivos = Array.from((evento.target as HTMLInputElement).files ?? []);
  }

  protected listoParaEnviar(): boolean {
    return this.lineas().length > 0 && this.archivos.length > 0 && (this.medio !== 'TRANSFERENCIA' || !!this.referencia.trim());
  }

  protected async enviar(): Promise<void> {
    const ok = await this.dialogo.confirmar(
      `¿Enviar el pago de ${this.total().toFixed(2)} USD? Se pagará cada cosa de la lista cuando el personal confirme que recibió el dinero.`,
      'Enviar pago');
    if (!ok) {
      return;
    }
    this.error.set(null);
    this.aviso.set(null);
    this.enviando.set(true);
    const referencia = this.medio === 'TRANSFERENCIA' ? this.referencia.trim() : null;
    this.api.pagar(this.lineas().map((l) => l.destino), this.medio, referencia, this.archivos, this.clave).subscribe({
      next: (r) => {
        this.clave = crypto.randomUUID();
        this.enviando.set(false);
        this.lineas.set([]);
        this.archivos = [];
        this.referencia = '';
        const entrada = this.archivo()?.nativeElement;
        if (entrada) {
          entrada.value = '';
        }
        this.aviso.set(`Pago ${r.codigo} enviado. Se aplicará cuando el personal confirme que recibió el dinero.`);
        this.cargarPagos();
      },
      error: (e) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  private cargarPagos(): void {
    this.api.misDepositos().subscribe({
      next: (s) => this.pagos.set(s.filter((x) => x.pago)),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected async cancelar(s: SolicitudDeposito): Promise<void> {
    if (await this.dialogo.confirmar(`¿Cancelar el pago ${s.codigo}?`, 'Cancelar pago', true)) {
      this.error.set(null);
      this.api.cancelarDeposito(s.id).subscribe({ next: () => this.cargarPagos(), error: (e) => this.error.set(mensajeDeError(e)) });
    }
  }
}
