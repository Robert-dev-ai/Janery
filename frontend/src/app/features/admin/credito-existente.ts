import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService, CreditoExistente, PersonaTramite, VistaCreditoExistente } from '../../core/services/api';
import { DialogoService } from '../../shared/components/dialogo';
import { mensajeDeError } from '../../shared/utils/errores';

/**
 * Créditos a letras que ya existían antes de usar Janery: se registran con su tasa, plazo y fecha de inicio reales y
 * con las letras ya pagadas. Desde ahí se cobran como cualquier otro crédito.
 */
@Component({
  selector: 'app-credito-existente',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, FormsModule],
  template: `
    <section class="seccion seccion-accion">
      <h2 class="seccion-titulo">Registrar un crédito que ya existía</h2>
      <p class="suave">Para los créditos dados antes de usar Janery. No entra ni sale dinero: ese dinero ya se movió. Lo cobrado antes no se reparte otra vez como ganancia; desde hoy, cada pago se cobra y se reparte como en cualquier crédito.</p>
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      @if (aviso()) { <p class="exito" role="status">{{ aviso() }}</p> }

      @if (!persona()) {
        <form class="herramientas" (ngSubmit)="buscar()">
          <label>Persona <input type="search" name="buscar" placeholder="Nombre, cédula o número de cuenta" [(ngModel)]="texto" /></label>
          <button type="submit">Buscar</button>
        </form>
        @for (p of resultados(); track p.usuarioId) {
          <div class="fila-dato tarjeta">
            <div><strong>{{ p.nombre }}</strong><p class="suave">{{ p.identificacion }} · {{ p.cuenta }} · {{ p.socio ? 'Socio' : 'Cliente' }}</p></div>
            <button type="button" (click)="elegir(p)">Elegir</button>
          </div>
        } @empty {
          @if (buscado()) { <p class="vacio">Nadie con cuenta activa coincide con la búsqueda. Las letras pendientes se cobran de la cuenta principal: ábrala primero.</p> }
        }
      } @else {
        <div class="encabezado-tarjeta">
          <p class="m-0"><strong>{{ persona()!.nombre }}</strong> · {{ persona()!.identificacion }} · {{ persona()!.socio ? 'Socio' : 'Cliente' }}</p>
          <button type="button" class="secundario" (click)="reiniciar()">Elegir a otra persona</button>
        </div>

        <form class="tarjeta" (ngSubmit)="previsualizar()" (input)="vista.set(null)" (change)="vista.set(null)">
          <label>Modalidad
            <select name="modalidad" [(ngModel)]="datos.modalidad">
              <option value="NORMAL">Normal</option>
              <option value="INVERSION">Inversión</option>
              <option value="ESTUDIO">Estudio</option>
              <option value="PERSONALIZADO">Personalizado</option>
            </select>
          </label>
          <label>Importe prestado (USD) <input type="number" min="0" step="any" name="importe" placeholder="0" [(ngModel)]="datos.importe" /></label>
          <label>Plazo total (meses) <input type="number" min="1" step="1" name="plazo" placeholder="0" [(ngModel)]="datos.plazoMeses" /></label>
          <label>Interés (% mensual sobre el importe) <input type="number" min="0" step="any" name="interes" placeholder="0" [(ngModel)]="datos.tasaInteres" /></label>
          <label>Desgravamen (% mensual sobre el importe, vacío si no tenía) <input type="number" min="0" step="any" name="desgravamen" placeholder="0" [(ngModel)]="datos.tasaDesgravamen" /></label>
          <label>Fecha en que recibió el dinero <input type="date" name="inicio" [max]="hoy" [(ngModel)]="datos.fechaInicio" /></label>
          <label>Letras ya pagadas completas (vacío si ninguna) <input type="number" min="0" step="1" name="pagadas" placeholder="0" [(ngModel)]="datos.letrasPagadas" /></label>
          <label>Encaje que se le retuvo (USD, vacío si no tuvo) <input type="number" min="0" step="any" name="encaje" placeholder="0" [(ngModel)]="datos.encaje" /></label>
          <div class="fila-opcion">
            <label><input type="checkbox" name="ahorro" [(ngModel)]="datos.conAhorroLetras" /> Llevaba ahorro letras (letra redondeada a dólares enteros)</label>
          </div>
          <label>Nota (opcional) <input name="nota" maxlength="300" placeholder="Ej.: libreta 12, crédito de marzo" [(ngModel)]="datos.nota" /></label>
          <button type="submit" class="secundario" [disabled]="!completo()">Ver cómo queda</button>
        </form>

        @if (vista(); as v) {
          <dl class="ficha-datos">
            <div><dt>Perfil</dt><dd>{{ v.perfil === 'SOCIO' ? 'Socio' : 'Cliente' }}</dd></div>
            <div><dt>Letra mensual</dt><dd>{{ v.cuotas[0]?.total | currency: 'USD' }}</dd></div>
            <div><dt>Letras pagadas</dt><dd>{{ v.letrasPagadas }} de {{ v.cuotas.length }}</dd></div>
            <div><dt>Capital pendiente</dt><dd>{{ v.capitalPendiente | currency: 'USD' }}</dd></div>
            <div><dt>Falta por pagar (sin mora)</dt><dd>{{ v.totalPendiente | currency: 'USD' }}</dd></div>
            <div><dt>Encaje retenido</dt><dd>{{ v.encaje | currency: 'USD' }}</dd></div>
            <div><dt>Ahorro letras acumulado</dt><dd>{{ v.ahorroAcumulado | currency: 'USD' }}</dd></div>
          </dl>
          @if (v.letrasAtrasadas > 0) {
            <p class="negativo">{{ v.letrasAtrasadas }} letra{{ v.letrasAtrasadas === 1 ? ' ya venció' : 's ya vencieron' }} sin pagarse.
              @if (v.tasaMora > 0) { Como es cliente, la mora de {{ v.tasaMora | currency: 'USD' }} diarios corre desde cada vencimiento. }
              Si en realidad ya las pagó, súmelas a las letras pagadas.</p>
          }
          <div class="tabla">
            <table>
              <thead><tr><th>Letra</th><th>Vence</th><th class="num">Capital</th><th class="num">Interés</th><th class="num">Desgravamen</th><th class="num">Ahorro</th><th class="num">Total</th><th>Estado</th></tr></thead>
              <tbody>
                @for (l of v.cuotas; track l.numero) {
                  <tr>
                    <td>{{ l.numero }}</td>
                    <td>{{ l.vencimiento | date: 'mediumDate' }}</td>
                    <td class="num">{{ l.capital | number: '1.2-2' }}</td>
                    <td class="num">{{ l.interes | number: '1.2-2' }}</td>
                    <td class="num">{{ l.fondoDesgravamen | number: '1.2-2' }}</td>
                    <td class="num">{{ l.ahorroLetras | number: '1.2-2' }}</td>
                    <td class="num">{{ l.total | currency: 'USD' }}</td>
                    <td>
                      @if (l.numero <= v.letrasPagadas) { <span class="estado estado-neutro">Pagada</span> }
                      @else if (l.vencimiento < hoy) { <span class="negativo">Atrasada</span> }
                      @else { Pendiente }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <button type="button" [disabled]="enviando()" (click)="registrar(v)">Registrar crédito</button>
        }
      }
    </section>
  `,
})
export class CreditoExistenteForm {
  readonly registrado = output<void>();

  private readonly api = inject(ApiService);
  private readonly dialogo = inject(DialogoService);

  protected readonly resultados = signal<PersonaTramite[]>([]);
  protected readonly buscado = signal(false);
  protected readonly persona = signal<PersonaTramite | null>(null);
  protected readonly vista = signal<VistaCreditoExistente | null>(null);
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  protected readonly hoy = new Date().toLocaleDateString('en-CA', { timeZone: 'America/Guayaquil' });
  protected texto = '';
  protected datos = this.vacio();
  // Una clave por intento: se renueva solo tras un éxito, para que reintentar no duplique.
  private clave: string = crypto.randomUUID();

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
    this.aviso.set(null);
  }

  protected reiniciar(): void {
    this.persona.set(null);
    this.vista.set(null);
    this.datos = this.vacio();
  }

  /** Importe, plazo, interés y fecha son obligatorios; lo demás vacío cuenta como cero. */
  protected completo(): boolean {
    const d = this.datos;
    const valido = (n: number | null) => n === null || n >= 0;
    return (d.importe ?? 0) > 0 && (d.plazoMeses ?? 0) > 0 && d.tasaInteres !== null && d.tasaInteres >= 0 && !!d.fechaInicio
      && valido(d.tasaDesgravamen) && valido(d.letrasPagadas) && valido(d.encaje);
  }

  private envio(): CreditoExistente {
    const d = this.datos;
    return { ...d, importe: d.importe!, plazoMeses: d.plazoMeses!, tasaInteres: d.tasaInteres!, tasaDesgravamen: d.tasaDesgravamen ?? 0,
      letrasPagadas: d.letrasPagadas ?? 0, encaje: d.encaje ?? 0 };
  }

  protected previsualizar(): void {
    this.error.set(null);
    this.api.vistaCreditoExistente(this.persona()!.usuarioId, this.envio()).subscribe({
      next: (v) => this.vista.set(v),
      error: (e) => this.error.set(mensajeDeError(e)),
    });
  }

  protected async registrar(v: VistaCreditoExistente): Promise<void> {
    const p = this.persona()!;
    const pregunta = `¿Registrar el crédito de ${p.nombre} por ${this.datos.importe} USD, con ${v.letrasPagadas} de ${v.cuotas.length} letras pagadas? Queda activo de inmediato y no se puede borrar.`;
    if (!(await this.dialogo.confirmar(pregunta, 'Registrar'))) {
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    this.api.registrarCreditoExistente(p.usuarioId, this.envio(), this.clave).subscribe({
      next: () => {
        this.clave = crypto.randomUUID();
        this.enviando.set(false);
        this.reiniciar();
        this.aviso.set(`Crédito de ${p.nombre} registrado. Ya aparece entre los créditos activos.`);
        this.registrado.emit();
      },
      error: (e) => {
        this.enviando.set(false);
        this.error.set(mensajeDeError(e));
      },
    });
  }

  /** Los números empiezan vacíos: con un 0 escrito, al teclear quedaba «010». */
  private vacio(): Formulario {
    return { modalidad: 'NORMAL', importe: null, plazoMeses: null, tasaInteres: null, tasaDesgravamen: null, conAhorroLetras: false, fechaInicio: '', letrasPagadas: null, encaje: null, nota: '' };
  }
}

type Formulario = Omit<CreditoExistente, 'importe' | 'plazoMeses' | 'tasaInteres' | 'tasaDesgravamen' | 'letrasPagadas' | 'encaje'> & {
  importe: number | null;
  plazoMeses: number | null;
  tasaInteres: number | null;
  tasaDesgravamen: number | null;
  letrasPagadas: number | null;
  encaje: number | null;
};
