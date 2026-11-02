import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { Ahorro, ApiService, Credito, DatosCredito, Perfil, ProductosCredito, Simulacion, SolicitudCupo } from '../../core/services/api';
import { alCambiar } from '../../core/services/tiempo-real';
import { Estado } from '../../shared/components/estado';
import { DocumentosCredito } from './documentos-credito';
import { nombreFamilia } from '../../shared/utils/etiquetas';
import { DialogoService } from '../../shared/components/dialogo';
import { mensajeDeError } from '../../shared/utils/errores';

@Component({
  selector: 'app-creditos',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, DocumentosCredito, Estado, FormsModule, ReactiveFormsModule],
  template: `
    <h1>Créditos</h1>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }

    @if (atrasadas() > 0) {
      <p class="alerta-atraso" role="alert">Tiene {{ atrasadas() }} {{ atrasadas() === 1 ? 'letra atrasada' : 'letras atrasadas' }}.
        @if (mora() > 0) { La mora acumulada es de {{ mora() | currency: 'USD' }} y sube cada día hasta que pague la letra. }</p>
    }

    <section class="total">
      <div>
        <h2>Total que debe</h2>
        <p class="total-importe">{{ totalDeuda() | currency: 'USD' }}</p>
      </div>
      <p class="total-partes">
        <span>Capital<strong>{{ totalCapital() | currency: 'USD' }}</strong></span>
        <span>Créditos activos<strong>{{ activos().length }}</strong></span>
        @if (mora() > 0) { <span>Mora<strong>{{ mora() | currency: 'USD' }}</strong></span> }
      </p>
    </section>

    <section class="seccion">
      <h2 class="seccion-titulo">Créditos vigentes</h2>
      @for (c of vigentes(); track c.id) {
        <section class="tarjeta">
          <div class="encabezado-tarjeta">
            <h2>{{ familia(c.familia) }} {{ c.modalidad ?? '' }}</h2>
            <app-estado [valor]="c.estado" />
          </div>
          <p class="suave">
            Interés {{ c.tasaInteres | number: '1.0-4' }} %{{ c.unidadTasa === 'PCT_DIARIO' ? ' diario' : ' mensual' }}
            @if (c.tasaDesgravamen > 0) { · desgravamen {{ c.tasaDesgravamen | number: '1.0-4' }} % }
          </p>
          @if (c.estado === 'SOLICITADO') {
            <p>Solicitud de {{ c.importe | currency: 'USD' }} a {{ c.plazoMeses }} {{ c.plazoMeses === 1 ? 'letra' : 'letras' }}, pendiente de aprobación. El dinero llega a su cuenta cuando se apruebe.</p>
            @if (c.origenEncaje) { <p class="suave"><strong>Encaje:</strong> {{ origen(c.origenEncaje) }}.</p> }
            @if (c.familia === 'LETRAS') { <app-documentos-credito [creditoId]="c.id" /> }
          } @else {
            <div class="importes">
              <div>Capital pendiente<p class="importe">{{ c.capitalPendiente | currency: 'USD' }}</p></div>
              <div>{{ esCupo(c) ? 'Total a pagar con interés' : 'Total pendiente' }}<p class="importe">{{ c.totalPendiente | currency: 'USD' }}</p></div>
              @if (c.tasaMora > 0) { <div>Mora ({{ c.tasaMora | currency: 'USD' }} por día)<p class="importe">{{ c.moraPendiente | currency: 'USD' }}</p></div> }
              @if (c.familia === 'LETRAS') {
                <div>Encaje<p class="importe">{{ c.encaje | currency: 'USD' }}</p></div>
                <div>Ahorro letras<p class="importe">{{ c.ahorroLetras | currency: 'USD' }}</p></div>
              }
            </div>

            @if (esCupo(c)) {
              <form class="acciones" (ngSubmit)="pagarCupo(c, importePago)">
                <label>Importe a pagar (USD)
                  <input type="number" min="0" step="1" name="pago{{ c.id }}" [(ngModel)]="importePago" />
                </label>
                <button type="submit" [disabled]="!importePago || importePago <= 0">Pagar</button>
              </form>
            } @else {
              <div class="tabla">
                <table>
                  <thead><tr><th>N.º</th><th>Vence</th><th class="num">Capital</th><th class="num">Interés</th><th class="num">Desgravamen</th><th class="num">Ahorro letras</th><th class="num">Total</th>@if (c.tasaMora > 0) { <th class="num">Mora</th> }<th>Estado</th></tr></thead>
                  <tbody>
                    @for (l of c.cuotas; track l.numero) {
                      <tr>
                        <td>{{ l.numero }}</td>
                        <td>{{ l.vencimiento | date: 'mediumDate' : 'UTC' }}</td>
                        <td class="num">{{ l.capital | currency: 'USD' }}</td>
                        <td class="num">{{ l.interes | currency: 'USD' }}</td>
                        <td class="num">{{ l.fondoDesgravamen | currency: 'USD' }}</td>
                        <td class="num">{{ l.ahorroLetras | currency: 'USD' }}</td>
                        <td class="num">{{ l.total | currency: 'USD' }}</td>
                        @if (c.tasaMora > 0) { <td class="num">{{ l.mora | currency: 'USD' }}</td> }
                        <td>
                          @if (c.cuotasPagadas.includes(l.numero)) {
                            <span class="estado estado-exito">Pagada</span>
                          } @else if (l.diasAtraso > 0) {
                            <span class="estado estado-peligro">Atrasada {{ l.diasAtraso }} {{ l.diasAtraso === 1 ? 'día' : 'días' }}</span>
                          } @else if (l.pagado > 0) {
                            <span class="estado estado-informacion">Pagado {{ l.pagado | currency: 'USD' }}</span>
                          } @else {
                            <span class="estado estado-neutro">Pendiente</span>
                          }
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
              @if (esUltima(c) && c.familia === 'LETRAS') {
                <label><input type="checkbox" [(ngModel)]="usarAhorro" /> Usar ahorro letras en esta última letra</label>
                <label><input type="checkbox" [(ngModel)]="usarEncaje" /> Usar encaje en esta última letra</label>
              }
              <p>Falta de la siguiente letra: <strong>{{ pendienteSiguiente(c) | currency: 'USD' }}</strong></p>
              @if (c.familia === 'LETRAS') { <p class="suave">Puede pagar más: lo que exceda pasa a las siguientes letras.</p> }
              <form class="acciones" (ngSubmit)="pagarCuota(c)">
                <label>Importe (USD; vacío, lo que falta de la letra)
                  <input type="number" min="0" step="1" name="letra{{ c.id }}" [(ngModel)]="importeLetra" />
                </label>
                <button type="submit">Pagar</button>
              </form>
              <p class="suave">Si paga solo una parte, se cobra en este orden: capital, desgravamen, interés, ahorro letras y mora.</p>
              @if (c.familia === 'LETRAS') {
                @if (c.precancelable) {
                  <div class="tarjeta mt-4">
                    <h3>Pagar todo el crédito</h3>
                    <p>Ya puede terminar el crédito pagando todo el capital pendiente@if (c.moraPendiente > 0) { y su mora }: <strong>{{ c.capitalPendiente + c.moraPendiente | currency: 'USD' }}</strong>. Sin interés, desgravamen ni ahorro letras de las letras que faltan. Su encaje y ahorro letras vuelven a su cuenta.</p>
                    <button type="button" (click)="precancelar(c)">Pagar todo el crédito</button>
                  </div>
                } @else {
                  <p class="suave">Podrá pagar todo el crédito, solo con su capital, cuando tenga {{ c.letrasParaPrecancelar }} letras pagadas. Lleva {{ c.cuotasPagadas.length }}.</p>
                }
              }
            }
          }
        </section>
      } @empty {
        <p class="vacio">No tiene créditos vigentes.</p>
      }
    </section>

    @if (cargando()) { <p class="cargando" role="status">Cargando…</p> }

    @if (productos(); as p) {
      <section class="tarjeta seccion-accion">
        <h2>{{ p.perfil === 'SOCIO' ? 'Cupo emergente' : 'Crédito emergente' }}</h2>
        @if (p.perfil === 'SOCIO') {
          <p>Utilizado: {{ p.cupoUtilizado | currency: 'USD' }} · Disponible: <strong>{{ p.cupoDisponible | currency: 'USD' }}</strong></p>
          <p class="suave"><strong>Cupo emergente:</strong> interés {{ p.emergenteInteres | number: '1.0-4' }} % y desgravamen {{ p.emergenteDesgravamen | number: '1.0-4' }} % diarios sobre lo utilizado. Cada retiro se aprueba antes de llegar a su cuenta.</p>
          @for (s of cupoPendiente(); track s.id) {
            <p class="suave"><strong>En espera de aprobación:</strong> {{ s.importe | currency: 'USD' }}</p>
          }
          @if (ultimoRechazo(); as r) {
            <p class="suave"><strong>Último retiro rechazado:</strong> {{ r.importe | currency: 'USD' }}. {{ r.motivo }}</p>
          }
          @if (p.promocionAbierta) {
            <label>Destino
              <select name="destino" [(ngModel)]="promocion">
                <option [ngValue]="null">Cupo general</option>
                <option [ngValue]="p.promocionAbierta">Promoción {{ p.promocionAbierta }} (hasta {{ p.promocionLimite | currency: 'USD' }}, {{ p.promocionTasa | number: '1.0-4' }} % diario)</option>
              </select>
            </label>
          }
        } @else {
          <p>Hasta {{ p.emergenteLimite | currency: 'USD' }}, una vez al mes, a pagar el mes siguiente.</p>
          <p class="suave"><strong>Crédito emergente:</strong> interés {{ p.emergenteInteres | number: '1.0-4' }} % y desgravamen {{ p.emergenteDesgravamen | number: '1.0-4' }} % mensuales. Se aprueba antes de llegar a su cuenta.</p>
        }
        <form class="acciones" (ngSubmit)="pedirEmergente(p)">
          <label>Importe (USD)
            <input type="number" min="0" step="1" name="emergente" [(ngModel)]="importeEmergente" />
          </label>
          <button type="submit" [disabled]="!importeEmergente || importeEmergente <= 0">Solicitar</button>
        </form>
      </section>

      <section class="tarjeta seccion-accion">
        <h2>Crédito a letras</h2>
        <p class="suave"><strong>Crédito a letras:</strong> letras mensuales. El encaje se paga al inicio y se devuelve al terminar.</p>
        <form (ngSubmit)="simular()">
          <label>Modalidad
            <select name="modalidad" [(ngModel)]="modalidad">
              @for (m of p.letras; track m.modalidad) {
                <option [value]="m.modalidad">{{ m.modalidad }}: {{ m.tasaInteres | number: '1.0-4' }} % mensual, encaje {{ m.porcentajeEncaje | number: '1.0-4' }} %</option>
              }
            </select>
          </label>
          <label>Importe (USD, de {{ p.importeMinimo | number }} a {{ p.importeMaximo | number }})
            <input type="number" min="0" name="importe" step="1" [(ngModel)]="importe" />
          </label>
          <label>Plazo (de {{ p.plazoMinimo }} a {{ p.plazoMaximo }} meses)
            <input type="number" name="plazo" step="1" [(ngModel)]="plazo" />
          </label>
          <button type="submit" class="secundario" [disabled]="!importe || !plazo">Simular</button>
        </form>
        @if (simulacion(); as s) {
          <p>Encaje al inicio: <strong>{{ s.encaje | currency: 'USD' }}</strong></p>
          <div class="tabla">
            <table>
              <thead><tr><th>N.º</th><th class="num">Capital</th><th class="num">Interés</th><th class="num">Desgravamen</th><th class="num">Ahorro letras</th><th class="num">Total</th></tr></thead>
              <tbody>
                @for (l of s.cuotas; track l.numero) {
                  <tr>
                    <td>{{ l.numero }}</td>
                    <td class="num">{{ l.capital | currency: 'USD' }}</td>
                    <td class="num">{{ l.interes | currency: 'USD' }}</td>
                    <td class="num">{{ l.fondoDesgravamen | currency: 'USD' }}</td>
                    <td class="num">{{ l.ahorroLetras | currency: 'USD' }}</td>
                    <td class="num">{{ l.total | currency: 'USD' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="tarjeta mt-4">
            <h3>Solicitar este crédito</h3>
            @if (faltanDatos()) {
              <form [formGroup]="datos" (ngSubmit)="guardarDatos()">
                <p>Para solicitar el crédito, complete estos datos. Son obligatorios.</p>
                <label>Dirección <input formControlName="direccion" maxlength="200" /></label>
                <label>Lugar de trabajo <input formControlName="lugarTrabajo" maxlength="150" /></label>
                <label>Ocupación <input formControlName="ocupacion" maxlength="100" /></label>
                <label>Antigüedad en el trabajo <input formControlName="antiguedadTrabajo" maxlength="50" placeholder="Ejemplo: 2 años" /></label>
                <label>Ingresos mensuales (USD) <input type="number" min="0" step="1" formControlName="ingresosMensuales" /></label>
                <label>Fecha de nacimiento <input type="date" formControlName="fechaNacimiento" /></label>
                <label>Estado civil
                  <select formControlName="estadoCivil">
                    <option value="SOLTERO">Soltero/a</option>
                    <option value="CASADO">Casado/a</option>
                    <option value="UNION_LIBRE">Unión libre</option>
                    <option value="DIVORCIADO">Divorciado/a</option>
                    <option value="VIUDO">Viudo/a</option>
                  </select>
                </label>
                <label>Nombre de su referencia <input formControlName="referenciaNombre" maxlength="150" /></label>
                <label>Teléfono de su referencia <input type="tel" formControlName="referenciaTelefono" maxlength="20" /></label>
                <label>Banco <input formControlName="banco" maxlength="100" /></label>
                <label>Tipo de cuenta
                  <select formControlName="tipoCuentaBanco">
                    <option value="AHORROS">Ahorros</option>
                    <option value="CORRIENTE">Corriente</option>
                  </select>
                </label>
                <label>Número de cuenta <input formControlName="numeroCuentaBanco" maxlength="40" /></label>
                <button type="submit" [disabled]="datos.invalid">Guardar datos</button>
              </form>
            } @else {
              <label>¿De dónde pagará el encaje de {{ s.encaje | currency: 'USD' }}?
                <select name="origenEncaje" [(ngModel)]="origenEncaje">
                  <option value="">Elija una opción</option>
                  <option value="EFECTIVO">En efectivo, al aprobarse</option>
                  <option value="CUENTA">De mi cuenta principal</option>
                  <option value="AHORROS">De mis ahorros</option>
                  <option value="CREDITO">Descontado del crédito</option>
                </select>
              </label>
              @if (origenEncaje === 'AHORROS') {
                <label>Ahorro del que se descuenta
                  <select name="ahorroEncaje" [(ngModel)]="ahorroEncaje">
                    @for (a of ahorrosNormales(); track a.id) { <option [ngValue]="a.id">{{ a.nombre }}: {{ a.saldo | currency: 'USD' }}</option> }
                  </select>
                </label>
              }
              @if (origenEncaje === 'CREDITO') {
                <p class="suave"><strong>Descontado del crédito:</strong> recibirá {{ s.importe - s.encaje | currency: 'USD' }} en su cuenta.</p>
              }
              <label>Documentos en PDF (opcional, puede elegir varios)
                <input type="file" accept="application/pdf,.pdf" multiple (change)="elegirDocumentos($event)" />
              </label>
              @if (documentos.length) { <p class="suave">{{ documentos.length }} {{ documentos.length === 1 ? 'documento elegido' : 'documentos elegidos' }}.</p> }
              <button type="button" [disabled]="!origenEncaje || (origenEncaje === 'AHORROS' && !ahorroEncaje)" (click)="solicitar()">Solicitar este crédito</button>
            }
          </div>
        }
      </section>
    }

    <section class="seccion">
      <h2 class="seccion-titulo">Historial de créditos</h2>
      @if (historial().length) {
        <div class="tabla">
          <table>
            <thead><tr><th>Crédito</th><th>Solicitado</th><th>Terminado</th><th class="num">Importe</th><th class="num">Letras</th><th class="num">Pendiente</th><th>Estado</th></tr></thead>
            <tbody>
              @for (c of historial(); track c.id) {
                <tr>
                  <td>{{ familia(c.familia) }} {{ c.modalidad ?? '' }}
                    @if (c.motivoRechazo) { <br /><small class="suave">{{ c.motivoRechazo }}</small> }
                    @if (c.precanceladoEn) { <br /><small class="suave">Pagado por adelantado</small> }
                  </td>
                  <td>{{ c.creadoEn | date: 'mediumDate' : 'America/Guayaquil' }}</td>
                  <td>{{ c.finalizadoEn ? (c.finalizadoEn | date: 'mediumDate' : 'America/Guayaquil') : '—' }}</td>
                  <td class="num">{{ c.importe | currency: 'USD' }}</td>
                  <td class="num">{{ c.plazoMeses ?? '—' }}</td>
                  <td class="num">{{ c.totalPendiente | currency: 'USD' }}</td>
                  <td><app-estado [valor]="c.estado" /></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <p class="vacio">Aún no tiene créditos.</p>
      }
    </section>
  `,
})
export class Creditos implements OnInit {
  private readonly api = inject(ApiService);
  private readonly dialogo = inject(DialogoService);

  protected readonly creditos = signal<Credito[]>([]);
  protected readonly vigentes = computed(() => this.creditos().filter((c) =>
    c.estado !== 'FINALIZADO' && c.estado !== 'RECHAZADO' && !(this.esCupo(c) && c.totalPendiente === 0)));
  protected readonly activos = computed(() => this.vigentes().filter((c) => c.estado === 'ACTIVO'));
  protected readonly totalDeuda = computed(() => this.activos().reduce((t, c) => t + c.totalPendiente, 0));
  protected readonly totalCapital = computed(() => this.activos().reduce((t, c) => t + c.capitalPendiente, 0));
  /** Todos los créditos, el más reciente primero. */
  protected readonly historial = computed(() => [...this.creditos()].sort((a, b) => b.id - a.id));
  protected readonly atrasadas = computed(() => this.creditos().reduce((t, c) => t + c.letrasAtrasadas, 0));
  protected readonly mora = computed(() => this.creditos().reduce((t, c) => t + c.moraPendiente, 0));
  protected readonly solicitudesCupo = signal<SolicitudCupo[]>([]);
  protected readonly cupoPendiente = computed(() => this.solicitudesCupo().filter((s) => s.estado === 'PENDIENTE'));
  protected readonly ultimoRechazo = computed(() => this.solicitudesCupo().find((s) => s.estado !== 'PENDIENTE' && s.estado === 'RECHAZADA') ?? null);
  protected readonly ahorrosNormales = signal<Ahorro[]>([]);
  protected readonly perfil = signal<Perfil | null>(null);
  protected readonly faltanDatos = computed(() => !this.perfil()?.datosCreditoCompletos);
  protected readonly productos = signal<ProductosCredito | null>(null);
  protected readonly cargando = signal(true);
  protected readonly simulacion = signal<Simulacion | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  protected modalidad = 'NORMAL';
  protected importe: number | null = null;
  protected plazo: number | null = null;
  protected importeEmergente: number | null = null;
  protected importePago: number | null = null;
  protected importeLetra: number | null = null;
  protected promocion: string | null = null;
  protected usarAhorro = false;
  protected usarEncaje = false;
  protected origenEncaje = '';
  protected ahorroEncaje: number | null = null;
  protected documentos: File[] = [];
  protected readonly datos = inject(NonNullableFormBuilder).group({
    direccion: ['', [Validators.required, Validators.pattern(/\S/)]],
    lugarTrabajo: ['', [Validators.required, Validators.pattern(/\S/)]],
    ocupacion: ['', [Validators.required, Validators.pattern(/\S/)]],
    antiguedadTrabajo: ['', [Validators.required, Validators.pattern(/\S/)]],
    ingresosMensuales: [0, [Validators.required, Validators.min(0)]],
    fechaNacimiento: ['', Validators.required],
    estadoCivil: ['SOLTERO', Validators.required],
    referenciaNombre: ['', [Validators.required, Validators.pattern(/\S/)]],
    referenciaTelefono: ['', [Validators.required, Validators.pattern(/\S/)]],
    banco: ['', [Validators.required, Validators.pattern(/\S/)]],
    tipoCuentaBanco: ['AHORROS', Validators.required],
    numeroCuentaBanco: ['', [Validators.required, Validators.pattern(/\S/)]],
  });

  // Una clave por intento: se renueva solo tras un éxito, para que reintentar no duplique.
  private clave: string = crypto.randomUUID();

  constructor() {
    alCambiar(() => this.cargar());
  }

  ngOnInit(): void {
    this.cargar();
  }

  protected familia(codigo: string): string {
    return nombreFamilia(codigo);
  }

  protected esCupo(c: Credito): boolean {
    return c.familia === 'EMERGENTE_SOCIO' || c.familia === 'EMERGENTE_ESTACIONAL';
  }

  protected esUltima(c: Credito): boolean {
    return c.cuotasPagadas.length === c.cuotas.length - 1;
  }

  protected simular(): void {
    this.error.set(null);
    this.api.simularLetras(this.modalidad, this.importe!, this.plazo!).subscribe({
      next: (s) => this.simulacion.set(s),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected origen(codigo: string): string {
    return ({ EFECTIVO: 'en efectivo, al aprobarse', CUENTA: 'de su cuenta principal', AHORROS: 'de sus ahorros', CREDITO: 'descontado del crédito' } as Record<string, string>)[codigo] ?? codigo;
  }

  protected elegirDocumentos(evento: Event): void {
    this.documentos = Array.from((evento.target as HTMLInputElement).files ?? []);
  }

  /** La dirección va con el contacto y lo demás con los datos para crédito: son dos rutas del perfil. */
  protected guardarDatos(): void {
    const { direccion, ...credito } = this.datos.getRawValue();
    this.error.set(null);
    this.api.actualizarContacto({ telefono: this.perfil()?.telefono ?? '', direccion: direccion.trim() }).subscribe({
      next: () => this.api.actualizarDatosCredito(credito as DatosCredito).subscribe({
        next: (p) => this.perfil.set(p),
        error: (e) => this.error.set(mensajeDeError(e)),
      }),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected solicitar(): void {
    this.error.set(null);
    this.aviso.set(null);
    const ahorro = this.origenEncaje === 'AHORROS' ? this.ahorroEncaje : null;
    this.api.solicitarLetras(this.modalidad, this.importe!, this.plazo!, this.origenEncaje, ahorro).subscribe({
      next: ({ id }) => {
        const archivos = this.documentos;
        this.simulacion.set(null);
        this.origenEncaje = '';
        this.ahorroEncaje = null;
        this.documentos = [];
        this.aviso.set('Solicitud enviada. El dinero llega a su cuenta cuando se apruebe.');
        this.cargar();
        if (archivos.length) {
          // La solicitud ya existe: si falla la subida, se reintenta desde su panel de documentos.
          this.api.subirDocumentosCredito(id, archivos).subscribe({
            next: () => this.cargar(),
            error: (e) => this.error.set('La solicitud se envió, pero los documentos no: ' + mensajeDeError(e)),
          });
        }
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected pedirEmergente(p: ProductosCredito): void {
    const accion = p.perfil === 'SOCIO'
      ? this.api.disponerCupo(this.importeEmergente!, this.promocion, this.clave)
      : this.api.solicitarEmergente(this.importeEmergente!, this.clave);
    this.ejecutar(accion, 'Solicitud enviada. El dinero llega a su cuenta cuando se apruebe.', () => {
      this.importeEmergente = null;
    });
  }

  protected pagarCupo(c: Credito, importe: number | null): void {
    this.ejecutar(this.api.pagarCredito(c.id, { importe: importe! }, this.clave), 'Pago registrado.', () => {
      this.importePago = null;
    });
  }

  protected pagarCuota(c: Credito): void {
    const final = this.esUltima(c) && c.familia === 'LETRAS';
    const cuerpo = { importe: this.importeLetra || null, usarAhorroLetras: final && this.usarAhorro, usarEncaje: final && this.usarEncaje };
    this.ejecutar(this.api.pagarCredito(c.id, cuerpo, this.clave), 'Pago registrado.', () => {
      this.importeLetra = null;
    });
  }

  protected async precancelar(c: Credito): Promise<void> {
    if (await this.dialogo.confirmar(`¿Pagar todo el crédito con ${c.capitalPendiente + c.moraPendiente} USD? El crédito terminará.`, 'Pagar todo el crédito')) {
      this.ejecutar(this.api.precancelarCredito(c.id, this.clave), 'Crédito pagado por completo.');
    }
  }

  protected pendienteSiguiente(c: Credito): number {
    const siguiente = c.cuotas.find((l) => !c.cuotasPagadas.includes(l.numero));
    return siguiente ? siguiente.total - siguiente.pagado + siguiente.mora : 0;
  }

  private ejecutar(accion: Observable<unknown>, aviso: string, alTerminar?: () => void): void {
    this.error.set(null);
    this.aviso.set(null);
    accion.subscribe({
      next: () => {
        this.clave = crypto.randomUUID();
        this.aviso.set(aviso);
        alTerminar?.();
        this.cargar();
      },
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  private cargar(): void {
    // Por separado: cada parte aparece en cuanto llega, sin esperar a la otra.
    this.api.misCreditos().subscribe({
      next: (creditos) => this.creditos.set(creditos),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
    const fallo = (e: unknown) => this.error.set(mensajeDeError(e));
    this.api.misSolicitudesCupo().subscribe({ next: (s) => this.solicitudesCupo.set(s), error: fallo });
    this.api.misAhorros().subscribe({ next: (a) => this.ahorrosNormales.set(a.filter((x) => x.tipo === 'NORMAL' && x.estado === 'ACTIVA')), error: fallo });
    this.api.miPerfil().subscribe({
      next: (p) => {
        this.perfil.set(p);
        if (!this.datos.dirty) {
          this.datos.patchValue({ direccion: p.direccion ?? '' });
        }
      },
      error: fallo,
    });
    this.api.productosCredito().subscribe({
      next: (productos) => {
        this.productos.set(productos);
        this.cargando.set(false);
      },
      error: (e) => {
        this.error.set(mensajeDeError(e));
        this.cargando.set(false);
      },
    });
  }
}
