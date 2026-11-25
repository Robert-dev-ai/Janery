import { CurrencyPipe } from '@angular/common';
import { Component, computed, ElementRef, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, debounceTime, distinctUntilChanged, of, Subject, switchMap } from 'rxjs';

import { ApiService, CobroVentanilla, DestinoPago, FichaVentanilla, MesAporte, OrigenCobro, PersonaVentanilla, SolicitudRetiro } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { DialogoService } from '../../shared/components/dialogo';
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
 * Ventanilla de tesorería. Decisión del propietario: cada persona puede hacer sus movimientos, pero lo común es que se
 * lo pida al tesorero, que cobra y entrega dinero por cualquiera. Todo se aplica al momento. En efectivo la foto del
 * comprobante es opcional; en transferencia, obligatoria.
 */
@Component({
  selector: 'app-ventanilla',
  imports: [CurrencyPipe, FormsModule],
  template: `
    <h1>Ventanilla</h1>
    <p class="suave">Cobre o entregue dinero a cualquier persona con cuenta activa. Todo se aplica en el momento: hágalo cuando ya recibió o entregó el dinero.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }

    <section class="tarjeta">
      <h2>Persona</h2>
      @if (ficha(); as f) {
        <div class="encabezado-tarjeta">
          <div>
            <p class="m-0"><strong>{{ f.persona.nombre }}</strong>@if (f.persona.socio) { · socio }</p>
            <p class="m-0 suave">Cédula {{ f.persona.identificacion }} · cuenta <span class="codigo">{{ f.persona.numeroCuenta }}</span></p>
          </div>
          <button type="button" class="secundario" (click)="cambiarPersona()">Atender a otra persona</button>
        </div>
        <div class="fila-dato"><span>Saldo de la cuenta principal</span><strong class="num">{{ f.saldoCuenta | currency: 'USD' }}</strong></div>
        @for (a of f.ahorros; track a.id) {
          <div class="fila-dato"><span>Ahorro «{{ a.nombre }}» <span class="codigo">{{ a.codigo }}</span></span><strong class="num">{{ a.saldo | currency: 'USD' }}</strong></div>
        }
      } @else {
        <form class="herramientas" (ngSubmit)="buscar()">
          <label>Buscar <input type="search" name="buscar" placeholder="Nombre, cédula o número de cuenta (mínimo 3 caracteres)" [(ngModel)]="texto" (ngModelChange)="alEscribir($event)" /></label>
        </form>
        @for (p of resultados(); track p.usuarioId) {
          <div class="fila-dato">
            <span><strong>{{ p.nombre }}</strong>@if (p.socio) { · socio } <span class="suave">· {{ p.identificacion }} · {{ p.numeroCuenta }}</span></span>
            <button type="button" (click)="atender(p.usuarioId)">Atender</button>
          </div>
        } @empty {
          @if (buscado()) { <p class="vacio">Nadie con cuenta activa coincide con la búsqueda.</p> }
        }
        @if (cargandoFicha()) { <p class="cargando" role="status">Cargando…</p> }
      }
    </section>

    <div class="acciones mb-2" role="tablist" aria-label="Operación">
      <button type="button" role="tab" [attr.aria-selected]="modo() === 'cobrar'" [class.secundario]="modo() !== 'cobrar'" (click)="modo.set('cobrar')">Cobrar</button>
      <button type="button" role="tab" [attr.aria-selected]="modo() === 'entregar'" [class.secundario]="modo() !== 'entregar'" (click)="modo.set('entregar')">Entregar dinero</button>
    </div>

    @if (modo() === 'cobrar') {
      @if (recibo(); as r) {
        <section class="tarjeta">
          <h2>Cobro registrado</h2>
          <p class="exito" role="status">{{ r.codigo ? 'Pago ' + r.codigo + ' aplicado.' : 'Pagado con el saldo de ' + r.origen + '.' }}</p>
          @for (d of r.detalle; track $index) { <div class="fila-dato"><span>{{ d }}</span></div> }
          <div class="fila-dato"><strong>Total</strong><strong class="num">{{ r.importe | currency: 'USD' }}</strong></div>
          <button type="button" (click)="recibo.set(null)">Nuevo cobro</button>
        </section>
      } @else {
        @if (ficha(); as f) {
          <section class="tarjeta">
            <h2>1. ¿Qué paga {{ f.persona.nombre }}?</h2>
            <form class="estrecho" (ngSubmit)="agregar()">
              <label>Concepto
                <select name="concepto" [ngModel]="concepto()" (ngModelChange)="elegirConcepto($event)">
                  <option value="">Elija qué va a cobrar…</option>
                  <optgroup label="Créditos y letras">
                    @for (c of f.opciones.creditos; track c.id) {
                      <option [value]="'C:' + c.id">Crédito {{ c.codigo }} · {{ familia(c.familia) }}{{ c.modalidad ? ' ' + c.modalidad.toLowerCase() : '' }}@if (c.proximaLetra) { · letra {{ c.proximaLetra | currency: 'USD' }}} · debe {{ c.totalPendiente | currency: 'USD' }}</option>
                    } @empty {
                      <option disabled>No tiene créditos por pagar</option>
                    }
                  </optgroup>
                  <optgroup label="Ahorros">
                    @for (a of f.opciones.ahorros; track a.id) {
                      <option [value]="'A:' + a.id">Depósito al ahorro «{{ a.nombre }}»</option>
                    } @empty {
                      <option disabled>No tiene ahorros abiertos</option>
                    }
                  </optgroup>
                  <optgroup label="Aportes de socio">
                    @if (f.opciones.socio) {
                      <option value="APORTE_OBLIGATORIO">Aporte mensual ({{ f.opciones.aporteMensual | currency: 'USD' }} por mes)</option>
                      <option value="APORTE_PERSONAL">Aporte personal</option>
                      <option value="CAJA_CHICA">Caja chica ({{ f.opciones.aporteCajaChica | currency: 'USD' }} por mes)</option>
                    } @else {
                      <option disabled>Solo para socios</option>
                    }
                  </optgroup>
                  <optgroup label="Cuenta principal">
                    <option value="CUENTA">Depósito a la cuenta principal</option>
                  </optgroup>
                </select>
              </label>

              @if (porMeses()) {
                <div class="meses" role="group" aria-label="Meses a cobrar">
                  @for (m of f.opciones.meses; track m.periodo) {
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
              <button type="submit" [disabled]="!puedeAgregar()">Agregar al cobro</button>
            </form>
            <p class="suave">Para cobrar también lo de otra persona en el mismo cobro, use «Atender a otra persona»: la lista se conserva.</p>
          </section>
        } @else if (!lineas().length) {
          <p class="vacio">Busque a la persona que va a atender.</p>
        }

        @if (lineas().length) {
          <section class="tarjeta">
            <h2>2. Lo que se cobra</h2>
            <div class="tabla">
              <table>
                <thead><tr><th scope="col">Para</th><th scope="col">Concepto</th><th scope="col" class="num">Importe</th><th scope="col"></th></tr></thead>
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
                <tfoot><tr><th scope="row" colspan="2">Total</th><td class="num font-semibold">{{ total() | currency: 'USD' }}</td><td></td></tr></tfoot>
              </table>
            </div>
          </section>

          <form class="tarjeta estrecho" (ngSubmit)="cobrar()">
            <h2>3. ¿Cómo paga?</h2>
            <label>Quién entrega el dinero
              <select name="pagador" [ngModel]="pagadorId()" (ngModelChange)="elegirPagador($event)">
                @for (p of personasDelCobro(); track p.usuarioId) {
                  <option [ngValue]="p.usuarioId">{{ p.nombre }}</option>
                }
              </select>
            </label>
            <label>Forma de pago
              <select name="origen" [ngModel]="origen()" (ngModelChange)="origen.set($event)">
                <option value="EFECTIVO">En efectivo</option>
                <option value="TRANSFERENCIA">Por transferencia</option>
                @if (fichaPagador(); as fp) {
                  <option value="CUENTA">Con el saldo de su cuenta principal ({{ fp.saldoCuenta | currency: 'USD' }})</option>
                  @for (a of fp.ahorros; track a.id) {
                    <option [value]="'AHORRO:' + a.id">Con su ahorro «{{ a.nombre }}» ({{ a.saldo | currency: 'USD' }})</option>
                  }
                }
              </select>
            </label>
            @if (conSaldo()) {
              <p class="suave">Con el saldo de una cuenta o de un ahorro solo se paga lo de esa misma persona, y no entra dinero a la caja.</p>
              @if (saldoOrigen() < total()) {
                <p class="error" role="alert">El saldo ({{ saldoOrigen() | currency: 'USD' }}) no alcanza para el total.</p>
              }
            } @else {
              @if (origen() === 'TRANSFERENCIA') {
                <label>Número de la transferencia <input name="referencia" maxlength="100" [(ngModel)]="referencia" /></label>
              }
              <label>Comprobante ({{ origen() === 'TRANSFERENCIA' ? 'obligatorio' : 'opcional' }})
                <input #archivo type="file" multiple accept="application/pdf,image/jpeg,image/png,image/webp" (change)="elegirArchivos($event)" />
              </label>
              <p class="suave">Foto o PDF, hasta 5 archivos de 10 MB cada uno.{{ origen() === 'EFECTIVO' ? ' En efectivo no es obligatorio porque lo recibe en mano.' : '' }}</p>
            }
            <button type="submit" [disabled]="!listoParaCobrar() || enviando()">Cobrar {{ total() | currency: 'USD' }}</button>
          </form>
        }
      }
    } @else {
      @if (ficha(); as f) {
        <form class="tarjeta estrecho" (ngSubmit)="entregar()">
          <h2>Entregar dinero a {{ f.persona.nombre }}</h2>
          @if (entregado(); as e) {
            <p class="exito" role="status">Retiro {{ e.codigo }} entregado: {{ e.importe | currency: 'USD' }}{{ e.costo > 0 ? ' más ' + (e.costo | currency: 'USD') + ' de costo de transferencia' : '' }}.</p>
          }
          <label>Sale de
            <select name="origenRetiro" [(ngModel)]="origenRetiro">
              <option value="CUENTA">Cuenta principal ({{ f.saldoCuenta | currency: 'USD' }})</option>
              @for (a of f.ahorros; track a.id) {
                <option [value]="'AHORRO:' + a.id">Ahorro «{{ a.nombre }}» ({{ a.saldo | currency: 'USD' }})</option>
              }
            </select>
          </label>
          <label>Importe (USD) <input type="number" min="0" step="any" name="importeRetiro" placeholder="0" [(ngModel)]="importeRetiro" /></label>
          <label>¿Cómo lo entrega?
            <select name="medioRetiro" [(ngModel)]="medioRetiro">
              <option value="EFECTIVO">En efectivo</option>
              <option value="TRANSFERENCIA">Por transferencia</option>
            </select>
          </label>
          @if (medioRetiro === 'TRANSFERENCIA') {
            <label>Banco o cooperativa <input name="banco" maxlength="100" [(ngModel)]="banco" /></label>
            <label>Tipo de cuenta
              <select name="tipoCuenta" [(ngModel)]="tipoCuenta">
                <option value="AHORROS">Ahorros</option>
                <option value="CORRIENTE">Corriente</option>
              </select>
            </label>
            <label>Número de cuenta <input name="numeroCuenta" maxlength="40" [(ngModel)]="numeroCuenta" /></label>
            <label>Titular <input name="titular" maxlength="150" [(ngModel)]="titular" /></label>
            <label>Número de la transferencia (opcional) <input name="referenciaRetiro" maxlength="100" [(ngModel)]="referenciaRetiro" /></label>
            <p class="suave">A Pichincha y Guayaquil no cuesta. A otro banco o cooperativa se suma el costo de la transferencia, que sale de la misma cuenta o ahorro.</p>
          }
          <button type="submit" [disabled]="!listoParaEntregar() || enviando()">Entregar dinero</button>
        </form>
      } @else {
        <p class="vacio">Busque a la persona a la que va a entregar dinero.</p>
      }
    }
  `,
})
export class Ventanilla {
  private readonly api = inject(ApiService);
  private readonly dialogo = inject(DialogoService);
  private readonly archivo = viewChild<ElementRef<HTMLInputElement>>('archivo');
  private readonly busquedas = new Subject<string>();
  /** Fichas de las personas de este cobro: de ahí salen el nombre y los saldos de quien paga. */
  private readonly fichas = signal(new Map<number, FichaVentanilla>());

  protected readonly modo = signal<'cobrar' | 'entregar'>('cobrar');
  protected readonly resultados = signal<PersonaVentanilla[]>([]);
  protected readonly buscado = signal(false);
  protected readonly ficha = signal<FichaVentanilla | null>(null);
  protected readonly cargandoFicha = signal(false);
  protected readonly concepto = signal('');
  protected readonly meses = signal(new Set<string>());
  protected readonly lineas = signal<Linea[]>([]);
  protected readonly pagadorId = signal<number | null>(null);
  /** EFECTIVO, TRANSFERENCIA, CUENTA o AHORRO:<id>. */
  protected readonly origen = signal('EFECTIVO');
  protected readonly recibo = signal<(CobroVentanilla & { origen: string }) | null>(null);
  protected readonly entregado = signal<SolicitudRetiro | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);

  protected readonly total = computed(() => this.lineas().reduce((s, l) => s + l.importe, 0));
  protected readonly porMeses = computed(() => this.concepto() === 'APORTE_OBLIGATORIO' || this.concepto() === 'CAJA_CHICA');
  protected readonly importeMeses = computed(() => {
    const o = this.ficha()?.opciones;
    const porMes = (this.concepto() === 'CAJA_CHICA' ? o?.aporteCajaChica : o?.aporteMensual) ?? 0;
    return this.meses().size * porMes;
  });
  protected readonly creditoElegido = computed(() =>
    this.concepto().startsWith('C:') ? this.ficha()?.opciones.creditos.find((c) => c.id === Number(this.concepto().slice(2))) ?? null : null);
  protected readonly personasDelCobro = computed(() => {
    const vistas = new Map<number, { usuarioId: number; nombre: string }>();
    this.lineas().forEach((l) => vistas.set(l.destino.usuarioId, { usuarioId: l.destino.usuarioId, nombre: l.para }));
    return [...vistas.values()];
  });
  protected readonly fichaPagador = computed(() => {
    const id = this.pagadorId();
    return id === null ? null : this.fichas().get(id) ?? null;
  });
  protected readonly conSaldo = computed(() => this.origen() === 'CUENTA' || this.origen().startsWith('AHORRO:'));
  protected readonly saldoOrigen = computed(() => {
    const f = this.fichaPagador();
    if (!f) {
      return 0;
    }
    return this.origen() === 'CUENTA' ? f.saldoCuenta : f.ahorros.find((a) => `AHORRO:${a.id}` === this.origen())?.saldo ?? 0;
  });

  protected texto = '';
  protected importe: number | null = null;
  protected referencia = '';
  private archivos: File[] = [];
  protected origenRetiro = 'CUENTA';
  protected importeRetiro: number | null = null;
  protected medioRetiro: 'EFECTIVO' | 'TRANSFERENCIA' = 'EFECTIVO';
  protected banco = '';
  protected tipoCuenta = 'AHORROS';
  protected numeroCuenta = '';
  protected titular = '';
  protected referenciaRetiro = '';
  // Una clave por intento: se renueva solo tras un éxito, para que reintentar no cobre ni entregue dos veces.
  private claveCobro: string = crypto.randomUUID();
  private claveRetiro: string = crypto.randomUUID();

  constructor() {
    // Otro cambio (por ejemplo, un pago confirmado en otra pantalla) deja viejos los saldos: se vuelven a leer.
    alCambiar(() => this.refrescarFichas());
    this.busquedas.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap((t) => (t.length < 3 ? of(null) : this.api.buscarEnVentanilla(t).pipe(catchError((e) => {
        this.error.set(mensajeDeError(e));
        return of(null);
      })))),
      takeUntilDestroyed(),
    ).subscribe((r) => {
      this.resultados.set(r ?? []);
      this.buscado.set(r !== null);
    });
  }

  protected familia(codigo: string): string {
    return nombreFamilia(codigo);
  }

  protected alEscribir(texto: string): void {
    this.busquedas.next(texto.trim());
  }

  protected buscar(): void {
    this.busquedas.next(this.texto.trim());
  }

  protected atender(usuarioId: number): void {
    this.error.set(null);
    this.cargandoFicha.set(true);
    this.api.fichaVentanilla(usuarioId).subscribe({
      next: (f) => {
        this.cargandoFicha.set(false);
        this.guardarFicha(f);
        this.ficha.set(f);
        this.limpiarConcepto();
        this.entregado.set(null);
      },
      error: (e) => {
        this.cargandoFicha.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  protected cambiarPersona(): void {
    this.ficha.set(null);
    this.texto = '';
    this.resultados.set([]);
    this.buscado.set(false);
    this.limpiarConcepto();
    this.entregado.set(null);
  }

  private guardarFicha(f: FichaVentanilla): void {
    const nuevas = new Map(this.fichas());
    nuevas.set(f.persona.usuarioId, f);
    this.fichas.set(nuevas);
  }

  private refrescarFichas(): void {
    const actual = this.ficha()?.persona.usuarioId;
    for (const id of this.fichas().keys()) {
      this.api.fichaVentanilla(id).subscribe({
        next: (f) => {
          this.guardarFicha(f);
          if (f.persona.usuarioId === actual) {
            this.ficha.set(f);
          }
        },
        error: () => undefined,
      });
    }
  }

  // --- Cobrar

  protected elegirConcepto(valor: string): void {
    this.concepto.set(valor);
    this.meses.set(new Set());
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

  protected mesNoDisponible(m: MesAporte): boolean {
    const id = this.ficha()?.persona.usuarioId;
    const tipo = this.concepto();
    return this.mesPagado(m) || this.lineas().some((l) => l.destino.usuarioId === id && l.destino.tipo === tipo
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
    const f = this.ficha();
    if (!f || !this.puedeAgregar()) {
      return;
    }
    const o = f.opciones;
    const valor = this.concepto();
    const para = f.persona.nombre;
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
    // Por defecto paga la primera persona de la lista; el tesorero puede cambiarla.
    if (this.pagadorId() === null) {
      this.pagadorId.set(o.usuarioId);
    }
    this.limpiarConcepto();
  }

  protected quitar(indice: number): void {
    this.lineas.update((l) => l.filter((_, i) => i !== indice));
    const personas = this.personasDelCobro();
    if (!personas.some((p) => p.usuarioId === this.pagadorId())) {
      this.pagadorId.set(personas[0]?.usuarioId ?? null);
      this.origen.set('EFECTIVO');
    }
  }

  protected elegirPagador(id: number): void {
    this.pagadorId.set(id);
    // Los saldos son de cada persona: al cambiar quién paga, se vuelve a efectivo.
    if (this.conSaldo()) {
      this.origen.set('EFECTIVO');
    }
  }

  protected elegirArchivos(evento: Event): void {
    this.archivos = Array.from((evento.target as HTMLInputElement).files ?? []);
  }

  protected listoParaCobrar(): boolean {
    if (!this.lineas().length || this.pagadorId() === null) {
      return false;
    }
    if (this.conSaldo()) {
      return this.saldoOrigen() >= this.total() && this.lineas().every((l) => l.destino.usuarioId === this.pagadorId());
    }
    return this.origen() !== 'TRANSFERENCIA' || (!!this.referencia.trim() && this.archivos.length > 0);
  }

  protected async cobrar(): Promise<void> {
    const pagador = this.personasDelCobro().find((p) => p.usuarioId === this.pagadorId());
    const forma = this.origen() === 'EFECTIVO' ? 'en efectivo' : this.origen() === 'TRANSFERENCIA' ? 'por transferencia'
      : this.origen() === 'CUENTA' ? 'con el saldo de su cuenta principal' : 'con su ahorro';
    const ok = await this.dialogo.confirmar(
      `¿Cobrar ${this.total().toFixed(2)} USD ${forma}, pagados por ${pagador?.nombre}? Se aplica en este momento.`, 'Cobrar');
    if (!ok) {
      return;
    }
    const [origen, ahorro] = this.origen().split(':') as [OrigenCobro, string | undefined];
    const referencia = origen === 'TRANSFERENCIA' ? this.referencia.trim() : null;
    const archivos = this.conSaldo() ? [] : this.archivos;
    const nombreOrigen = origen === 'CUENTA' ? 'la cuenta principal'
      : `el ahorro «${this.fichaPagador()?.ahorros.find((a) => a.id === Number(ahorro))?.nombre ?? ''}»`;
    this.error.set(null);
    this.enviando.set(true);
    this.api.cobrarEnVentanilla(this.pagadorId()!, this.lineas().map((l) => l.destino), origen, ahorro ? Number(ahorro) : null,
      referencia, archivos, this.claveCobro).subscribe({
      next: (r) => {
        this.claveCobro = crypto.randomUUID();
        this.enviando.set(false);
        this.recibo.set({ ...r, origen: nombreOrigen });
        this.lineas.set([]);
        this.pagadorId.set(null);
        this.origen.set('EFECTIVO');
        this.referencia = '';
        this.archivos = [];
        const entrada = this.archivo()?.nativeElement;
        if (entrada) {
          entrada.value = '';
        }
        this.refrescarFichas();
      },
      error: (e) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  // --- Entregar

  protected listoParaEntregar(): boolean {
    if (!this.importeRetiro || this.importeRetiro <= 0) {
      return false;
    }
    return this.medioRetiro === 'EFECTIVO'
      || (!!this.banco.trim() && !!this.numeroCuenta.trim() && !!this.titular.trim());
  }

  protected async entregar(): Promise<void> {
    const f = this.ficha();
    if (!f || !this.listoParaEntregar()) {
      return;
    }
    const [origen, ahorro] = this.origenRetiro.split(':') as ['CUENTA' | 'AHORRO', string | undefined];
    const ok = await this.dialogo.confirmar(
      `¿Entregar ${this.importeRetiro!.toFixed(2)} USD ${this.medioRetiro === 'EFECTIVO' ? 'en efectivo' : 'por transferencia'} a ${f.persona.nombre}? Sale de la caja en este momento.`,
      'Entregar dinero');
    if (!ok) {
      return;
    }
    const aBanco = this.medioRetiro === 'TRANSFERENCIA';
    this.error.set(null);
    this.enviando.set(true);
    this.api.entregarEnVentanilla({
      usuarioId: f.persona.usuarioId, origen, ahorroId: ahorro ? Number(ahorro) : null, importe: this.importeRetiro!,
      medio: this.medioRetiro, banco: aBanco ? this.banco.trim() : null, tipoCuenta: aBanco ? this.tipoCuenta : null,
      numeroCuenta: aBanco ? this.numeroCuenta.trim() : null, titularCuenta: aBanco ? this.titular.trim() : null,
      referencia: aBanco && this.referenciaRetiro.trim() ? this.referenciaRetiro.trim() : null,
    }, this.claveRetiro).subscribe({
      next: (r) => {
        this.claveRetiro = crypto.randomUUID();
        this.enviando.set(false);
        this.entregado.set(r);
        this.importeRetiro = null;
        this.referenciaRetiro = '';
        this.refrescarFichas();
      },
      error: (e) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }
}
