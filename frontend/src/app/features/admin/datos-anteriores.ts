import { CurrencyPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, EstadoAportes, PersonaTramite } from '../../core/services/api';
import { DialogoService } from '../../shared/components/dialogo';
import { mensajeDeError } from '../../shared/utils/errores';
import { CreditoExistenteForm } from './credito-existente';

type Pestana = 'LETRAS' | 'EMERGENTE' | 'CUPO' | 'AHORRO' | 'APORTES';

/**
 * Todo lo que una persona ya tenía antes de usar Janery: se elige a la persona una vez y se registra, uno por uno, su
 * crédito a letras, su emergente, su cupo de socio, sus ahorros y sus aportes. Entran los saldos de hoy, sin mover dinero.
 */
@Component({
  selector: 'app-datos-anteriores',
  imports: [CreditoExistenteForm, CurrencyPipe, FormsModule],
  template: `
    <h1>Datos anteriores a Janery</h1>
    <p class="suave">Para cargar lo que cada persona ya tenía antes de usar Janery. No entra ni sale dinero de la caja ni de su cuenta: ese dinero ya se movió. Se registra lo que tiene o debe hoy y, desde ahí, todo funciona como cualquier otro producto.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }

    @if (!persona()) {
      <form class="herramientas" (ngSubmit)="buscar()">
        <label>Persona <input type="search" name="buscar" placeholder="Apellido, nombre, cédula o número de cuenta" [(ngModel)]="texto" /></label>
        <button type="submit">Buscar</button>
      </form>
      @for (p of resultados(); track p.usuarioId) {
        <div class="fila-dato tarjeta">
          <div><strong>{{ p.nombre }}</strong><p class="suave">{{ p.identificacion }} · {{ p.cuenta }} · {{ p.socio ? 'Socio' : 'Cliente' }}</p></div>
          <button type="button" (click)="elegir(p)">Elegir</button>
        </div>
      } @empty {
        @if (buscado()) { <p class="vacio">Nadie con cuenta activa coincide con la búsqueda. Si la persona aún no está en Janery, créela primero en Usuarios.</p> }
      }
    } @else {
      <div class="encabezado-tarjeta">
        <p class="m-0"><strong>{{ persona()!.nombre }}</strong> · {{ persona()!.identificacion }} · {{ persona()!.socio ? 'Socio' : 'Cliente' }}</p>
        <button type="button" class="secundario" (click)="reiniciar()">Elegir a otra persona</button>
      </div>

      <nav class="pestanas" aria-label="Qué registrar">
        @for (p of pestanas; track p.id) {
          <button type="button" [class.activo]="pestana() === p.id" [attr.aria-current]="pestana() === p.id ? 'page' : null"
            (click)="cambiar(p.id)">{{ p.titulo }}</button>
        }
      </nav>

      @switch (pestana()) {
        @case ('LETRAS') {
          <app-credito-existente [fija]="persona()" (registrado)="registrado('Crédito a letras registrado.')" />
        }
        @case ('EMERGENTE') {
          <form class="tarjeta" (ngSubmit)="registrarEmergente()">
            <h2 class="seccion-titulo">Emergente de cliente</h2>
            <p class="suave">Un solo pago, que vence un mes después de recibir el dinero. El interés y el desgravamen se calculan sobre el importe.</p>
            <label>Importe que recibió (USD) <input type="number" min="0" step="any" name="importe" [(ngModel)]="emergente.importe" /></label>
            <label>Fecha en que recibió el dinero <input type="date" name="inicio" [max]="hoy" [(ngModel)]="emergente.fechaInicio" /></label>
            <label>Interés (% mensual) <input type="number" min="0" step="any" name="interes" [(ngModel)]="emergente.tasaInteres" /></label>
            <label>Desgravamen (% mensual) <input type="number" min="0" step="any" name="desgravamen" [(ngModel)]="emergente.tasaDesgravamen" /></label>
            <label>Capital que debe hoy (USD, vacío si debe todo el importe) <input type="number" min="0" step="any" name="debe" [(ngModel)]="emergente.capitalDebe" /></label>
            <div class="fila-opcion">
              <label><input type="checkbox" name="cargos" [(ngModel)]="emergente.cargosPagados" /> Ya pagó el interés y el desgravamen</label>
            </div>
            <label>Nota (opcional) <input name="nota" maxlength="300" [(ngModel)]="emergente.nota" /></label>
            <button type="submit" [disabled]="enviando() || !emergenteCompleto()">Registrar emergente</button>
          </form>
        }
        @case ('CUPO') {
          <form class="tarjeta" (ngSubmit)="registrarCupo()">
            <h2 class="seccion-titulo">Cupo emergente de socio</h2>
            @if (!persona()!.socio) {
              <p class="alerta-atraso">Solo los socios tienen cupo. Registre primero su ingreso como socio en «Aportes de socio».</p>
            }
            <p class="suave">Lo que debe hoy de su cupo. Desde hoy le corre el interés diario vigente sobre ese capital, y su cupo disponible baja en ese valor.</p>
            <label>Capital que debe hoy (USD) <input type="number" min="0" step="any" name="debe" [(ngModel)]="cupo.capitalDebe" /></label>
            <label>Interés ya corrido sin pagar (USD, vacío si nada) <input type="number" min="0" step="any" name="interes" [(ngModel)]="cupo.interesPendiente" /></label>
            <label>Desgravamen ya corrido sin pagar (USD, vacío si nada) <input type="number" min="0" step="any" name="desgravamen" [(ngModel)]="cupo.desgravamenPendiente" /></label>
            <label>Nota (opcional) <input name="nota" maxlength="300" [(ngModel)]="cupo.nota" /></label>
            <button type="submit" [disabled]="enviando() || !persona()!.socio || !((cupo.capitalDebe ?? 0) > 0)">Registrar deuda del cupo</button>
          </form>
        }
        @case ('AHORRO') {
          <form class="tarjeta" (ngSubmit)="registrarAhorro()">
            <h2 class="seccion-titulo">Ahorro</h2>
            <label>Tipo
              <select name="tipo" [(ngModel)]="ahorro.tipo" (ngModelChange)="alCambiarTipo()">
                <option value="NORMAL">Normal</option>
                <option value="PLAZO_FIJO">Plazo fijo</option>
              </select>
            </label>
            <label>Nombre <input name="nombre" maxlength="100" placeholder="Ej.: Ahorro de la libreta" [(ngModel)]="ahorro.nombre" /></label>
            <label>{{ ahorro.tipo === 'NORMAL' ? 'Saldo que tiene hoy' : 'Monto depositado' }} (USD) <input type="number" min="0" step="any" name="saldo" [(ngModel)]="ahorro.saldo" /></label>
            @if (ahorro.tipo === 'NORMAL') {
              <p class="suave">Gana el interés del ahorro normal desde hoy.</p>
            } @else {
              <label>Fecha de apertura <input type="date" name="apertura" [max]="hoy" [(ngModel)]="ahorro.fechaApertura" /></label>
              <label>Plazo (meses) <input type="number" min="1" step="1" name="plazo" [(ngModel)]="ahorro.plazoMeses" (ngModelChange)="sugerirTasa()" /></label>
              <label>Tasa (% anual) <input type="number" min="0" step="any" name="tasa" [(ngModel)]="ahorro.tasaAnual" /></label>
              <p class="suave">Vence al cumplir el plazo desde su apertura y paga su interés al vencer, como cualquier plazo fijo.</p>
            }
            <button type="submit" [disabled]="enviando() || !ahorroCompleto()">Registrar ahorro</button>
          </form>
        }
        @case ('APORTES') {
          <form class="tarjeta" (ngSubmit)="registrarAportes()">
            <h2 class="seccion-titulo">Aportes de socio</h2>
            <p class="suave">Los meses se marcan pagados <strong>desde el mes en que entró como socio</strong> hasta el mes que indique, y se pueden adelantar hasta diciembre de este año. Los ya pagados se saltan. Deje vacío lo que no aplique.</p>
            @if (estado(); as e) {
              @if (e.socio) {
                <dl class="ficha-datos">
                  <div><dt>Socio desde</dt><dd>{{ e.socioDesde }}</dd></div>
                  <div><dt>Aporte inicial</dt><dd>{{ e.aporteInicial ? 'Registrado' : 'Sin registrar' }}</dd></div>
                  <div><dt>Aportes mensuales pagados</dt><dd>{{ rango(e.mesesAporte) }}</dd></div>
                  <div><dt>Caja chica pagada</dt><dd>{{ rango(e.mesesCajaChica) }}</dd></div>
                </dl>
                <p class="suave">Si es socio desde antes del {{ e.socioDesde }}, escriba su fecha real en «Socio desde»: los meses se cuentan desde ahí.</p>
              } @else {
                <p class="suave">Aún no figura como socio: indique desde cuándo lo es.</p>
              }
            }
            <label>Socio desde {{ persona()!.socio ? '(vacío para mantener la fecha que tiene)' : '' }} <input type="date" name="desde" [max]="hoy" [(ngModel)]="aportes.socioDesde" /></label>
            <label>Aporte inicial (USD) <input type="number" min="0" step="any" name="inicial" [(ngModel)]="aportes.aporteInicial" /></label>
            <label>Aporte mensual pagado hasta el mes <input type="month" name="mensuales" [max]="finDeAnio" [(ngModel)]="aportes.mensualesHasta" /></label>
            @if (aportes.mensualesHasta) { <p class="suave">{{ meses('aporte mensual', aportes.mensualesHasta, estado()?.mesesAporte ?? []) }}</p> }
            <label>Valor de cada aporte mensual (USD) <input type="number" min="0" step="any" name="mensual" [(ngModel)]="aportes.importeMensual" /></label>
            <label>Aportes personales, en total (USD) <input type="number" min="0" step="any" name="personales" [(ngModel)]="aportes.personales" /></label>
            <label>Caja chica pagada hasta el mes <input type="month" name="caja" [max]="finDeAnio" [(ngModel)]="aportes.cajaChicaHasta" /></label>
            @if (aportes.cajaChicaHasta) { <p class="suave">{{ meses('caja chica', aportes.cajaChicaHasta, estado()?.mesesCajaChica ?? []) }}</p> }
            @if (valores()['APORTE_CAJA_CHICA']; as caja) { <p class="suave">Cada mes de caja chica se registra por {{ caja | currency: 'USD' }}, el valor vigente.</p> }
            <button type="submit" [disabled]="enviando() || (!persona()!.socio && !aportes.socioDesde)">Registrar aportes</button>
          </form>
        }
      }
    }
  `,
})
export class DatosAnteriores implements OnInit {
  private readonly api = inject(ApiService);
  private readonly dialogo = inject(DialogoService);

  protected readonly pestanas: { id: Pestana; titulo: string }[] = [
    { id: 'LETRAS', titulo: 'Crédito a letras' },
    { id: 'EMERGENTE', titulo: 'Emergente' },
    { id: 'CUPO', titulo: 'Cupo de socio' },
    { id: 'AHORRO', titulo: 'Ahorros' },
    { id: 'APORTES', titulo: 'Aportes de socio' },
  ];

  protected readonly resultados = signal<PersonaTramite[]>([]);
  protected readonly buscado = signal(false);
  protected readonly persona = signal<PersonaTramite | null>(null);
  protected readonly pestana = signal<Pestana>('LETRAS');
  protected readonly valores = signal<Record<string, number>>({});
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  protected readonly hoy = new Date().toLocaleDateString('en-CA', { timeZone: 'America/Guayaquil' });
  /** Decisión del propietario: se pueden adelantar los meses que faltan del año. */
  protected readonly finDeAnio = `${this.hoy.slice(0, 4)}-12`;
  protected readonly estado = signal<EstadoAportes | null>(null);
  protected texto = '';
  protected emergente = this.emergenteVacio();
  protected cupo = this.cupoVacio();
  protected ahorro = this.ahorroVacio();
  protected aportes = this.aportesVacio();
  // Una clave por intento: se renueva solo tras un éxito, para que reintentar no duplique.
  private clave: string = crypto.randomUUID();

  ngOnInit(): void {
    this.api.valoresAnteriores().subscribe({
      next: (v) => {
        this.valores.set(v);
        this.emergente = this.emergenteVacio();
        this.aportes = this.aportesVacio();
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected buscar(): void {
    this.error.set(null);
    this.api.buscarPersonasExistente(this.texto.trim()).subscribe({
      next: (l) => {
        this.resultados.set(l);
        this.buscado.set(true);
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected elegir(p: PersonaTramite): void {
    this.persona.set(p);
    this.pestana.set('LETRAS');
    this.aviso.set(null);
    this.error.set(null);
    this.cargarEstado();
  }

  private cargarEstado(): void {
    this.estado.set(null);
    this.api.estadoAportes(this.persona()!.usuarioId).subscribe({
      next: (e) => this.estado.set(e),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  /** Meses pagados de forma legible: «2025-01 a 2026-11», con los huecos separados. */
  protected rango(meses: string[]): string {
    if (!meses.length) {
      return 'Ninguno';
    }
    const tramos: string[] = [];
    let inicio = meses[0];
    let anterior = meses[0];
    for (const mes of [...meses.slice(1), '']) {
      if (mes !== siguiente(anterior)) {
        tramos.push(inicio === anterior ? inicio : `${inicio} a ${anterior}`);
        inicio = mes;
      }
      anterior = mes;
    }
    return tramos.join(', ');
  }

  /** Qué meses marcará el registro: desde el ingreso (el más antiguo entre el que tiene y el escrito) hasta el elegido. */
  protected meses(que: string, hasta: string, pagados: string[]): string {
    const actual = this.estado()?.socioDesde?.slice(0, 7);
    const escrito = this.aportes.socioDesde ? this.aportes.socioDesde.slice(0, 7) : undefined;
    const desde = [actual, escrito].filter((m): m is string => !!m).sort()[0];
    if (!desde) {
      return 'Indique en «Socio desde» cuándo entró: los meses se cuentan desde ahí.';
    }
    if (hasta < desde) {
      return `Ese mes es anterior a su ingreso (${desde}): no se marcará ningún mes.`;
    }
    let nuevos = 0;
    let yaPagados = 0;
    for (let m = desde; m <= hasta; m = siguiente(m)) {
      if (pagados.includes(m)) {
        yaPagados++;
      } else {
        nuevos++;
      }
    }
    const saltan = yaPagados ? ` (${yaPagados} ya pagado${yaPagados === 1 ? '' : 's'} se salta${yaPagados === 1 ? '' : 'n'})` : '';
    return `Se marcarán ${nuevos} mes${nuevos === 1 ? '' : 'es'} de ${que}, de ${desde} a ${hasta}${saltan}.`;
  }

  protected reiniciar(): void {
    this.persona.set(null);
    this.emergente = this.emergenteVacio();
    this.cupo = this.cupoVacio();
    this.ahorro = this.ahorroVacio();
    this.aportes = this.aportesVacio();
  }

  protected cambiar(p: Pestana): void {
    this.pestana.set(p);
    this.aviso.set(null);
    this.error.set(null);
  }

  protected registrado(mensaje: string): void {
    this.aviso.set(`${mensaje} Puede seguir registrando otros datos de ${this.persona()!.nombre}.`);
  }

  // --- Emergente

  protected emergenteCompleto(): boolean {
    const e = this.emergente;
    return (e.importe ?? 0) > 0 && !!e.fechaInicio && (e.tasaInteres ?? -1) >= 0 && (e.tasaDesgravamen ?? -1) >= 0;
  }

  protected async registrarEmergente(): Promise<void> {
    const e = this.emergente;
    const debe = vacio(e.capitalDebe) ? e.importe! : e.capitalDebe!;
    if (!(await this.confirmar(`¿Registrar el emergente de ${this.persona()!.nombre} por ${e.importe} USD, del ${e.fechaInicio}, con ${debe} USD de capital por pagar?`))) {
      return;
    }
    this.enviar(this.api.registrarEmergenteAnterior(this.persona()!.usuarioId, {
      importe: e.importe!, fechaInicio: e.fechaInicio, tasaInteres: e.tasaInteres!, tasaDesgravamen: e.tasaDesgravamen!,
      capitalDebe: vacio(e.capitalDebe) ? null : e.capitalDebe, cargosPagados: e.cargosPagados, nota: e.nota,
    }, this.clave), () => {
      this.emergente = this.emergenteVacio();
      this.registrado('Emergente registrado.');
    });
  }

  // --- Cupo

  protected async registrarCupo(): Promise<void> {
    const c = this.cupo;
    if (!(await this.confirmar(`¿Registrar que ${this.persona()!.nombre} debe ${c.capitalDebe} USD de su cupo emergente?`))) {
      return;
    }
    this.enviar(this.api.registrarCupoAnterior(this.persona()!.usuarioId, {
      capitalDebe: c.capitalDebe!, interesPendiente: vacio(c.interesPendiente) ? null : c.interesPendiente,
      desgravamenPendiente: vacio(c.desgravamenPendiente) ? null : c.desgravamenPendiente, nota: c.nota,
    }, this.clave), () => {
      this.cupo = this.cupoVacio();
      this.registrado('Deuda del cupo registrada.');
    });
  }

  // --- Ahorro

  protected alCambiarTipo(): void {
    this.sugerirTasa();
  }

  /** Propone la tasa vigente para ese plazo, si es uno de los habituales; se puede cambiar por la que tenía. */
  protected sugerirTasa(): void {
    const tasa = this.valores()[`PLAZO_FIJO_TASA_${this.ahorro.plazoMeses}_MESES`];
    if (tasa !== undefined) {
      this.ahorro.tasaAnual = tasa;
    }
  }

  protected ahorroCompleto(): boolean {
    const a = this.ahorro;
    const base = !!a.nombre.trim() && (a.saldo ?? 0) > 0;
    return a.tipo === 'NORMAL' ? base : base && !!a.fechaApertura && (a.plazoMeses ?? 0) > 0 && (a.tasaAnual ?? -1) >= 0;
  }

  protected async registrarAhorro(): Promise<void> {
    const a = this.ahorro;
    const tipo = a.tipo === 'NORMAL' ? 'ahorro normal' : 'plazo fijo';
    if (!(await this.confirmar(`¿Registrar el ${tipo} «${a.nombre.trim()}» de ${this.persona()!.nombre} por ${a.saldo} USD?`))) {
      return;
    }
    const plazo = a.tipo === 'PLAZO_FIJO';
    this.enviar(this.api.registrarAhorroAnterior(this.persona()!.usuarioId, {
      tipo: a.tipo, nombre: a.nombre.trim(), saldo: a.saldo!, fechaApertura: plazo ? a.fechaApertura : null,
      plazoMeses: plazo ? a.plazoMeses : null, tasaAnual: plazo ? a.tasaAnual : null,
    }, this.clave), () => {
      this.ahorro = this.ahorroVacio();
      this.registrado('Ahorro registrado.');
    });
  }

  // --- Aportes

  protected async registrarAportes(): Promise<void> {
    if (!(await this.confirmar(`¿Registrar los aportes anteriores de ${this.persona()!.nombre}?`))) {
      return;
    }
    const a = this.aportes;
    this.enviar(this.api.registrarAportesAnteriores(this.persona()!.usuarioId, {
      socioDesde: a.socioDesde || null, aporteInicial: vacio(a.aporteInicial) ? null : a.aporteInicial,
      mensualesHasta: a.mensualesHasta || null, importeMensual: vacio(a.importeMensual) ? null : a.importeMensual,
      personales: vacio(a.personales) ? null : a.personales, cajaChicaHasta: a.cajaChicaHasta || null,
    }, this.clave), (r) => {
      this.persona.update((p) => (p ? { ...p, socio: true } : p));
      this.aportes = this.aportesVacio();
      this.cargarEstado();
      this.registrado(`Aportes registrados: ${r.mesesAporte} meses de aporte mensual y ${r.mesesCajaChica} de caja chica.`);
    });
  }

  // --- Apoyo

  private confirmar(pregunta: string): Promise<boolean> {
    return this.dialogo.confirmar(`${pregunta} No se puede borrar después.`, 'Registrar');
  }

  private enviar<T>(peticion: import('rxjs').Observable<T>, alTerminar: (r: T) => void): void {
    this.enviando.set(true);
    this.error.set(null);
    this.aviso.set(null);
    peticion.subscribe({
      next: (r) => {
        this.clave = crypto.randomUUID();
        this.enviando.set(false);
        alTerminar(r);
      },
      error: (e) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  private emergenteVacio() {
    const v = this.valores?.() ?? {};
    return { importe: null as number | null, fechaInicio: '', tasaInteres: v['EMERGENTE_CLIENTE_INTERES'] ?? null as number | null,
      tasaDesgravamen: v['EMERGENTE_CLIENTE_DESGRAVAMEN'] ?? null as number | null, capitalDebe: null as number | null,
      cargosPagados: false, nota: '' };
  }

  private cupoVacio() {
    return { capitalDebe: null as number | null, interesPendiente: null as number | null, desgravamenPendiente: null as number | null, nota: '' };
  }

  private ahorroVacio() {
    return { tipo: 'NORMAL' as 'NORMAL' | 'PLAZO_FIJO', nombre: '', saldo: null as number | null, fechaApertura: '',
      plazoMeses: null as number | null, tasaAnual: null as number | null };
  }

  private aportesVacio() {
    const v = this.valores?.() ?? {};
    return { socioDesde: '', aporteInicial: null as number | null, mensualesHasta: '', importeMensual: v['APORTE_OBLIGATORIO'] ?? null as number | null,
      personales: null as number | null, cajaChicaHasta: '' };
  }
}

/** El mes siguiente, en formato AAAA-MM. */
function siguiente(mes: string): string {
  if (!mes) {
    return '';
  }
  const [anio, m] = mes.split('-').map(Number);
  return m === 12 ? `${anio + 1}-01` : `${anio}-${String(m + 1).padStart(2, '0')}`;
}

/** Un campo numérico borrado llega como null o como texto vacío. */
function vacio(n: number | null | undefined): boolean {
  return n === null || n === undefined || (n as unknown) === '';
}
